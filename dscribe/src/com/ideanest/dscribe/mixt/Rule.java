package com.ideanest.dscribe.mixt;

import java.text.MessageFormat;
import java.util.*;

import org.apache.log4j.Logger;
import org.exist.fluent.*;

import com.ideanest.dscribe.mixt.blocks.*;

public class Rule {
	
	private static final Logger LOG = Logger.getLogger(Rule.class);
	
	// TODO: make this an extension point
	@SuppressWarnings("unchecked")
	private static final Class[] BLOCK_CLASSES = {
		For.class, With.class, CreateDoc.class, Insert.class
	};
	
	private static final Map<QName,BlockType> BLOCK_TYPE_DICTIONARY = new HashMap<QName,BlockType>();
	static {
		for (Class<BlockType> blockClass : BLOCK_CLASSES) {
			try {
				BlockType blockType = blockClass.newInstance();
				BLOCK_TYPE_DICTIONARY.put(blockType.xmlName(), blockType);
			} catch (InstantiationException e) {
				LOG.error("unable to instantiate block class " + blockClass.getName(), e);
			} catch (IllegalAccessException e) {
				LOG.error("unable to access block class " + blockClass.getName(), e);
			}
		}
	}
	
	public final Engine engine;
	final String id;
	private final String toString;
	private final List<Block> blocks = new ArrayList<Block>();
	private Set<Document> touched = new HashSet<Document>();
	private final Accumulator.Locator<Document> modifiedDocsLocator;
	private int firstDifferentStage;

	Rule(Node def, Node prevDef, Engine engine, Accumulator.Locator<Document> modifiedDocsLocator) throws RuleBaseException {
		this.engine = engine;
		this.modifiedDocsLocator = modifiedDocsLocator;
		try {
			this.id = def.query().single("@xml:id").value();
		} catch (DatabaseException e) {
			throw new RuleBaseException("a rule " + def.name() + " has no xml:id");
		}

		toString = buildToString(def.query().optional("@name").value());
		LOG.debug("reading in " + this);
		
		firstDifferentStage = parseBlocks(def, prevDef);
		
		if (firstDifferentStage < Integer.MAX_VALUE) {
			LOG.info(this + " has changed starting at stage " + firstDifferentStage + ", withdrawing affected mods");
			engine.withdrawMods(engine.modStore.query().unordered(
					"//mod[@rule=$_1][xs:integer(@stage) >= $_2]", id, firstDifferentStage));
		}
	
	}

	private int parseBlocks(Node def, Node prevDef) throws RuleBaseException {
		int firstDiff = Integer.MAX_VALUE;
		try {
			Mod mod = Mod.bootstrap(this);
			Iterator<Node> prevBlocksIterator = prevDef.query().all("*").nodes().iterator();
			for (Node blockDef : def.query().all("*").nodes()) {
				
				if (firstDiff == Integer.MAX_VALUE &&
						!(prevBlocksIterator.hasNext() && compareBlocks(blockDef, prevBlocksIterator.next()))) {
					firstDiff = blocks.size();
				}
				
				Block block = defineBlock(blockDef);
				mod = mod.deriveChild(block, "x");		// a bogus key, just to make the mod believable
				mod.analyze();
				blocks.add(block);
				
			}
			if (firstDiff == Integer.MAX_VALUE && prevBlocksIterator.hasNext()) firstDiff = blocks.size();
			return firstDiff;
		} catch (DatabaseException e) {
			throw new RuleBaseException(this + " definition in error", e);
		} catch (TransformException e) {
			throw new RuleBaseException(this + " failed dynamic analysis", e);
		}
	}

	private boolean compareBlocks(Node blockDef, Node prevBlockDef) {
		return engine.workspace.query().single(
				"deep-equal($_1, $_2) and " +
				"count(in-scope-prefixes($_1)) eq count(in-scope-prefixes($_2)) and " +
				"every $prefix in in-scope-prefixes($_1) satisfies namespace-uri-for-prefix($prefix, $_1) eq namespace-uri-for-prefix($prefix, $_2)",
				blockDef, prevBlockDef).booleanValue();
	}

	private Block defineBlock(Node blockDef) throws RuleBaseException {
		BlockType blockType = BLOCK_TYPE_DICTIONARY.get(blockDef.qname());
		if (blockType == null) throw new RuleBaseException(this + " unknown block " + blockDef);
		Block block = blockType.define(blockDef);
		boolean isLinear = block instanceof LinearBlock, isKey = block instanceof KeyBlock;
		if (isLinear && isKey) throw new RuleBaseException("block " + block + " is both key and linear");
		if (!(isLinear || isKey)) throw new RuleBaseException("block " + block + " is neither key nor linear");
		return block;
	}
	
	<D extends Document> void addTouched(Collection<D> docs) {
		touched.addAll(docs);
	}
	
	void process() throws TransformException {
		LOG.debug("processing " + this);
		
		Set<Document> modifiedDocs = modifiedDocsLocator.catchUp();
		
		verifyMods(modifiedDocs);
		
		touched.addAll(modifiedDocs);
		QueryService touchedScope = engine.globalScope.database().query(touched);
		touched = new HashSet<Document>();	// don't clear, old set still referenced by touchedScope
		
		Collection<Mod> mods = new ArrayList<Mod>();
		mods.add(Mod.bootstrap(this));
		
		for (int stage = 0; stage < blocks.size(); stage++) {
			Block block = blocks.get(stage);
			boolean lastBlock = stage == blocks.size()-1;
			LOG.debug("processing " + block);
			
			LOG.debug("restoring mods in progress");
			Set<String> modKeys = new HashSet<String>();
			for (Mod mod : mods) modKeys.add(mod.key());			
			for (Node node : engine.modStore.query().unordered("//mod[@rule=$_1][xs:integer(@stage)=_$2]", id, stage-1).nodes()) {
				if (!modKeys.contains(node.query().single("@xml:id").value())) {
					mods.add(restore(node));
				}
			}
			
			LOG.debug("resolving mods");
			Collection<Mod> nextStageMods = new ArrayList<Mod>();
			engine.numBlocksResolved.increment(mods.size());
			for (Mod mod : mods) nextStageMods.addAll(
					mod.resolveChildren(block, lastBlock, (stage >= firstDifferentStage) ? null : touchedScope));
			mods = nextStageMods;
			
		}
		
		engine.numModsCompleted.increment(mods.size());
		firstDifferentStage = Integer.MAX_VALUE;
	}

	private Mod restore(Node modNode) throws TransformException {
		return restore(modNode, Mod.bootstrap(this), true);
	}
	
	private Mod restore(Node node, Mod mod, boolean inclusive) throws TransformException {
		ItemList history = engine.modStore.query().all(
				"for $node in (/id($_2/ancestor/@refid)[xs:integer(@stage) > $_1] union $_3)" +
				"order by xs:integer($node/@stage) return $node", mod.stage, node, inclusive ? node : null);
		for (Node historyNode : history.nodes()) {
			for (Node blockNode : historyNode.query().all("block").nodes()) {
				mod = mod.restoreChild(blocks.get(mod.stage+1), blockNode);
			}
		}
		return mod;
	}
	
	private ItemList convertDocsToNames(Set<Document> docs) {
		Collection<String> docNamesList = new ArrayList<String>(docs.size());
		for (Document doc : docs) docNamesList.add(engine.relativePath(doc));
		return engine.utilQuery.unordered("$_1", docNamesList);
	}

	/**
	 * Verify all mods that depend on documents that have been modified.
	 * Mods form a tree, so if we were to verify in random order we would re-verify parts
	 * of the tree more than once.  Hence, we do a pre-order traversal of the connected
	 * subset of the tree that contains all the mods that we need to verify.  As soon as a
	 * mod fails to verify, we can cut off that entire branch.  If a mod passes, we recursively
	 * verify its descendants, saving on the expense of restoring the node again.
	 * 
	 * @param modifiedDocs the set of modified documents
	 */
	private void verifyMods(Set<Document> modifiedDocs) {
		LOG.debug("checking for mods to verify");
		
		ItemList modifiedDocsNames = convertDocsToNames(modifiedDocs);
		
		ItemList modsToVerify = engine.modStore.query().unordered(
				"//mod[@rule=$_1][.//dependency/@doc=$_2]", id, modifiedDocsNames);
		if (modsToVerify.size() == 0) {
			LOG.debug("no mods to verify");
		} else {
			LOG.debug(MessageFormat.format(
					"verifying {1,choice,1#1 mod|1<{1,number,integer} mods}",
					new Object[] {modsToVerify.size()}
			));
			verifySubtree(null, Mod.bootstrap(this), modsToVerify, modifiedDocsNames);
		}
	}
	
	private void verifySubtree(Node node, Mod mod, ItemList modsToVerify, ItemList modifiedDocsNames) {
		if (node != null) mod = verifyNode(node, mod, modsToVerify.size()==0, modifiedDocsNames);
		if (mod == null) return;
		for (Node descendant : modsToVerify.query().unordered("mod[not(ancestor/@refid=$_1/xml:id)]", modsToVerify).nodes()) {
			verifySubtree(descendant, mod, modsToVerify.query().unordered("mod[ancestor/@refid=$_1/xml:id]", descendant), modifiedDocsNames);
		}
	}
	
	private Mod verifyNode(Node node, Mod mod, boolean terminal, ItemList modifiedDocsNames) {
		try {
			mod = restore(node, mod, false);
		} catch (TransformException e) {
			LOG.error("failed to restore " + mod + " with no modified dependencies; clearing all mods for " + this, e);
			engine.withdrawRule(id);
			// TODO: abort verification traversal and do global processing
			return null;
		}

		LinkedList<Integer> stagesToVerify = new LinkedList<Integer>();
		for (Item item : node.query().all("block[dependency/@doc=$_1]/@stage", modifiedDocsNames)) {
			stagesToVerify.add(item.intValue());
		}
		assert stagesToVerify.size() > 0;
		for (Node blockNode : node.query().all("block").nodes()) {
			if (terminal && stagesToVerify.size() == 0) {
				LOG.debug("verification ended early for " + mod);
				return null;	// no descendants, so end early
			}
			boolean shouldVerify = stagesToVerify.getFirst() == mod.stage;
			try {
				final Block block = blocks.get(mod.stage+1);
				mod = mod.restoreChild(block, blockNode);
				if (shouldVerify) {
					mod.verify();
					stagesToVerify.removeFirst();
					engine.numBlocksVerified.increment();
				}
			} catch (TransformException e) {
				if (shouldVerify) {
					LOG.debug("verification failed for " + mod, e);
				} else {
					LOG.error("failed to restore unmodified block " + mod + " in modified context; treating as normal verification failure", e);
				}
				engine.withdrawMod(node.query().single("@xml:id").value());
				return null;
			}
		}
		return mod;
	}
	
//	private String blockToString(int stage) {
//		return this + ".block[" + stage + ":" + blocks.get(stage).getClass().getSimpleName() + "]";
//	}
	
	private String buildToString(String primaryName) {
		StringBuilder sb = new StringBuilder();
		sb.append("rule[").append(id);
		if (primaryName != null && primaryName.length() > 0) sb.append(":").append(primaryName);
		sb.append("]");
		return sb.toString();
	}

	@Override public String toString() {
		return toString;
	}
	
}