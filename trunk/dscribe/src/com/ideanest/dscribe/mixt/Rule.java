package com.ideanest.dscribe.mixt;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.lang.reflect.*;
import java.text.MessageFormat;
import java.util.*;

import org.apache.log4j.Logger;
import org.exist.fluent.*;
import org.exist.storage.DBBroker;
import org.hamcrest.*;
import org.jmock.*;
import org.jmock.integration.junit4.*;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.*;
import org.junit.runner.RunWith;

import com.ideanest.dscribe.mixt.BlockType.AllowAttributes;
import com.ideanest.dscribe.mixt.blocks.*;

public class Rule {
	
	private static final Logger LOG = Logger.getLogger(Rule.class);
	
	// TODO: make this an extension point
	@SuppressWarnings("unchecked")
	private static final Class[] BLOCK_CLASSES = {
		For.class, With.class, CreateDoc.class, Insert.class, Sort.class
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
	
	/**
	 * Write out the versions of all defined block types, in a format that can be used in a later run to
	 * detect changed versions.  The builder should be writing to (some descendant of) the resource that
	 * will later be provided as the <code>prevrulespace</code>.
	 * 
	 * @param builder an element builder on the desired target resource
	 */
	static void writeBlockTypeVersions(ElementBuilder<?> builder) {
		builder.namespace("record", Engine.RECORD_NS);
		for (BlockType blockType : BLOCK_TYPE_DICTIONARY.values()) {
			builder.elem("record:block-type")
				.attr("class", blockType.getClass().getName())
				.attr("version", blockType.version())
			.end("record:block-type");
		}
	}
	
	static Collection<QName> verifyBlockTypeVersions(Resource record) {
		Collection<QName> badBlockNames = new ArrayList<QName>();
		for (BlockType blockType : BLOCK_TYPE_DICTIONARY.values()) {
			final String lastVersion = record.query().namespace("record", Engine.RECORD_NS).optional(
					"//record:block-type[@class=$_1]/@version", blockType.getClass().getName()).value();
			if (!blockType.version().equals(lastVersion)) {
				LOG.debug("block " + blockType.xmlName() + " changed from version " + lastVersion + " to " + blockType.version());
				badBlockNames.add(blockType.xmlName());
			}
		}
		return badBlockNames;
	}
	
	public final Engine engine;
	final String id;
	private final String toString;
	private final List<Block> blocks = new ArrayList<Block>();
	private Set<XMLDocument> touched = new HashSet<XMLDocument>();
	private final Accumulator.Locator<XMLDocument> modifiedDocsLocator;
	private int firstDifferentStage, lastKeyStage = -1;
	private final Node rootModNode;
	private final QueryService globalScope;

	private Shim self;  // for testing only

	Rule(Node def, Node prevDef, Engine engine, Accumulator.Locator<XMLDocument> modifiedDocsLocator, Engine.Module module, Engine.Module prevModule) throws RuleBaseException {
		this.engine = engine;
		this.modifiedDocsLocator = modifiedDocsLocator;
		// Need to clone the workspace to avoid wiping out namespace bindings of the folder's shared query service.
		this.globalScope = engine.workspace().cloneWithoutNamespaceBindings().query();
		if (module.source() != null) globalScope.importModule(module.source());
		initDefaultShim();
		
		validateAttributes(def, Collections.singleton("desc"));
		
		try {
			this.id = def.query().single("@xml:id").value();
		} catch (DatabaseException e) {
			throw new RuleBaseException("a rule " + def.name() + " has no xml:id");
		}

		toString = buildToString(def.query().optional("@desc").value());
		LOG.debug("reading in " + this);
		
		rootModNode = self.rootMod().node();

		// parsing blocks initializes firstDifferentStage and lastKeyStage
		parseBlocks(def, prevDef, module, prevModule);
		
		if (firstDifferentStage < Integer.MAX_VALUE) {
			LOG.debug(this + " has changed starting at stage " + firstDifferentStage + ", withdrawing affected mods");
			engine.withdrawMods(rootModNode.query().unordered(".//mod[xs:integer(@stage) >= $_1]", firstDifferentStage));
		}
	}

	// for testing only
	private Rule(Engine engine, Accumulator.Locator<XMLDocument> modifiedDocsLocator) {
		this.engine = engine;
		this.modifiedDocsLocator = modifiedDocsLocator;
		this.globalScope = engine.workspace().query();
		this.id = "r1";
		rootModNode = null;
		toString = "rule[r1]";
		initDefaultShim();
	}

	private interface Shim {
		Mod rootMod();
		int processStage(int stage, QueryService touchedScope) throws TransformException;
		Mod restoreMod(Node modNode, Mod prevMod) throws TransformException;
		void verifyMods(Set<XMLDocument> modifiedDocs) throws TransformException;
		void verifyModTree(ItemList modsToVerify, Collection<String> modifiedDocsNames) throws TransformException;
	}
	
	private void initDefaultShim() {
		this.self = new Shim() {
			public Mod rootMod() {
				return Rule.this.bootstrapMod();
			}
			public int processStage(int stage, QueryService touchedScope) throws TransformException {
				return Rule.this.processStage(stage, touchedScope);
			}
			public Mod restoreMod(Node modNode, Mod prevMod) throws TransformException {
				return Rule.this.restoreMod(modNode, prevMod);
			}
			public void verifyMods(Set<XMLDocument> modifiedDocs) throws TransformException {
				Rule.this.verifyMods(modifiedDocs);
			}
			public void verifyModTree(ItemList modsToVerify, Collection<String> modifiedDocsNames) throws TransformException {
				Rule.this.verifyModTree(modsToVerify, modifiedDocsNames);
			}
		};
	}
	
	QueryService globalScope() {
		return globalScope;
	}
	
	/**
	 * Parse all the blocks in the given rule definition, comparing it to a previous definition of the rule.
	 * Fill the <code>blocks</code> list with the blocks and analyze each one.  Return the index of the
	 * first different block -- but note that this doesn't take implementation changes into account!
	 *
	 * @param def this rule's definition node, of which the block definitions are the children
	 * @param prevDef the previous version of the rule's definition block; cannot be <code>null</code>, but
	 * 		can be an inexistent node
	 * @param module the module holding the function definitions for this rule
	 * @param prevModule the matching previous module or <code>null</code>
	 * @throws RuleBaseException if unable to instantiate a block from its definition at any point
	 */
	private void parseBlocks(Node def, Node prevDef, Engine.Module module, Engine.Module prevModule) throws RuleBaseException {
		firstDifferentStage = Integer.MAX_VALUE;
		try {
			Mod mod = self.rootMod();
			Iterator<Node> prevBlocksIterator = prevDef.query().all("* except alias").nodes().iterator();
			for (Node blockDef : def.query().all("* except alias").nodes()) {
				
				Block block = defineBlock(blockDef);
				if (block instanceof KeyBlock) lastKeyStage = blocks.size();
				mod = mod.deriveChild(block, block instanceof KeyBlock ? "fakeKey" : null);		// a bogus key, just to make the mod believable
				QueryService.QueryAnalysis analysis = mod.analyze();

				if (firstDifferentStage == Integer.MAX_VALUE) {
					Node prevBlockDef = prevBlocksIterator.hasNext() ? prevBlocksIterator.next() : null;
					if (prevBlockDef == null || !equalBlocks(blockDef, prevBlockDef)
							|| module.areFunctionsModified(analysis.requiredFunctions(), prevModule)) {
						if (LOG.isDebugEnabled()) {
							LOG.debug(
									this + " blocks differ at stage " + blocks.size() + ";\n" +
									"--- current block:\n" + blockDef + " with ns prefixes " + blockDef.query().all("in-scope-prefixes(.)") + "\n" +
									"--- previous block:\n" + prevBlockDef + (prevBlockDef == null ? "" : " with ns prefixes " + prevBlockDef.query().all("in-scope-prefixes(.)")) + "\n" +
									"--- required functions:\n" + analysis.requiredFunctions());
						}
						firstDifferentStage = blocks.size();
					}
				}
				
				blocks.add(block);
				
			}
			if (firstDifferentStage == Integer.MAX_VALUE && prevBlocksIterator.hasNext()) firstDifferentStage = blocks.size();
		} catch (DatabaseException e) {
			throw new RuleBaseException(this + " definition in error", e);
		} catch (TransformException e) {
			throw new RuleBaseException(this + " failed dynamic analysis", e);
		}
	}

	/**
	 * Compare two block definitions for equality.  To be equal, the two block definitions must be equal at the XML structural
	 * and content level, and have the same in-scope namespaces assigned to the same prefixes.  This last
	 * is important since the namespace bindings will be used by XQuery expressions embedded in the content.
	 * 
	 * @param block1 one block's definition
	 * @param block2 the other block's definition
	 * @return <code>true</code> if the two blocks are equal, <code>false</code> otherwise
	 */
	private boolean equalBlocks(Node block1, Node block2) {
		return engine.utilQuery().single(
				"deep-equal($_1, $_2) and " +
				"(count(in-scope-prefixes($_1)) eq count(in-scope-prefixes($_2))) and " +
				"(every $prefix in in-scope-prefixes($_1) satisfies (namespace-uri-for-prefix($prefix, $_1) eq namespace-uri-for-prefix($prefix, $_2)))",
				block1, block2).booleanValue();
	}

	/**
	 * Instantiate the block defined by the given node and validate the implementation.
	 *
	 * @param blockDef the block's definition
	 * @return a new block
	 * @throws RuleBaseException if the block type is unknown, or the block's implementation is invalid
	 */
	private Block defineBlock(Node blockDef) throws RuleBaseException {
		BlockType blockType = BLOCK_TYPE_DICTIONARY.get(blockDef.qname());
		if (blockType == null) throw new RuleBaseException(this + " unknown block " + blockDef);
		validateBlockAttributes(blockDef, blockType);
		Block block = blockType.define(blockDef);
		boolean isLinear = block instanceof LinearBlock, isKey = block instanceof KeyBlock;
		if (isLinear && isKey) throw new RuleBaseException("block " + block + " is both key and linear");
		if (!(isLinear || isKey)) throw new RuleBaseException("block " + block + " is neither key nor linear");
		return block;
	}

	private void validateBlockAttributes(Node blockDef, BlockType blockType) throws RuleBaseException {
		try {
			AllowAttributes annotation = blockType.getClass().getMethod("define", Node.class).getAnnotation(AllowAttributes.class);
			Collection<String> allowedAttributes = annotation == null ? Collections.<String>emptySet() : Arrays.asList(annotation.value());
			validateAttributes(blockDef, allowedAttributes);
		} catch (SecurityException e) {
			throw new RuntimeException("could not get define method on " + blockType.getClass(), e);
		} catch (NoSuchMethodException e) {
			throw new RuntimeException("could not get define method on " + blockType.getClass(), e);
		}
	}

	private void validateAttributes(Node node, Collection<String> allowedAttributes) throws RuleBaseException {
		Collection<String> attributes = new ArrayList<String>(Arrays.asList(node.query().all(
				"let $a := @*[namespace-uri() = ''] return if ($a) then $a/local-name() else ()").values().toArray()));
		attributes.removeAll(allowedAttributes);
		if (!attributes.isEmpty()) {
			throw new RuleBaseException(this + " illegal attributes " + attributes + " on " + node);
		}
	}
	
	void addTouched(Collection<XMLDocument> docs) {
		touched.addAll(docs);
	}
	
	@SuppressWarnings("unchecked")  // each block is only fed segs that it produced; see SortController.sort(Node)
	void sortBlock(int stage, Collection<Seg> segs, OrderGraph graph) throws TransformException {
		SortingBlock block;
		try {
			 block = (SortingBlock) blocks.get(stage);
		} catch (ClassCastException e) {
			throw new TransformException("not a sorting block: " + this + ", block " + stage);
		}
		block.sort(segs, graph);
	}
	
	void process1(boolean doGlobalProcessing) throws TransformException {
		LOG.debug("processing " + this);
		if (blocks.size() == 0) return;
		
		Set<XMLDocument> modifiedDocs = modifiedDocsLocator.catchUp();
		
		QueryService touchedScope = null;
		if (!doGlobalProcessing) {
			try {
				self.verifyMods(modifiedDocs);
				touched.addAll(modifiedDocs);
				touchedScope = globalScope.database().query().limitRootDocuments(touched).importSameModulesAs(globalScope);
			} catch (TransformException e) {
				// inconsistent state, rule withdrawn, do global processing after all
			}
		}
		touched = new HashSet<XMLDocument>();	// don't clear, old set still referenced by touchedScope
				
		int lastStageNumModsResolved = 0;
		// The stage counter is the child stage that we'll be resolving at each iteration, based on stage-1.
		for (int stage = 0; stage < blocks.size(); stage++) {
			lastStageNumModsResolved = self.processStage(stage, touchedScope);
		}

		engine.stats.numModsCompleted.increment(lastStageNumModsResolved);
		firstDifferentStage = Integer.MAX_VALUE;
	}
	
	void process(boolean doGlobalProcessing) throws TransformException {
		LOG.debug("processing " + this);
		if (blocks.size() == 0) return;
		
		Set<XMLDocument> modifiedDocs = modifiedDocsLocator.catchUp();
		ItemList modsToVerify = null;
		QueryService touchedScope = null;
		if (!doGlobalProcessing) {
			Collection<String> modifiedDocsNames = convertDocsToNames(modifiedDocs);		
			LOG.debug("modified docs names: " + modifiedDocsNames);
			modsToVerify = rootModNode.query().unordered(
					".//dependency[@doc=$_1][@kind='verified']/parent::mod", modifiedDocsNames);			
			touched.addAll(modifiedDocs);
			touchedScope = globalScope.database().query().limitRootDocuments(touched).importSameModulesAs(globalScope);
		}
		touched = new HashSet<XMLDocument>();	// don't clear, old set still referenced by touchedScope
				
		int lastStageNumModsResolved = processSubtree(self.rootMod(), false, false, touchedScope, modsToVerify);
		
		engine.stats.numModsCompleted.increment(lastStageNumModsResolved);
		firstDifferentStage = Integer.MAX_VALUE;
	}
	
	private static boolean nodeIsMod(Node node) {
		return "mod".equals(node.qname().getLocalPart()) && Engine.MOD_NS.equals(node.qname().getNamespaceURI());		
	}
	
	private static boolean hasModChild(Node node) {
		// The following mess is equivalent to the following, but faster:
		//   mod.node().query().exists("mod")
		for (Node child :node.query().unordered("*").nodes()) {
			if (nodeIsMod(child)) return true;
		}
		return false;
	}
	
	private int processSubtree(Mod mod, boolean verifyOnly, boolean resolveOnly, QueryService touchedScope, ItemList modsToVerify) throws TransformException {
		final int nextStage = mod.stage + 1;
		final Block block = nextStage < blocks.size() ? blocks.get(nextStage) : null;
		final boolean lastBlock = nextStage == blocks.size() - 1;
		final QueryService stageScope = nextStage >= firstDifferentStage ? null : touchedScope;
		
		int numFullyResolved = 0;
		if (!verifyOnly && (block instanceof KeyBlock || !hasModChild(mod.node()))) {
			LOG.debug("resolving children of " + mod);
			int numResolved = mod.resolveChildren(block, lastBlock, stageScope);
			engine.stats.numBlocksResolved.increment();
			if (lastBlock) numFullyResolved += numResolved;
		}
		
		if (block == null || lastBlock && resolveOnly) return numFullyResolved;

		for (Node childNode : mod.node().query().unordered("*").nodes()) {
			if (!(childNode.extant() && nodeIsMod(childNode))) continue;
			// Calculating whether the branch is complete is not worth it, since running the query (or storing a
			// precomputed flag) is expensive, and the benefit is low for typical rules that have a bunch of key
			// blocks followed by one or two cheap linear ones.
			// boolean branchComplete = verifyOnly || (nextStage == lastKeyStage && rootModNode.query().exists("//mod[@stage=$_1] intersect $_2/descendant::*", blocks.size()-1, childNode));
			boolean mustVerifyChild = !resolveOnly && modsToVerify != null && engine.utilQuery().exists("$_1 intersect $_2", modsToVerify, childNode);
			boolean mustVerifyDescendant = !resolveOnly && modsToVerify != null && engine.utilQuery().exists("$_1 intersect $_2/descendant::*", modsToVerify, childNode);
			LOG.debug("child of " + mod + ": " + (mustVerifyChild ? "verify; " : "don't verify; ") + (mustVerifyDescendant ? "verify below; " : "don't verify below; "));
			
			Mod childMod = null;
			if (mustVerifyChild) {
				try {
					childMod = mod.restoreChild(block, childNode);
					engine.stats.numModsRestored.increment();
					childMod.verify();
					engine.stats.numBlocksVerified.increment();
				} catch (TransformException e) {
					if (childMod == null) {
						LOG.debug("failed to restore a child of " + mod + " with modified depencencies: " + e.getMessage());
					} else {
						LOG.debug("failed to verify " + childMod + ": " + e.getMessage());
					}
					engine.withdrawMod(childNode);
					childMod = null;
					modsToVerify.removeDeletedNodes();
				}
			} else if (!lastBlock || mustVerifyDescendant) {
				try {
					childMod = mod.restoreChild(block, childNode);
					engine.stats.numModsRestored.increment();
				} catch (TransformException e) {
					LOG.error("failed to restore " + this + " " + childNode + " with no modified dependencies", e);
					// This should never happen, but we could try to recover by withdrawing the rule and
					// recomputing all mods from scratch.
					// engine.withdrawRule(id);
					throw e;
				}
			}
			// We only want to descend if we either need to resolve more (!lastBlock) or verify more
			// (mustVerifyDescendant) in this branch.  If either of those is true, then childMod should
			// have been restored, unless it failed verification in which case we have nothing to descend
			// into and we'll deal with this branch again in the next cycle.
			if (childMod != null && (!lastBlock || mustVerifyDescendant)) {
				numFullyResolved += processSubtree(
						childMod, lastBlock, !mustVerifyDescendant, touchedScope, modsToVerify);
			}
		}
		return numFullyResolved;
	}

	private int processStage(int stage, QueryService touchedScope) throws TransformException {
		LOG.debug(this + " processing stage " + stage);
		
		int numResolved = 0;
		final Block block = blocks.get(stage);
		boolean lastBlock = stage == blocks.size() - 1;
		QueryService stageScope = stage >= firstDifferentStage ? null : touchedScope;
		boolean mustRestore = block instanceof KeyBlock;
		
		if (stage == 0) {
			if (mustRestore || !rootModNode.query().exists("mod")) {
				Mod mod = self.rootMod();
				LOG.debug("resolving children of " + mod);
				numResolved += mod.resolveChildren(block, lastBlock, stageScope);
				engine.stats.numBlocksResolved.increment();
			}
		} else {
			Mod mod = null;
			for (Node node : self.rootMod().node().query().all(
					".//mod[@stage=$_1]" + (mustRestore ? "" : "[not(exists(mod))]"), stage - 1).nodes()) {
				LOG.debug("restoring mod at stage " + (stage - 1));
				mod = self.restoreMod(node, mod);
				LOG.debug("resolving children of " + mod);
				numResolved += mod.resolveChildren(block, lastBlock, stageScope);
				engine.stats.numBlocksResolved.increment();
			}
		}
		
		return numResolved;
	}

	Mod restoreMod(Node node, Mod prevMod) throws TransformException {
		Mod mod = self.rootMod();
		ItemList modNodes = engine.modStore().query().all(
				"for $mod in ($_1/ancestor-or-self::* except (/modstore, $_2)) " +
				"order by xs:integer($mod/@stage) return $mod", node, mod.node());
		Mod[] prevModStack = null;
		if (prevMod != null) {
			 prevModStack = new Mod[prevMod.stage + 1];
			while (prevMod.stage >= 0) {
				prevModStack[prevMod.stage] = prevMod;
				prevMod = prevMod.parent;
			}
		}
		for (Node historyNode : modNodes.nodes()) {
			int stage = mod.stage + 1;
			prevMod = prevModStack == null || stage >= prevModStack.length ? null : prevModStack[stage];
			mod = prevMod != null && historyNode.equals(prevMod.node())
					? prevMod
					: mod.restoreChild(blocks.get(stage), historyNode);
		}
		engine.stats.numModsRestored.increment();
		return mod;
	}

	private Mod bootstrapMod() {
		Mod rootMod = Mod.bootstrap(this);
		rootMod.node().namespaceBindings().put("", Engine.MOD_NS);
		return rootMod;
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
	 * @throws TransformException if an internally inconsistent state was detected during the verification 
	 */
	private void verifyMods(Set<XMLDocument> modifiedDocs) throws TransformException {
		LOG.debug("checking for mods to verify");
		
		Collection<String> modifiedDocsNames = convertDocsToNames(modifiedDocs);
		
		ItemList modsToVerify = rootModNode.query().unordered(
				".//dependency[@doc=$_1]/parent::mod", modifiedDocsNames);
		if (modsToVerify.size() == 0) {
			LOG.debug("no mods to verify");
		} else {
			LOG.debug(MessageFormat.format(
					"verifying {0,choice,1#1 mod|1<{0,number,integer} mods}",
					new Object[] {modsToVerify.size()}
			));
			self.verifyModTree(modsToVerify, modifiedDocsNames);
		}
	}
	
	/**
	 * Convert a set of documents to a sequence of their relative paths.
	 *
	 * @param docs the set of documents to convert
	 * @return the paths of the given documents, relative to the engine's workspace
	 */
	private Collection<String> convertDocsToNames(Collection<XMLDocument> docs) {
		Collection<String> docNamesList = new ArrayList<String>(docs.size());
		for (Document doc : docs) docNamesList.add(engine.relativePath(doc));
		return docNamesList;
	}

	private void verifyModTree(ItemList modsToVerify, Collection<String> modifiedDocsNames) throws TransformException {
		List<Mod> mods = new ArrayList<Mod>();
		mods.add(self.rootMod());
		while(!mods.isEmpty()) {
			Mod mod = mods.remove(mods.size()-1);
			modsToVerify.removeDeletedNodes();
			ItemList childNodes = mod.node().query().unordered("mod[exists(descendant-or-self::* intersect $_1)]", modsToVerify);
			ItemList childrenToVerify = engine.utilQuery().unordered("$_1 intersect $_2", childNodes, modsToVerify);
			ItemList childrenNotToVerify = engine.utilQuery().unordered("$_1 except $_2", childNodes, modsToVerify);
			childNodes = null;
			for (Node childNode : childrenToVerify.nodes()) {
				Mod childMod = null;
				try {
					childMod = mod.restoreChild(blocks.get(mod.stage+1), childNode);
					childMod.verify();
					engine.stats.numBlocksVerified.increment();
					mods.add(childMod);
				} catch (TransformException e) {
					if (childMod == null) {
						LOG.debug("failed to restore a child of " + mod + " with modified depencencies: " + e.getMessage());
					} else {
						LOG.debug("failed to verify " + childMod + ": " + e.getMessage());
					}
					engine.withdrawMod(childNode);
				}
			}
			for (Node childNode : childrenNotToVerify.nodes()) {
				try {
					mods.add(mod.restoreChild(blocks.get(mod.stage+1), childNode));
				} catch (TransformException e) {
					LOG.error("failed to restore " + childNode + " with no modified dependencies; clearing all mods for " + this, e);
					engine.withdrawRule(id);
					throw e;
				}
			}
		}
	}
	
	private String buildToString(String primaryName) {
		StringBuilder sb = new StringBuilder();
		sb.append("rule<").append(id);
		if (primaryName != null && primaryName.length() > 0) sb.append(": ").append(primaryName);
		sb.append(">");
		return sb.toString();
	}

	@Override public String toString() {
		return toString;
	}
	
	@Deprecated @DatabaseTestCase.ConfigFile("test/conf.xml") @RunWith(JMock.class)
	public static class _ConstructorTest extends DatabaseTestCase {
		private Engine engine;
		private Node modStore;
		private XMLDocument ruleDoc;
		protected final Mockery mockery = new JUnit4Mockery() {{
			setImposteriser(ClassImposteriser.INSTANCE);
		}};
		
		@Before public void setUp() {
			modStore = db.getFolder("/").documents().load(Name.create("mods"), Source.xml(
					"<modstore xmlns='" + Engine.MOD_NS + "'>" + 
					"<mods rule='r1'>" +
					"	<mod xml:id='_r1.j-230.' stage='0'>" + 
					"		<dependency doc='model/something.java'/>" + 
					"		<reference refid='j-230'/>" + 
					"		<mod stage='1'>" + 
					"			<dependency doc='mappings.xml'/>" + 
					"			<reference refid='m-12'/>" + 
					"			<mod xml:id='_r1.j-230.m-12.' stage='2'>" + 
					"				<affected refid='_r1.j-230.m-12..1'/>" + 
					"			</mod>" + 
					"		</mod>" + 
					"	</mod>" +
					"</mods>" +
					"<mods rule='r2'>" +
					"	<mod xml:id='_r2.j-230.' stage='0'>" +
					"		<dependency doc='model/something.java'/>" +
					"		<reference refid='j-230'/>" +
					"	</mod>" +
					"</mods>" + 
					"</modstore>")).root();
			modStore.namespaceBindings().put("", Engine.MOD_NS);
			engine = mockery.mock(Engine.class);
			mockery.checking(new Expectations() {{
				allowing(engine).workspace(); will(returnValue(db.getFolder("/")));
				allowing(engine).modStore(); will(returnValue(modStore));
				allowing(engine).utilQuery(); will(returnValue(db.query()));
			}});
		}
		
		private Node makeRule(String attributes, String xml) {
			return (ruleDoc = db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<rule " + attributes + " xmlns='" + Engine.MIXT_NS + "'>" + xml + "</rule>"))).root();
		}
		
		@Test public void sameDefsWithName() throws RuleBaseException {
			Rule rule = new Rule(
					makeRule("xml:id='r1' desc='myrule'", "<create-doc>foobar</create-doc>"),
					makeRule("xml:id='r1' desc='myrule'", "<create-doc>foobar</create-doc>"),
					engine, null, new Engine.Module(ruleDoc), null);
			assertThat(rule.toString(), containsString("r1"));
			assertThat(rule.toString(), containsString("myrule"));
		}

		@Test public void sameDefsWithoutName() throws RuleBaseException {
			Rule rule = new Rule(
					makeRule("xml:id='r1'", "<create-doc>foobar</create-doc>"),
					makeRule("xml:id='r1'", "<create-doc>foobar</create-doc>"),
					engine, null, new Engine.Module(ruleDoc), null);
			assertThat(rule.toString(), containsString("r1"));
		}
		
		@Test public void diffDefsStage0() throws RuleBaseException {
			mockery.checking(new Expectations() {{
				one(engine).withdrawMods(modStore.query().all("//mods[@rule='r1']//mod"));
			}});
			new Rule(
					makeRule("xml:id='r1'", "<create-doc>foo</create-doc>"),
					makeRule("xml:id='r1'", "<create-doc>bar</create-doc>"),
					engine, null, new Engine.Module(ruleDoc), null);
		}

		@Test public void diffDefsStage2() throws RuleBaseException {
			mockery.checking(new Expectations() {{
				one(engine).withdrawMods(modStore.query().all("/id('_r1.j-230.m-12.')"));
			}});
			new Rule(
					makeRule("xml:id='r1'", "<create-doc>foo</create-doc><with some='$x'>foo</with><insert>goo</insert>"),
					makeRule("xml:id='r1'", "<create-doc>foo</create-doc><with some='$x'>foo</with>"),
					engine, null, new Engine.Module(ruleDoc), null);
		}
	}
	
	@Deprecated @DatabaseTestCase.ConfigFile("test/conf.xml") @RunWith(JMock.class)
	public static abstract class _RuleTest extends DatabaseTestCase {
		protected Rule rule;
		protected Node modStore;
		protected final Mockery mockery = new JUnit4Mockery() {{
			setImposteriser(ClassImposteriser.INSTANCE);
		}};
		
		@SuppressWarnings("unchecked")
		@Before public void setUpEngineAndCreateRule() {
			final Engine engine = mockery.mock(Engine.class);
			mockery.checking(new Expectations() {{
				allowing(engine).workspace(); will(returnValue(db.getFolder("/")));
				allowing(engine).utilQuery(); will(returnValue(db.query()));
			}});
			final Accumulator.Locator<XMLDocument> locator = mockery.mock(Accumulator.Locator.class);
			rule = new Rule(engine, locator);
			rule.self = mockery.mock(Rule.Shim.class);
		}
		
		protected void initEmptyModStore() {
			initLiteralModStore("");
		}
		
		protected void initLiteralModStore(String xml) {
			modStore = db.getFolder("/").documents().load(Name.create("mods"), Source.xml(
					"<modstore xmlns='" + Engine.MOD_NS + "'><mods xml:id='_r1.' rule='r1'>" + xml + "</mods></modstore>")).root();
			modStore.namespaceBindings().put("", Engine.MOD_NS);
			mockery.checking(new Expectations() {{
				allowing(rule.engine).modStore(); will(returnValue(modStore));
			}});
			try {
				Field field = Rule.class.getDeclaredField("rootModNode");
				field.setAccessible(true);
				field.set(rule, modStore.query().single("//mods").node());
			} catch (SecurityException e) {
				throw new RuntimeException(e);
			} catch (IllegalArgumentException e) {
				throw new RuntimeException(e);
			} catch (NoSuchFieldException e) {
				throw new RuntimeException(e);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}

		protected Node modNodeAt(int stage) {
			return modStore.query().single("//mod[@stage=$_1]", stage).node();
		}
		
		protected Node modNode(String id) {
			return modStore.query().single("/id($_1)", "_"+id).node();
		}
		
		protected <T extends Mod> T mockMod(Class<T> clazz, final String id, final int stage) {
			return mockMod(clazz, id, stage, null);
		}
		
		protected <T extends Mod> T mockMod(Class<T> clazz, final String id, final int stage, Mod parent) {
			final T mod = mockery.mock(clazz, "mod" + id + "@" + stage);
			mockery.checking(new Expectations() {{
				if (stage == -1) {
					allowing(rule.self).rootMod();  will(returnValue(mod));
				}
				allowing(mod).key();  will(returnValue(id));
				allowing(mod).node();  will(returnValue(stage == -1 ?
						modStore.query().single("mods[@rule='r1']").node() : modStore.query().optional("/id($_1)", id).node()));
			}});
			try {
				Field field = Mod.class.getDeclaredField("stage");
				field.setAccessible(true);
				field.setInt(mod, stage);
				field = Mod.class.getDeclaredField("rule");
				field.setAccessible(true);
				field.set(mod, rule);
				field = Mod.class.getDeclaredField("parent");
				field.setAccessible(true);
				field.set(mod, parent);
			} catch (SecurityException e) {
				throw new RuntimeException(e);
			} catch (IllegalArgumentException e) {
				throw new RuntimeException(e);
			} catch (NoSuchFieldException e) {
				throw new RuntimeException(e);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
			return mod;
		}
		
		protected Counter injectEngineCounter(String fieldName) {
			try {
				if (rule.engine.stats == null) {
					Field field = Engine.class.getDeclaredField("stats");
					field.setAccessible(true);
					field.set(rule.engine, new Engine.Stats());
				}
				final Counter counter = new Counter(fieldName + " = {0,number,integer}");
				Field field = Engine.Stats.class.getDeclaredField(fieldName);
				field.setAccessible(true);
				field.set(rule.engine.stats, counter);
				return counter;
			} catch (SecurityException e) {
				throw new RuntimeException(e);
			} catch (IllegalArgumentException e) {
				throw new RuntimeException(e);
			} catch (NoSuchFieldException e) {
				throw new RuntimeException(e);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}

	}
	
	@Deprecated
	public static class _UtilityTest extends _RuleTest {
		private Node makeBlock(String xml) {
			return db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<blocks xmlns='" + Engine.MIXT_NS + "'>" + xml + "</blocks>")).root().query().single("*").node();
		}
		
		@Test public void defineBlockWorks() throws RuleBaseException {
			Block block = rule.defineBlock(makeBlock("<for one='$x'>//method</for>"));
			assertTrue(block instanceof LinearBlock);
		}
		
		@Test(expected = RuleBaseException.class)
		public void defineBlockFailsOnUnknownBlockType() throws RuleBaseException {
			rule.defineBlock(makeBlock("<foobar>foo</foobar>"));
		}
		
		@Test(expected = RuleBaseException.class)
		public void defineBlockFailsOnIllegalAttribute() throws RuleBaseException {
			rule.defineBlock(makeBlock("<for into='$x'>foo</for>"));
		}
		
		@Test public void defineBlockAcceptsNamespacedAttribute() throws RuleBaseException {
			rule.defineBlock(makeBlock("<for xmlns:x='foo' x:into='$x' each='$y'>foo</for>"));
		}
		
		@Test public void compareBlocksEqual() {
			assertTrue(rule.equalBlocks(
					makeBlock("<for one='$x'>//method</for>"),
					makeBlock("<for one='$x'>//method</for>")));
		}

		@Test public void compareBlocksDifferentElement() {
			assertFalse(rule.equalBlocks(
					makeBlock("<for one='$x'>//method</for>"),
					makeBlock("<with one='$x'>//method</with>")));
		}

		@Test public void compareBlocksDifferentAttribute() {
			assertFalse(rule.equalBlocks(
					makeBlock("<for one='$x'>//method</for>"),
					makeBlock("<for each='$x'>//method</for>")));
		}

		@Test public void compareBlocksDifferentContent() {
			assertFalse(rule.equalBlocks(
					makeBlock("<for one='$x'>//method</for>"),
					makeBlock("<for one='$x'>//field</for>")));
		}
		
		@Test public void compareBlocksDifferentNumNamespaces() {
			assertFalse(rule.equalBlocks(
					makeBlock("<for one='$x' xmlns:java='foo'>//method</for>"),
					makeBlock("<for one='$x'>//method</for>")));
		}

		@Test public void compareBlocksDifferentPrefixes() {
			assertFalse(rule.equalBlocks(
					makeBlock("<for one='$x' xmlns:java='foo'>//java:method</for>"),
					makeBlock("<for one='$x' xmlns:java2='foo'>//java2:method</for>")));
		}

		@Test public void compareBlocksDifferentNamespaces() {
			assertFalse(rule.equalBlocks(
					makeBlock("<for one='$x' xmlns:java='foo'>//java:method</for>"),
					makeBlock("<for one='$x' xmlns:java='bar'>//java:method</for>")));
		}
		
		@Test public void convertDocsToNames() {
			String[] docNames = {"a", "b", "c", "d"};
			Set<XMLDocument> docs = new HashSet<XMLDocument>();
			for (final String docName : docNames) {
				final XMLDocument doc = db.getFolder("/").documents().load(Name.create(docName), Source.xml("<foo/>"));
				docs.add(doc);
				mockery.checking(new Expectations() {{
					one(rule.engine).relativePath(doc); will(returnValue(docName));
				}});
			}
			assertEquals(new HashSet<String>(Arrays.asList(docNames)),
					new HashSet<String>(rule.convertDocsToNames(docs)));
		}
		
		@Test public void writeBlockTypeVersions() {
			ElementBuilder<XMLDocument> builder = db.getFolder("/").documents().build(Name.generate()).elem("root");
			Rule.writeBlockTypeVersions(builder);
			XMLDocument doc = builder.end("root").commit();
			doc.namespaceBindings().put("", Engine.RECORD_NS);
			assertEquals(BLOCK_CLASSES.length, doc.query().all("//block-type").size());
			for (BlockType blockType : BLOCK_TYPE_DICTIONARY.values()) {
				assertTrue(doc.query().exists("//block-type[@class=$_1][@version=$_2]", blockType.getClass().getName(), blockType.version()));
			}
		}
		
		@Test public void verifyBlockTypeVersions() {
			XMLDocument doc = db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<root xmlns='" + Engine.RECORD_NS + "'>" +
					"  <block-type class='com.ideanest.dscribe.mixt.blocks.For' version='" + new For().version() + "'/>" +
					"  <block-type class='com.ideanest.dscribe.mixt.blocks.With' version='" + new With().version() + "foo'/>" +
					"</root>"));
			Collection<QName> badBlockNames = Rule.verifyBlockTypeVersions(doc);
			NamespaceMap ns = new NamespaceMap("", Engine.MIXT_NS);
			assertFalse("good version", badBlockNames.contains(QName.parse("for", ns)));
			assertTrue("bad version", badBlockNames.contains(QName.parse("with", ns)));
			assertTrue("missing record", badBlockNames.contains(QName.parse("insert", ns)));
		}
	}

	@Deprecated
	public static class _ParseBlocksTest extends _RuleTest {
		final Engine.Module module = mockery.mock(Engine.Module.class, "module");
		final Engine.Module prevModule = mockery.mock(Engine.Module.class, "prevModule");

		@Before public void allowEmptyFunctionsModifiedCalls() {
			mockery.checking(new Expectations() {{
				allowing(module).areFunctionsModified(Collections.<QName>emptySet(), prevModule);
				will(returnValue(false));
			}});
		}
		
		private Node makeRule(String xml) {
			return db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<rule xmlns='" + Engine.MIXT_NS + "'>" + xml + "</rule>")).root();
		}
		
		@Before public void overrideMockShim() throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
			initEmptyModStore();
			rule.initDefaultShim();
		}
		
		@Test(expected = RuleBaseException.class)
		public void badBlock() throws RuleBaseException {
			rule.parseBlocks(makeRule("<foo/>"), db.query().optional("inexistent").node(), module, prevModule);
		}
		
		@Test public void noPrevDef1() throws RuleBaseException {
			rule.parseBlocks(
					makeRule("<for each='$x'> //method </for>"),
					db.query().optional("inexistent").node(),
					module, prevModule);
			assertEquals(0, rule.firstDifferentStage);
			assertEquals(1, rule.blocks.size());
		}
		
		@Test public void noPrevDef2() throws RuleBaseException {
			rule.parseBlocks(
					makeRule("<for each='$x'> //method </for><insert> <operation/> </insert>"),
					db.query().optional("inexistent").node(),
					module, prevModule);
			assertEquals(0, rule.firstDifferentStage);
			assertEquals(2, rule.blocks.size());
		}
		
		@Test public void prevUnknownBlock() throws RuleBaseException {
			rule.parseBlocks(
					makeRule("<for each='$x'> //method </for>"),
					makeRule("<foo each='$x'> //method </foo>"),
					module, prevModule);
			assertEquals(0, rule.firstDifferentStage);
			assertEquals(1, rule.blocks.size());
		}
		
		@Test public void samePrevDef1() throws RuleBaseException {
			rule.parseBlocks(
					makeRule("<for each='$x'> //method </for>"),
					makeRule("<for each='$x'> //method </for>"),
					module, prevModule);
			assertEquals(Integer.MAX_VALUE, rule.firstDifferentStage);
			assertEquals(1, rule.blocks.size());
		}

		@Test public void samePrevDef2() throws RuleBaseException {
			rule.parseBlocks(
					makeRule("<for each='$x'> //method </for><insert> <operation/> </insert>"),
					makeRule("<for each='$x'> //method </for><insert> <operation/> </insert>"),
					module, prevModule);
			assertEquals(Integer.MAX_VALUE, rule.firstDifferentStage);
			assertEquals(2, rule.blocks.size());
		}
		
		@Test public void lastBlockDiff() throws RuleBaseException {
			rule.parseBlocks(
					makeRule("<for each='$x'> //method </for><insert> <operation/> </insert>"),
					makeRule("<for each='$x'> //method </for><insert> <op/> </insert>"),
					module, prevModule);
			assertEquals(1, rule.firstDifferentStage);
			assertEquals(2, rule.blocks.size());
		}
		
		@Test public void middleBlockDiff() throws RuleBaseException {
			rule.parseBlocks(
					makeRule("<for each='$x'> //method </for><with some='$y'> $x/foo </with><insert> <operation/> </insert>"),
					makeRule("<for each='$x'> //method </for><with any='$y'> $x/foo </with><insert> <operation/> </insert>"),
					module, prevModule);
			assertEquals(1, rule.firstDifferentStage);
			assertEquals(3, rule.blocks.size());
		}
		
		@Test public void twoBlocksDiff() throws RuleBaseException {
			rule.parseBlocks(
					makeRule("<for each='$x'> //method </for><with some='$y'> $x/foo </with><insert> <operation/> </insert>"),
					makeRule("<for each='$x'> //method </for><with any='$y'> $x/foo </with><insert> <op/> </insert>"),
					module, prevModule);
			assertEquals(1, rule.firstDifferentStage);
			assertEquals(3, rule.blocks.size());
		}
		
		@Test public void prevDefLonger() throws RuleBaseException {
			rule.parseBlocks(
					makeRule("<for each='$x'> //method </for><with any='$y'> $x/foo </with>"),
					makeRule("<for each='$x'> //method </for><with any='$y'> $x/foo </with><insert> <operation/> </insert>"),
					module, prevModule);
			assertEquals(2, rule.firstDifferentStage);
			assertEquals(2, rule.blocks.size());
		}

		@Test public void prevDefShorter() throws RuleBaseException {
			rule.parseBlocks(
					makeRule("<for each='$x'> //method </for><with some='$y'> $x/foo </with><insert> <operation/> </insert>"),
					makeRule("<for each='$x'> //method </for><with some='$y'> $x/foo </with>"),
					module, prevModule);
			assertEquals(2, rule.firstDifferentStage);
			assertEquals(3, rule.blocks.size());
		}
		
		@Test public void noPrevDefModifiedFunction() throws RuleBaseException {
			rule.parseBlocks(
					makeRule("<for each='$x'> local:foo(//method) </for>"),
					db.query().optional("inexistent").node(),
					module, prevModule);
			assertEquals(0, rule.firstDifferentStage);
			assertEquals(1, rule.blocks.size());
		}
		
		@Test public void samePrevDefModifiedFunction() throws RuleBaseException {
			mockery.checking(new Expectations() {{
				one(module).areFunctionsModified(
						Collections.singleton(new QName("http://www.w3.org/2005/xquery-local-functions", "value", null)),
						prevModule);
				will(returnValue(true));
			}});
			rule.parseBlocks(
					makeRule("<for each='$x'> //method </for><insert> <operation x='{local:value()}'/> </insert>"),
					makeRule("<for each='$x'> //method </for><insert> <operation x='{local:value()}'/> </insert>"),
					module, prevModule);
			assertEquals(1, rule.firstDifferentStage);
			assertEquals(2, rule.blocks.size());
		}
		
		@Test public void samePrevDefUnmodifiedFunction() throws RuleBaseException {
			mockery.checking(new Expectations() {{
				one(module).areFunctionsModified(
						Collections.singleton(new QName("http://www.w3.org/2005/xquery-local-functions", "value", null)),
						prevModule);
				will(returnValue(false));
			}});
			rule.parseBlocks(
					makeRule("<for each='$x'> //method </for><insert> <operation x='{local:value()}'/> </insert>"),
					makeRule("<for each='$x'> //method </for><insert> <operation x='{local:value()}'/> </insert>"),
					module, prevModule);
			assertEquals(Integer.MAX_VALUE, rule.firstDifferentStage);
			assertEquals(2, rule.blocks.size());
		}
		
		@Test public void middleBlockDiffLastBlockModifiedFunction() throws RuleBaseException {
			rule.parseBlocks(
					makeRule("<for each='$x'> //method </for><with some='$y'> $x/foo </with> <insert> <operation x='{local:value()}'/> </insert>"),
					makeRule("<for each='$x'> //method </for><with any='$y'> $x/foo </with> <insert> <operation x='{local:value()}'/> </insert>"),
					module, prevModule);
			assertEquals(1, rule.firstDifferentStage);
			assertEquals(3, rule.blocks.size());
		}
		

	}

	@Deprecated
	public static class _ModTest extends _RuleTest {
		private List<Block> blocks;
		
		@Before public void setUp() {
			initLiteralModStore(
					"<mod stage='0' xml:id='_r1.0.'>" + 
					"	<dependency doc='model/something.java'/>" + 
					"	<reference refid='j-230'/>" + 
					"	<mod stage='1' xml:id='_r1.1.'>" + 
					"		<dependency doc='mappings.xml'/>" + 
					"		<reference refid='m-12'/>" + 
					"		<mod stage='2' xml:id='_r1.2.'>" + 
					"			<affected refid='_r1.j-230.m-12..1'/>" + 
					"			<mod xml:id='_r1.3.' stage='3'>" + 
					"				<dependency doc='foo.xml'/>" +
					"				<reference refid='m-14'/>" + 
					"			</mod>" + 
					"		</mod>" + 
					"	</mod>" +
					"</mod>");
			
			blocks = Arrays.asList(new Block[] {
					mockery.mock(LinearBlock.class, "b0"), mockery.mock(KeyBlock.class, "b1"),
					mockery.mock(LinearBlock.class, "b2"), mockery.mock(KeyBlock.class, "b3")
			});
			
			rule.blocks.addAll(blocks);
			rule.firstDifferentStage = Integer.MAX_VALUE;
		}
		
		private List<Mod> mockModRestoreSequence(final int lastStage) throws TransformException {
			final List<Mod> mods = new ArrayList<Mod>(lastStage+2);
			mods.add(mockMod(KeyMod.class, "_r1.", -1));
			for (int i = 0; i <= lastStage; i++) {
				mods.add(mockMod(blocks.get(i) instanceof KeyBlock ? KeyMod.class : Mod.class, "_r1." + i +".", i));
			}
			final Sequence s = mockery.sequence("stages");
			for (int i = -1; i < lastStage; i++) {
				final int index = i;
				mockery.checking(new Expectations() {{
					one(mods.get(index+1)).restoreChild(	
							with(same(blocks.get(index+1))), with(equal(modNodeAt(index+1))));
					inSequence(s);  will(returnValue(mods.get(index+2)));
				}});
			}
			return mods;
		}
		
		@Test public void restoreMod1() throws TransformException {
			final List<Mod> mods = mockModRestoreSequence(1);
			injectEngineCounter("numModsRestored");
			assertSame(mods.get(2), rule.restoreMod(modNodeAt(1), null));
			assertEquals(1, rule.engine.stats.numModsRestored.value());
		}

		@Test public void restoreMod2() throws TransformException {
			final List<Mod> mods = mockModRestoreSequence(2);
			injectEngineCounter("numModsRestored");
			assertSame(mods.get(3), rule.restoreMod(modNodeAt(2), null));
			assertEquals(1, rule.engine.stats.numModsRestored.value());
		}
		
		@Test public void restoreMod3() throws TransformException {
			injectEngineCounter("numModsRestored");
			final List<Mod> mods = new ArrayList<Mod>(2+2);
			mods.add(mockMod(KeyMod.class, "_r1.", -1));
			for (int i = 0; i <= 2; i++) {
				mods.add(mockMod(blocks.get(i) instanceof KeyBlock ? KeyMod.class : Mod.class, "_r1." + i +".", i, mods.get(i)));
			}
			final Node forkedNode = modNodeAt(1).append().elem("mod").attr("stage", 2).end("mod").commit();
			final Mod forkedMod = mockMod(Mod.class, "_r1.2b.", 2, mods.get(2));
			mockery.checking(new Expectations() {{
				one(mods.get(2)).restoreChild(	
						with(same(blocks.get(2))), with(equal(forkedNode)));
				will(returnValue(forkedMod));
			}});
			assertSame(forkedMod, rule.restoreMod(forkedNode, mods.get(3)));
			assertEquals(1, rule.engine.stats.numModsRestored.value());
		}
		
		@Test public void processStageAtRootStageNoKeyHasMod() throws TransformException {
			injectEngineCounter("numBlocksResolved");
			final QueryService scope = modStore.query();
			mockMod(KeyMod.class, "_r1.", -1);
			assertEquals(0, rule.processStage(0, scope));
			assertEquals(0, rule.engine.stats.numBlocksResolved.value());
		}
		
		@Test public void processStageAtRootStageIsKeyHasMod() throws TransformException {
			injectEngineCounter("numBlocksResolved");
			final QueryService scope = modStore.query();
			final KeyMod rootMod = mockMod(KeyMod.class, "_r1.", -1);
			blocks.set(0, mockery.mock(KeyBlock.class, "b0k"));
			rule.blocks.clear();  rule.blocks.addAll(blocks);
			mockery.checking(new Expectations() {{
				one(rootMod).resolveChildren(blocks.get(0), false, scope); will(returnValue(2));
			}});
			assertEquals(2, rule.processStage(0, scope));
			assertEquals(1, rule.engine.stats.numBlocksResolved.value());
		}
		
		@Test public void processStageAtRootStageNoKeyNoMod() throws TransformException {
			injectEngineCounter("numBlocksResolved");
			final QueryService scope = modStore.query();
			final KeyMod rootMod = mockMod(KeyMod.class, "_r1.", -1);
			modNodeAt(0).delete();
			mockery.checking(new Expectations() {{
				one(rootMod).resolveChildren(blocks.get(0), false, scope); will(returnValue(2));
			}});
			assertEquals(2, rule.processStage(0, scope));
			assertEquals(1, rule.engine.stats.numBlocksResolved.value());
		}
		
		@Test public void processStageAtRootStageIsKeyNoMod() throws TransformException {
			injectEngineCounter("numBlocksResolved");
			final QueryService scope = modStore.query();
			final KeyMod rootMod = mockMod(KeyMod.class, "_r1.", -1);
			blocks.set(0, mockery.mock(KeyBlock.class, "b0k"));
			rule.blocks.clear();  rule.blocks.addAll(blocks);
			modNodeAt(0).delete();
			mockery.checking(new Expectations() {{
				one(rootMod).resolveChildren(blocks.get(0), false, scope); will(returnValue(2));
			}});
			assertEquals(2, rule.processStage(0, scope));
			assertEquals(1, rule.engine.stats.numBlocksResolved.value());
		}
		
		@Test public void processStageAtPreKeyStage() throws TransformException {
			injectEngineCounter("numBlocksResolved");
			final QueryService scope = modStore.query();
			mockMod(KeyMod.class, "_r1.", -1);
			final Mod resultMod = mockMod(Mod.class, "_r1.2.", 2);
			mockery.checking(new Expectations() {{
				one(rule.self).restoreMod(modNodeAt(2), null);  will(returnValue(resultMod));
				one(resultMod).resolveChildren(blocks.get(3), true, scope); will(returnValue(2));
			}});
			assertEquals(2, rule.processStage(3, scope));
			assertEquals(1, rule.engine.stats.numBlocksResolved.value());
		}
		
		@Test public void processStageAtPreLinearStageHasMod() throws TransformException {
			injectEngineCounter("numBlocksResolved");
			final QueryService scope = modStore.query();
			mockMod(KeyMod.class, "_r1.", -1);
			assertEquals(0, rule.processStage(2, scope));
			assertEquals(0, rule.engine.stats.numBlocksResolved.value());
		}
		
		@Test public void processStageAtPreLinearStageNoMod() throws TransformException {
			injectEngineCounter("numBlocksResolved");
			final QueryService scope = modStore.query();
			mockMod(KeyMod.class, "_r1.", -1);
			final Mod resultMod = mockMod(KeyMod.class, "_r1.1.", 1);
			mockery.checking(new Expectations() {{
				one(rule.self).restoreMod(modNodeAt(1), null);  will(returnValue(resultMod));
				one(resultMod).resolveChildren(blocks.get(2), false, scope); will(returnValue(2));
			}});
			modNodeAt(2).delete();
			assertEquals(2, rule.processStage(2, scope));
			assertEquals(1, rule.engine.stats.numBlocksResolved.value());
		}
		
		@Test public void processStageUsesPrevMod() throws TransformException {
			injectEngineCounter("numBlocksResolved");
			final QueryService scope = modStore.query();
			mockMod(KeyMod.class, "_r1.", -1);
			final Node firstNode = modNodeAt(2);
			final Node secondNode = modNodeAt(1).append().elem("mod").attr("stage", 2).end("mod").commit();
			final Mod resultMod1 = mockMod(Mod.class, "_r1.2a.", 2);
			final Mod resultMod2 = mockMod(Mod.class, "_r1.2b.", 2);
			mockery.checking(new Expectations() {{
				one(rule.self).restoreMod(firstNode, null);  will(returnValue(resultMod1));
				one(resultMod1).resolveChildren(blocks.get(3), true, scope); will(returnValue(2));
				one(rule.self).restoreMod(secondNode, resultMod1);  will(returnValue(resultMod2));
				one(resultMod2).resolveChildren(blocks.get(3), true, scope); will(returnValue(4));
			}});
			assertEquals(6, rule.processStage(3, scope));
			assertEquals(2, rule.engine.stats.numBlocksResolved.value());
		}
		
	}

	@Deprecated
	public static class _VerifyModTreeTest extends _RuleTest {
		private Collection<String> modifiedDocsNames = Collections.<String>emptyList();
		private Map<String, KeyMod> mods = new HashMap<String, KeyMod>();
		private Mod rootMod;
		
		@Before public void setUp() throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
			initEmptyModStore();
			rootMod = mockMod(KeyMod.class, "_r1.", -1);
			injectEngineCounter("numBlocksVerified");
		}
		
		private void createMods(String... ids) throws Exception {
			for (String id : ids) createMod(id);
		}
		
		private void createMod(String id) throws Exception {
			id = "_" + id;
			int stage = id.split("\\.").length-1;
			if (rule.blocks.size() <= stage) rule.blocks.add(mockery.mock(KeyBlock.class, "block-" + stage));
			int k = id.lastIndexOf('.');
			Mod parent = k == -1 ? rootMod : mods.get(id.substring(1, k));
			Node parentNode = parent.node();
			parentNode.append().elem("mod").attr("xml:id", id).attr("stage", stage).end("mod").commit();
			KeyMod mod = mockMod(KeyMod.class, id, stage);
			Field mod_parent = Mod.class.getDeclaredField("parent");
			mod_parent.setAccessible(true);
			mod_parent.set(mod, parent);
			mods.put(id.substring(1), mod);
		}

		private void expectRestoreAndVerify(final String nodeId, final boolean succeed) throws TransformException {
			expectRestore(nodeId, true);
			final Mod mod = mods.get(nodeId);
			mockery.checking(new Expectations() {{
				one(mod).verify();
				if (!succeed) will(throwException(new TransformException()));
			}});
		}
		
		private void expectRestore(final String nodeId, final boolean succeed) throws TransformException {
			final Mod mod = mods.get(nodeId);
			mockery.checking(new Expectations() {{
				one(mod.parent).restoreChild(with(same(rule.blocks.get(mod.stage))), with(equal(modStore.query().single("/id($_1)", "_"+nodeId).node())));
				will(succeed ? returnValue(mod) : throwException(new TransformException()));
			}});
		}
		
		private ItemList modNodes(String... ids) {
			List<String> prefixedIds = new ArrayList<String>(ids.length);
			for (String id : ids) prefixedIds.add("_"+id);
			return modStore.query().all("/id($_1)", prefixedIds);
		}
		
		@Test public void singleVerified() throws Exception {
			createMods("1");
			expectRestoreAndVerify("1", true);
			rule.verifyModTree(modNodes("1"), modifiedDocsNames);
			assertEquals(1, rule.engine.stats.numBlocksVerified.value());
		}
		
		@Test public void singleVerifiedFailure() throws Exception {
			createMods("1");
			expectRestoreAndVerify("1", false);
			mockery.checking(new Expectations() {{
				one(rule.engine).withdrawMod(modStore.query().single("/id('_1')").node());
			}});
			rule.verifyModTree(modNodes("1"), modifiedDocsNames);
			assertEquals(0, rule.engine.stats.numBlocksVerified.value());
		}
		
		@Test public void linearChain() throws Exception {
			createMods("1", "1.1");
			expectRestoreAndVerify("1", true);
			expectRestoreAndVerify("1.1", true);
			rule.verifyModTree(modNodes("1", "1.1"), modifiedDocsNames);
			assertEquals(2, rule.engine.stats.numBlocksVerified.value());
		}
		
		@Test public void deepLinearChain() throws Exception {
			createMods("1", "1.1", "1.1.1");
			expectRestoreAndVerify("1", true);
			expectRestoreAndVerify("1.1", true);
			expectRestoreAndVerify("1.1.1", true);
			rule.verifyModTree(modNodes("1", "1.1", "1.1.1"), modifiedDocsNames);
			assertEquals(3, rule.engine.stats.numBlocksVerified.value());
		}
		
		@Test public void split() throws Exception {
			createMods("1", "2");
			expectRestoreAndVerify("1", true);
			expectRestoreAndVerify("2", true);
			rule.verifyModTree(modNodes("1", "2"), modifiedDocsNames);
			assertEquals(2, rule.engine.stats.numBlocksVerified.value());
		}
		
		@Test public void multipleSplit() throws Exception {
			createMods("1", "1.1", "1.2", "2", "2.1");
			expectRestoreAndVerify("1", true);
			expectRestoreAndVerify("1.1", true);
			expectRestoreAndVerify("1.2", true);
			expectRestoreAndVerify("2", true);
			expectRestoreAndVerify("2.1", true);
			rule.verifyModTree(modNodes("1", "1.1", "1.2", "2", "2.1"), modifiedDocsNames);
			assertEquals(5, rule.engine.stats.numBlocksVerified.value());
		}
		
		@Test public void skipOver() throws Exception {
			createMods("1", "1.1");
			expectRestore("1", true);
			expectRestoreAndVerify("1.1", true);
			rule.verifyModTree(modNodes("1.1"), modifiedDocsNames);
			assertEquals(1, rule.engine.stats.numBlocksVerified.value());
		}
		
		@Test public void commonUnchangedAncestor() throws Exception {
			createMods("1", "1.1", "1.2");
			expectRestore("1", true);
			expectRestoreAndVerify("1.1", true);
			expectRestoreAndVerify("1.2", true);
			rule.verifyModTree(modNodes("1.1", "1.2"), modifiedDocsNames);
			assertEquals(2, rule.engine.stats.numBlocksVerified.value());
		}
		
		@Test public void ignoresUnchangedBranches() throws Exception {
			createMods("1", "1.1", "2", "2.1");
			expectRestore("1", true);
			expectRestoreAndVerify("1.1", true);
			rule.verifyModTree(modNodes("1.1"), modifiedDocsNames);
			assertEquals(1, rule.engine.stats.numBlocksVerified.value());
		}
		
		@Test(expected = TransformException.class)
		public void restoreErrorWithdrawsRule() throws Exception {
			createMods("1", "1.1");
			expectRestore("1", false);
			mockery.checking(new Expectations() {{
				one(rule.engine).withdrawRule("r1");
			}});
			rule.verifyModTree(modNodes("1.1"), modifiedDocsNames);
		}
	}
	
	@Deprecated
	public static class _ProcessTest extends _RuleTest {
		private static Field queryServiceDocs;
		private static Method queryServicePrepareContext;

		@BeforeClass public static void setUpReflectionAccessors() throws SecurityException, NoSuchFieldException, NoSuchMethodException {
			queryServiceDocs = QueryService.class.getDeclaredField("docs");
			queryServiceDocs.setAccessible(true);
			queryServicePrepareContext = QueryService.class.getDeclaredMethod("prepareContext", DBBroker.class);
			queryServicePrepareContext.setAccessible(true);
		}
		
		private Matcher<QueryService> customScope;
		private Sequence seq;
		
		@Before public void setUp() {
			// set up a confuser mod for another rule
			initEmptyModStore();
			modStore.query().run("update insert <mods rule='r2' xml:id='_r2.'><mod stage='0'/></mods> into .");

			rule.firstDifferentStage = Integer.MAX_VALUE;
			mockMod(KeyMod.class, "_r1.", -1);
			injectEngineCounter("numModsCompleted");
			
			seq = mockery.sequence("process");
		}
		
		private void prepScenario(final boolean doGlobalProcessing, final boolean verifySuccessful) throws TransformException {
			final Set<XMLDocument> modifiedDocs = new HashSet<XMLDocument>();
			// add some documents to list in modifiedDocs
			modifiedDocs.add(db.getFolder("/").documents().load(Name.create("somedoc"), Source.xml("<data/>")));
			modifiedDocs.add(db.createFolder("/foo").documents().load(Name.create("otherdoc"), Source.xml("<data/>")));
			
			mockery.checking(new Expectations() {{
				one(rule.modifiedDocsLocator).catchUp();  will(returnValue(modifiedDocs));
				if (!doGlobalProcessing) {
					one(rule.self).verifyMods(modifiedDocs);
					if (!verifySuccessful) will(throwException(new TransformException()));
				}
			}});
			customScope = (!doGlobalProcessing && verifySuccessful) ? Matchers.<QueryService>notNullValue() : Matchers.<QueryService>nullValue();
		}
		
		private void prepIteration(Class<? extends Block> blockType, final int numModsToResolve) throws TransformException {
			final int stage = rule.blocks.size();
			final Block block = mockery.mock(blockType, "block_" + rule.blocks.size());
			rule.blocks.add(block);
			mockery.checking(new Expectations() {{
				one(rule.self).processStage(with(equalTo(stage)), with(customScope)); inSequence(seq);
				will(returnValue(numModsToResolve));
			}});
		}
		
		private void processRuleExpectingCompleted(boolean doGlobalProcessing, int expectedNumModsCompleted) throws TransformException {
			rule.process(doGlobalProcessing);
			assertEquals("num blocks completed", expectedNumModsCompleted, rule.engine.stats.numModsCompleted.value());
		}
		
		@Test public void emptyRun() throws TransformException {
			prepScenario(false, true);
			prepIteration(KeyBlock.class, 0);
			processRuleExpectingCompleted(false, 0);
		}
		
		@Test public void simpleRun() throws TransformException {
			prepScenario(false, true);
			prepIteration(KeyBlock.class, 2);
			prepIteration(LinearBlock.class, 1);
			prepIteration(LinearBlock.class, 1);
			processRuleExpectingCompleted(false, 1);
		}
		
		@Test public void verifyFailed() throws TransformException {
			prepScenario(false, false);
			prepIteration(KeyBlock.class, 2);
			prepIteration(LinearBlock.class, 1);
			prepIteration(LinearBlock.class, 1);
			processRuleExpectingCompleted(false, 1);
		}
		
		@Test public void globalProcessing() throws TransformException {
			prepScenario(true, false);
			prepIteration(KeyBlock.class, 2);
			prepIteration(LinearBlock.class, 1);
			prepIteration(LinearBlock.class, 1);
			processRuleExpectingCompleted(true, 1);
		}

		
	}
}