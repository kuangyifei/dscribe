package com.ideanest.dscribe.mixt;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

import java.lang.reflect.*;
import java.text.MessageFormat;
import java.util.*;

import org.apache.log4j.Logger;
import org.exist.dom.*;
import org.exist.fluent.*;
import org.exist.fluent.QName;
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
	
	/**
	 * Write out the versions of all defined block types, in a format that can be used in a later run to
	 * detect changed versions.  The builder should be writing to (some descendant of) the resource that
	 * will later be provided as the <code>prevrulespace</code>.
	 * 
	 * @param builder an element builder on the desired target resource
	 */
	static void writeBlockTypeVersions(ElementBuilder<?> builder) {
		builder.namespace("record", Transformer.RECORD_NS);
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
			final String lastVersion = record.query().namespace("record", Transformer.RECORD_NS).optional(
					"//record:block-type[@class=$_1]/@version", blockType.getClass().getName()).value();
			if (!blockType.version().equals(lastVersion)) {
				LOG.info("block " + blockType.xmlName() + " changed from version " + lastVersion + " to " + blockType.version());
				badBlockNames.add(blockType.xmlName());
			}
		}
		return badBlockNames;
	}
	
	public final Engine engine;
	final String id;
	private final String toString;
	private final List<Block> blocks = new ArrayList<Block>();
	private Set<Document> touched = new HashSet<Document>();
	private final Accumulator.Locator<XMLDocument> modifiedDocsLocator;
	private int firstDifferentStage;

	private Shim self;  // for testing only

	Rule(Node def, Node prevDef, Engine engine, Accumulator.Locator<XMLDocument> modifiedDocsLocator) throws RuleBaseException {
		this.engine = engine;
		this.modifiedDocsLocator = modifiedDocsLocator;
		initDefaultShim();
		
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
			engine.withdrawMods(engine.modStore().query().unordered(
					"//mod[@rule=$_1][xs:integer(@stage) >= $_2]", id, firstDifferentStage));
		}
	}

	// for testing only
	private Rule(Engine engine, Accumulator.Locator<XMLDocument> modifiedDocsLocator) {
		this.engine = engine;
		this.modifiedDocsLocator = modifiedDocsLocator;
		this.id = "r1";
		toString = "rule[r1]";
		initDefaultShim();
	}

	private interface Shim {
		KeyMod bootstrapMod();
		KeyMod restore(Node modNode, KeyMod baseMod) throws TransformException;
		void verifyMods(Set<XMLDocument> modifiedDocs) throws TransformException;
		void verifySubtree(KeyMod mod, ItemList modsToVerify, Collection<String> modifiedDocsNames) throws TransformException;
		KeyMod restoreAndVerify(Node modNode, KeyMod baseMod, boolean terminal, Collection<String> modifiedDocNames);
	}
	
	private void initDefaultShim() {
		this.self = new Shim() {
			public KeyMod bootstrapMod() {
				return Rule.this.bootstrapMod();
			}
			public KeyMod restore(Node modNode, KeyMod baseMod) throws TransformException {
				return Rule.this.restore(modNode, baseMod);
			}
			public void verifyMods(Set<XMLDocument> modifiedDocs) throws TransformException {
				Rule.this.verifyMods(modifiedDocs);
			}
			public void verifySubtree(KeyMod mod, ItemList modsToVerify, Collection<String> modifiedDocsNames) throws TransformException {
				Rule.this.verifySubtree(mod, modsToVerify, modifiedDocsNames);
			}
			public KeyMod restoreAndVerify(Node modNode, KeyMod baseMod, boolean terminal, Collection<String> modifiedDocNames) {
				return Rule.this.restoreAndVerify(modNode, baseMod, terminal, modifiedDocNames);
			}
		};
	}
	
	/**
	 * Parse all the blocks in the given rule definition, comparing it to a previous definition of the rule.
	 * Fill the <code>blocks</code> list with the blocks and analyze each one.  Return the index of the
	 * first different block -- but note that this doesn't take implementation changes into account!
	 *
	 * @param def this rule's definition node, of which the block definitions are the children
	 * @param prevDef the previous version of the rule's definition block; cannot be <code>null</code>, but
	 * 		can be an inexistent node
	 * @return the index of the first block whose definition differs from the previous version, or
	 * 		<code>Integer.MAX_VALUE</code> if both versions of the rule are exactly the same
	 * @throws RuleBaseException if unable to instantiate a block from its definition at any point
	 */
	private int parseBlocks(Node def, Node prevDef) throws RuleBaseException {
		int firstDiff = Integer.MAX_VALUE;
		try {
			Mod mod = self.bootstrapMod();
			Iterator<Node> prevBlocksIterator = prevDef.query().all("*").nodes().iterator();
			for (Node blockDef : def.query().all("*").nodes()) {
				
				if (firstDiff == Integer.MAX_VALUE &&
						!(prevBlocksIterator.hasNext() && equalBlocks(blockDef, prevBlocksIterator.next()))) {
					firstDiff = blocks.size();
				}
				
				Block block = defineBlock(blockDef);
				mod = mod.deriveChild(block, block instanceof KeyBlock ? "x" : null);		// a bogus key, just to make the mod believable
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
		validateAttributes(blockDef, blockType);
		Block block = blockType.define(blockDef);
		boolean isLinear = block instanceof LinearBlock, isKey = block instanceof KeyBlock;
		if (isLinear && isKey) throw new RuleBaseException("block " + block + " is both key and linear");
		if (!(isLinear || isKey)) throw new RuleBaseException("block " + block + " is neither key nor linear");
		return block;
	}

	private void validateAttributes(Node blockDef, BlockType blockType) throws RuleBaseException {
		try {
			AllowAttributes annotation = blockType.getClass().getMethod("define", Node.class).getAnnotation(AllowAttributes.class);
			Collection<String> allowedAttributes = annotation == null ? Collections.<String>emptySet() : Arrays.asList(annotation.value());
			Collection<String> attributes = new ArrayList<String>(Arrays.asList(blockDef.query().all(
					"let $a := @*[namespace-uri() = ''] return if ($a) then $a/local-name() else ()").values().toArray()));
			attributes.removeAll(allowedAttributes);
			if (!attributes.isEmpty()) {
				throw new RuleBaseException(this + " illegal attributes " + attributes + " on " + blockDef);
			}
		} catch (SecurityException e) {
			throw new RuntimeException("could not get define method on " + blockType.getClass(), e);
		} catch (NoSuchMethodException e) {
			throw new RuntimeException("could not get define method on " + blockType.getClass(), e);
		}
	}
	
	<D extends Document> void addTouched(Collection<D> docs) {
		touched.addAll(docs);
	}
	
	void process(boolean doGlobalProcessing) throws TransformException {
		LOG.debug("processing " + this);
		
		Set<XMLDocument> modifiedDocs = modifiedDocsLocator.catchUp();
		
		QueryService touchedScope = null;
		if (!doGlobalProcessing) {
			try {
				self.verifyMods(modifiedDocs);
				touched.addAll(modifiedDocs);
				touchedScope = engine.globalScope().database().query(touched);
			} catch (TransformException e) {
				// inconsistent state, rule withdrawn, do global processing after all
			}
		}
		touched = new HashSet<Document>();	// don't clear, old set still referenced by touchedScope
		
		Collection<Mod> mods = new ArrayList<Mod>();
		mods.add(self.bootstrapMod());
		
		for (int stage = 0; stage < blocks.size(); stage++) {
			Block block = blocks.get(stage);
			boolean lastBlock = stage == blocks.size()-1;
			LOG.debug("processing " + block);
			
			if (stage > 0) {
				LOG.debug("restoring mods in progress");
				Set<String> modKeys = new HashSet<String>();
				for (Mod mod : mods) modKeys.add(mod.key());
				for (Node node : engine.modStore().query().unordered("mod[@rule=$_1][xs:integer(@stage)=$_2]", id, stage-1).nodes()) {
					if (!modKeys.contains(node.query().single("@xml:id").value())) {
						mods.add(self.restore(node, self.bootstrapMod()));
					}
				}
			}
			
			LOG.debug("resolving mods");
			engine.numBlocksResolved.increment(mods.size());
			Collection<Mod> nextStageMods = new ArrayList<Mod>();
			for (Mod mod : mods) nextStageMods.addAll(
					mod.resolveChildren(block, lastBlock, (stage >= firstDifferentStage) ? null : touchedScope));
			mods = nextStageMods;
		}
		
		engine.numModsCompleted.increment(mods.size());
		firstDifferentStage = Integer.MAX_VALUE;
	}

	private KeyMod bootstrapMod() {
		return Mod.bootstrap(this);
	}

	/**
	 * Restore a mod from the mod store, given a base mod to start from and the node of the
	 * desired mod.
	 *
	 * @param modNode the target mod:mod node to restore to
	 * @param baseMod the base mod from which to start the restoration chain
	 * @return the mod corresponding to the target node
	 * @throws TransformException if a mod in the chain fails to be restored
	 */
	private KeyMod restore(Node modNode, KeyMod baseMod) throws TransformException {
		assert baseMod.rule.id.equals(modNode.query().single("@rule").value());
		Mod mod = baseMod;
		ItemList history = engine.modStore().query().all(
				"for $mod in ($_1/ancestor-or-self::mod except $_2/ancestor-or-self::mod) " +
				"order by xs:integer($mod/@stage) return $mod", modNode, baseMod.node());
		for (Node historyNode : history.nodes()) {
			for (Node blockNode : historyNode.query().all("block").nodes()) {
				mod = mod.restoreChild(blocks.get(mod.stage+1), blockNode);
			}
		}
		return (KeyMod) mod;
	}
	
	/**
	 * Restore from the baseMod to the given modNode, verifying any blocks in the modNode that depend on
	 * modified documents.  If restoration or verification fails, withdraw the target mod.
	 *
	 * @param modNode the target mod node to restore to and verify
	 * @param baseMod the base mod to start from
	 * @param terminal if <code>true</code>, don't continue restoring once all blocks have been verified and return <code>null</code>
	 * @param modifiedDocsNames the relative names of documents that have been modified, to be matched against block document dependencies
	 * @return the restored and verified mod, or <code>null</code> if restoration or verification failed or terminated early
	 */
	private KeyMod restoreAndVerify(Node modNode, KeyMod baseMod, boolean terminal, Collection<String> modifiedDocsNames) {
		assert baseMod.rule.id.equals(modNode.query().single("@rule").value());
		LinkedList<Integer> stagesToVerify = listDependentStages(modNode, modifiedDocsNames);
		assert stagesToVerify.size() > 0;
		Mod mod = baseMod;
  		for (Node blockNode : modNode.query().all("block").nodes()) {
			if (terminal && stagesToVerify.size() == 0) {
				LOG.debug("verification ended early for " + mod);
				return null;	// no descendants, so end early
			}
			boolean shouldVerify = !stagesToVerify.isEmpty() && stagesToVerify.getFirst() == mod.stage+1;
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
				engine.withdrawMod(modNode.query().single("@xml:id").value());
				return null;
			}
		}
		return (KeyMod) mod;
	}

	private LinkedList<Integer> listDependentStages(Node modNode, Collection<String> modifiedDocsNames) {
		LinkedList<Integer> stagesToVerify = new LinkedList<Integer>();
		for (Item item : modNode.query().all("block[dependency/@doc=$_1]/@stage", modifiedDocsNames)) {
			stagesToVerify.add(item.intValue());
		}
		return stagesToVerify;
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
		
		ItemList modsToVerify = engine.modStore().query().unordered(
				"//mod[@rule=$_1][.//dependency/@doc=$_2]", id, modifiedDocsNames);
		if (modsToVerify.size() == 0) {
			LOG.debug("no mods to verify");
		} else {
			LOG.debug(MessageFormat.format(
					"verifying {1,choice,1#1 mod|1<{1,number,integer} mods}",
					new Object[] {modsToVerify.size()}
			));
			self.verifySubtree(self.bootstrapMod(), modsToVerify, modifiedDocsNames);
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

	private void verifySubtree(KeyMod mod, ItemList modsToVerify, Collection<String> modifiedDocsNames) throws TransformException {
		if (mod == null) return;
		for(Node childNode : mod.node().query().unordered("mod[descendant-or-self::mod/@xml:id=$_1/@xml:id]", modsToVerify).nodes()) {
			KeyMod childMod;
			if (modsToVerify.query().exists("self::mod[@xml:id=$_1/@xml:id]", childNode)) {
				boolean terminal = !modsToVerify.query().exists("self::mod[@xml:id=$_1/descendant::mod/@xml:id]", childNode);
				childMod = self.restoreAndVerify(childNode, mod, terminal, modifiedDocsNames);
			} else {
				try {
					childMod = self.restore(childNode, mod);
				} catch (TransformException e) {
					LOG.error("failed to restore " + childNode + " with no modified dependencies; clearing all mods for " + this, e);
					engine.withdrawRule(id);
					throw e;
				}
			}
			verifySubtree(childMod, modsToVerify, modifiedDocsNames);
		}
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
	
	@Deprecated @DatabaseTestCase.ConfigFile("test/conf.xml") @RunWith(JMock.class)
	public static class _ConstructorTest extends DatabaseTestCase {
		private Engine engine;
		private Node modStore;
		protected final Mockery mockery = new JUnit4Mockery() {{
			setImposteriser(ClassImposteriser.INSTANCE);
		}};
		
		@Before public void setUp() {
			modStore = db.getFolder("/").documents().load(Name.create("mods"), Source.xml(
					"<mods xmlns='http://ideanest.com/reef/ns/mod'>" + 
					"<mod xml:id='_r1.j-230.' rule='r1' stage='0'>" + 
					"	<block stage='0'>" + 
					"		<dependency doc='model/something.java'/>" + 
					"		<reference refid='j-230'/>" + 
					"	</block>" + 
					"	<mod xml:id='_r1.j-230.m-12.' rule='r1' stage='2'>" + 
					"		<block stage='1'>" + 
					"			<dependency doc='mappings.xml'/>" + 
					" 			<reference refid='m-12'/>" + 
					"		</block>" + 
					"		<block stage='2'>" + 
					"			<affected refid='_r1.j-230.m-12..1' checksum='2341234'/>" + 
					"		</block>" + 
					"	</mod>" + 
					"</mod>" + 
					"<mod xml:id='_r2.j-230.' rule='r2' stage='0'>" + 
					"	<block stage='0'>" + 
					"		<dependency doc='model/something.java'/>" + 
					"		<reference refid='j-230'/>" + 
					"	</block>" + 
					"</mod>" + 
					"</mods>")).root();
			modStore.namespaceBindings().put("", Transformer.MOD_NS);
			engine = mockery.mock(Engine.class);
			mockery.checking(new Expectations() {{
				allowing(engine).workspace(); will(returnValue(db.getFolder("/")));
				allowing(engine).globalScope(); will(returnValue(db.getFolder("/").query()));
				allowing(engine).modStore(); will(returnValue(modStore));
				allowing(engine).utilQuery(); will(returnValue(db.query()));
			}});
		}
		
		private Node makeRule(String attributes, String xml) {
			return db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<rule " + attributes + " xmlns='" + Transformer.RULES_NS + "'>" + xml + "</rule>")).root();
		}
		
		@Test public void sameDefsWithName() throws RuleBaseException {
			Rule rule = new Rule(
					makeRule("xml:id='r1' name='myrule'", "<create-doc>foobar</create-doc>"),
					makeRule("xml:id='r1' name='myrule'", "<create-doc>foobar</create-doc>"),
					engine,
					null);
			assertThat(rule.toString(), containsString("r1"));
			assertThat(rule.toString(), containsString("myrule"));
		}

		@Test public void sameDefsWithoutName() throws RuleBaseException {
			Rule rule = new Rule(
					makeRule("xml:id='r1'", "<create-doc>foobar</create-doc>"),
					makeRule("xml:id='r1'", "<create-doc>foobar</create-doc>"),
					engine,
					null);
			assertThat(rule.toString(), containsString("r1"));
		}
		
		@Test public void diffDefsStage0() throws RuleBaseException {
			mockery.checking(new Expectations() {{
				one(engine).withdrawMods(modStore.query().all("//mod[@rule='r1']"));
			}});
			new Rule(
					makeRule("xml:id='r1'", "<create-doc>foo</create-doc>"),
					makeRule("xml:id='r1'", "<create-doc>bar</create-doc>"),
					engine,
					null);
		}

		@Test public void diffDefsStage2() throws RuleBaseException {
			mockery.checking(new Expectations() {{
				one(engine).withdrawMods(modStore.query().all("/id('_r1.j-230.m-12.')"));
			}});
			new Rule(
					makeRule("xml:id='r1'", "<create-doc>foo</create-doc><with some='$x'>foo</with><insert>goo</insert>"),
					makeRule("xml:id='r1'", "<create-doc>foo</create-doc><with some='$x'>foo</with>"),
					engine,
					null);
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
				allowing(engine).globalScope(); will(returnValue(db.getFolder("/").query()));
				allowing(engine).utilQuery(); will(returnValue(db.query()));
			}});
			final Accumulator.Locator<XMLDocument> locator = mockery.mock(Accumulator.Locator.class);
			rule = new Rule(engine, locator);
		}
		
		protected void initEmptyModStore() {
			initLiteralModStore("");
		}
		
		protected void initLiteralModStore(String xml) {
			modStore = db.getFolder("/").documents().load(Name.create("mods"), Source.xml(
					"<mods xmlns='http://ideanest.com/reef/ns/mod'>" + xml + "</mods>")).root();
			modStore.namespaceBindings().put("", Transformer.MOD_NS);
			mockery.checking(new Expectations() {{
				allowing(rule.engine).modStore(); will(returnValue(modStore));
			}});
		}	

		protected Node modNodeAt(int stage) {
			return modStore.query().single("//mod[@stage=$_1]", stage).node();
		}
		
		protected Node blockNodeAt(int stage) {
			return modStore.query().single("//block[@stage=$_1]", stage).node();
		}
		
		protected Node modNode(String id) {
			return modStore.query().single("/id($_1)", "_"+id).node();
		}
		
		protected <T extends Mod> T mockMod(Class<T> clazz, final String id, final int stage) {
			final T mod = mockery.mock(clazz, "mod_" + id + "@" + stage);
			mockery.checking(new Expectations() {{
				allowing(mod).key();  will(returnValue(id));
				allowing(mod).node();  will(returnValue(stage == -1 ? modStore : modStore.query().optional("/id($_1)", id).node()));
			}});
			try {
				Field field = Mod.class.getDeclaredField("stage");
				field.setAccessible(true);
				field.setInt(mod, stage);
				field = Mod.class.getDeclaredField("rule");
				field.setAccessible(true);
				field.set(mod, rule);
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
				final Counter counter = new Counter(fieldName + " = {1,number,integer}");
				Field field = Engine.class.getDeclaredField(fieldName);
				field.setAccessible(true);
				field.set(rule.engine, counter);
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
					"<blocks xmlns='" + Transformer.RULES_NS + "'>" + xml + "</blocks>")).root().query().single("*").node();
		}
		
		@Test public void defineBlockWorks() throws RuleBaseException {
			Block block = rule.defineBlock(makeBlock("<for one='$x'>//method</for>"));
			assertTrue(block instanceof LinearBlock);
		}
		
		@Test(expected = RuleBaseException.class)
		public void defineBlockFailsOnUnknownBlockType() throws RuleBaseException {
			rule.defineBlock(makeBlock("<foobar/>"));
		}
		
		@Test(expected = RuleBaseException.class)
		public void defineBlockFailsOnIllegalAttribute() throws RuleBaseException {
			rule.defineBlock(makeBlock("<for into='$x'/>"));
		}
		
		@Test public void defineBlockAcceptsNamespacedAttribute() throws RuleBaseException {
			rule.defineBlock(makeBlock("<for xmlns:x='foo' x:into='$x' each='$y'/>"));
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
			doc.namespaceBindings().put("", Transformer.RECORD_NS);
			assertEquals(BLOCK_CLASSES.length, doc.query().all("//block-type").size());
			for (BlockType blockType : BLOCK_TYPE_DICTIONARY.values()) {
				assertTrue(doc.query().exists("//block-type[@class=$_1][@version=$_2]", blockType.getClass().getName(), blockType.version()));
			}
		}
		
		@Test public void verifyBlockTypeVersions() {
			XMLDocument doc = db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<root xmlns='" + Transformer.RECORD_NS + "'>" +
					"  <block-type class='com.ideanest.dscribe.mixt.blocks.For' version='" + new For().version() + "'/>" +
					"  <block-type class='com.ideanest.dscribe.mixt.blocks.With' version='" + new With().version() + "foo'/>" +
					"</root>"));
			Collection<QName> badBlockNames = Rule.verifyBlockTypeVersions(doc);
			NamespaceMap ns = new NamespaceMap("", Transformer.RULES_NS);
			assertFalse("good version", badBlockNames.contains(QName.parse("for", ns)));
			assertTrue("bad version", badBlockNames.contains(QName.parse("with", ns)));
			assertTrue("missing record", badBlockNames.contains(QName.parse("insert", ns)));
		}
	}

	@Deprecated
	public static class _ParseBlocksTest extends _RuleTest {
		private Node makeRule(String xml) {
			return db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<rule xmlns='" + Transformer.RULES_NS + "'>" + xml + "</rule>")).root();
		}
		
		@Test(expected = RuleBaseException.class)
		public void badBlock() throws RuleBaseException {
			rule.parseBlocks(makeRule("<foo/>"), db.query().optional("inexistent").node());
		}
		
		@Test public void noPrevDef1() throws RuleBaseException {
			int firstDiff = rule.parseBlocks(
					makeRule("<for each='$x'> //method </for>"),
					db.query().optional("inexistent").node());
			assertEquals(0, firstDiff);
			assertEquals(1, rule.blocks.size());
		}
		
		@Test public void noPrevDef2() throws RuleBaseException {
			int firstDiff = rule.parseBlocks(
					makeRule("<for each='$x'> //method </for><insert> <operation/> </insert>"),
					db.query().optional("inexistent").node());
			assertEquals(0, firstDiff);
			assertEquals(2, rule.blocks.size());
		}
		
		@Test public void prevUnknownBlock() throws RuleBaseException {
			int firstDiff = rule.parseBlocks(
					makeRule("<for each='$x'> //method </for>"),
					makeRule("<foo each='$x'> //method </foo>"));
			assertEquals(0, firstDiff);
			assertEquals(1, rule.blocks.size());
		}
		
		@Test public void samePrevDef1() throws RuleBaseException {
			int firstDiff = rule.parseBlocks(
					makeRule("<for each='$x'> //method </for>"),
					makeRule("<for each='$x'> //method </for>"));
			assertEquals(Integer.MAX_VALUE, firstDiff);
			assertEquals(1, rule.blocks.size());
		}

		@Test public void samePrevDef2() throws RuleBaseException {
			int firstDiff = rule.parseBlocks(
					makeRule("<for each='$x'> //method </for><insert> <operation/> </insert>"),
					makeRule("<for each='$x'> //method </for><insert> <operation/> </insert>"));
			assertEquals(Integer.MAX_VALUE, firstDiff);
			assertEquals(2, rule.blocks.size());
		}
		
		@Test public void lastBlockDiff() throws RuleBaseException {
			int firstDiff = rule.parseBlocks(
					makeRule("<for each='$x'> //method </for><insert> <operation/> </insert>"),
					makeRule("<for each='$x'> //method </for><insert> <op/> </insert>"));
			assertEquals(1, firstDiff);
			assertEquals(2, rule.blocks.size());
		}
		
		@Test public void middleBlockDiff() throws RuleBaseException {
			int firstDiff = rule.parseBlocks(
					makeRule("<for each='$x'> //method </for><with some='$y'> $x/foo </with><insert> <operation/> </insert>"),
					makeRule("<for each='$x'> //method </for><with any='$y'> $x/foo </with><insert> <operation/> </insert>"));
			assertEquals(1, firstDiff);
			assertEquals(3, rule.blocks.size());
		}
		
		@Test public void twoBlocksDiff() throws RuleBaseException {
			int firstDiff = rule.parseBlocks(
					makeRule("<for each='$x'> //method </for><with some='$y'> $x/foo </with><insert> <operation/> </insert>"),
					makeRule("<for each='$x'> //method </for><with any='$y'> $x/foo </with><insert> <op/> </insert>"));
			assertEquals(1, firstDiff);
			assertEquals(3, rule.blocks.size());
		}
		
		@Test public void prevDefLonger() throws RuleBaseException {
			int firstDiff = rule.parseBlocks(
					makeRule("<for each='$x'> //method </for><with any='$y'> $x/foo </with>"),
					makeRule("<for each='$x'> //method </for><with any='$y'> $x/foo </with><insert> <operation/> </insert>"));
			assertEquals(2, firstDiff);
			assertEquals(2, rule.blocks.size());
		}

		@Test public void prevDefShorter() throws RuleBaseException {
			int firstDiff = rule.parseBlocks(
					makeRule("<for each='$x'> //method </for><with some='$y'> $x/foo </with><insert> <operation/> </insert>"),
					makeRule("<for each='$x'> //method </for><with some='$y'> $x/foo </with>"));
			assertEquals(2, firstDiff);
			assertEquals(3, rule.blocks.size());
		}
		
	}

	@Deprecated
	public static class _ModTest extends _RuleTest {
		private List<Block> blocks;
		
		@Before public void setUp() {
			initLiteralModStore(
					"<mods xmlns='http://ideanest.com/reef/ns/mod'>" + 
					"<mod xml:id='_r1.0.' rule='r1' stage='0'>" + 
					"	<block stage='0'>" + 
					"		<dependency doc='model/something.java'/>" + 
					"		<reference refid='j-230'/>" + 
					"	</block>" + 
					"	<mod xml:id='_r1.2.' rule='r1' stage='2'>" + 
					"		<block stage='1'>" + 
					"			<dependency doc='mappings.xml'/>" + 
					"			<reference refid='m-12'/>" + 
					"		</block>" + 
					"		<block stage='2'>" + 
					"			<affected refid='_r1.j-230.m-12..1' checksum='2341234'/>" + 
					"		</block>" + 
					"		<mod xml:id='_r1.3.' rule='r1' stage='3'>" + 
					"			<block stage='3'>" + 
					"				<dependency doc='foo.xml'/>" +
					"				<reference refid='m-14'/>" + 
					"			</block>" + 
					"		</mod>" + 
					"	</mod>" + 
					"</mod>" + 
					"</mods>");
			
			blocks = Arrays.asList(new Block[] {
					mockery.mock(KeyBlock.class, "b0"), mockery.mock(LinearBlock.class, "b1"),
					mockery.mock(KeyBlock.class, "b2"), mockery.mock(KeyBlock.class, "b3")
			});
			
			rule.blocks.addAll(blocks);
		}
		
		private List<Mod> mockModRestoreSequence(int firstStage, final int lastStage) throws TransformException {
			final List<Mod> mods = new ArrayList<Mod>(lastStage-firstStage+1);
			final Sequence s = mockery.sequence("stages");
			for (int i = 0; i < firstStage; i++) mods.add(null);
			for (int i = firstStage; i <= lastStage; i++) {
				mods.add(mockMod(
						modStore.query().optional("//mod[@stage=$_1]", i).extant() ? KeyMod.class : Mod.class,
						"_r1." + i +".",
						i));
			}
			for (int i = firstStage; i < lastStage; i++) {
				final int index = i;
				mockery.checking(new Expectations() {{
					one(mods.get(index)).restoreChild(	
							with(same(blocks.get(index+1))), with(equal(blockNodeAt(index+1))));
					inSequence(s);  will(returnValue(mods.get(index+1)));
				}});
			}
			return mods;
		}
		
		@Test public void restoreOneStage() throws TransformException {
			List<Mod> mods = mockModRestoreSequence(2, 3);
			assertSame(mods.get(3), rule.restore(modNodeAt(3), (KeyMod) mods.get(2)));
		}
		
		@Test public void restoreTwoStage() throws TransformException {
			List<Mod> mods = mockModRestoreSequence(0, 3);
			assertSame(mods.get(3), rule.restore(modNodeAt(3), (KeyMod) mods.get(0)));
		}
		
		@Test public void restoreFromBootstrap() throws TransformException {
			final List<Mod> mods = mockModRestoreSequence(0, 3);
			final KeyMod bootstrapMod = mockMod(KeyMod.class, "_r1.", -1);
			mockery.checking(new Expectations() {{
				one(bootstrapMod).restoreChild(with(same(blocks.get(0))), with(equal(blockNodeAt(0))));
				will(returnValue(mods.get(0)));  // should be in sequence, but this is the only way to get mods[0] anyway
			}});
			assertSame(mods.get(3), rule.restore(modNodeAt(3), bootstrapMod));
		}
		
		@Test public void listDependentStages1() {
			assertEquals(
					Collections.singletonList(1),
					rule.listDependentStages(modNodeAt(2), Collections.singleton("mappings.xml")));
		}

		@Test public void listDependentStages2() {
			assertEquals(
					Collections.emptyList(),
					rule.listDependentStages(modNodeAt(2), Collections.<String>emptyList()));
		}

		@Test public void listDependentStages3() {
			assertEquals(
					Collections.emptyList(),
					rule.listDependentStages(modNodeAt(3), Collections.singleton("mappings.xml")));
		}
		
		@Test public void verifyOneNodeSuccessfully() throws TransformException {
			final List<Mod> mods = mockModRestoreSequence(2, 3);
			mockery.checking(new Expectations() {{
				one(mods.get(3)).verify();
			}});
			Counter counter = injectEngineCounter("numBlocksVerified");
			assertSame(mods.get(3),
					rule.restoreAndVerify(modNodeAt(3), (KeyMod) mods.get(2), false, Collections.singleton("foo.xml")));
			assertEquals(1, counter.value());
		}

		@Test public void verifyOneNodeFailure() throws TransformException {
			final List<Mod> mods = mockModRestoreSequence(2, 3);
			mockery.checking(new Expectations() {{
				one(mods.get(3)).verify();  will(throwException(new TransformException()));
				one(rule.engine).withdrawMod("_r1.3.");
			}});
			Counter counter = injectEngineCounter("numBlocksVerified");
			assertNull(rule.restoreAndVerify(modNodeAt(3), (KeyMod) mods.get(2), false, Collections.singleton("foo.xml")));
			assertEquals(0, counter.value());
		}

		@Test public void verifySkipNodeSuccessfully() throws TransformException {
			final List<Mod> mods = mockModRestoreSequence(0, 2);
			mockery.checking(new Expectations() {{
				one(mods.get(1)).verify();
			}});
			Counter counter = injectEngineCounter("numBlocksVerified");
			assertSame(mods.get(2),
					rule.restoreAndVerify(modNodeAt(2), (KeyMod) mods.get(0), false, Collections.singleton("mappings.xml")));
			assertEquals(1, counter.value());
		}

		@Test public void verifySkipNodeTerminal() throws TransformException {
			final List<Mod> mods = mockModRestoreSequence(0, 1);
			mockery.checking(new Expectations() {{
				one(mods.get(1)).verify();
			}});
			Counter counter = injectEngineCounter("numBlocksVerified");
			assertNull(rule.restoreAndVerify(modNodeAt(2), (KeyMod) mods.get(0), true, Collections.singleton("mappings.xml")));
			assertEquals(1, counter.value());
		}

		@Test public void verifyTwoNodeFailure1() throws TransformException {
			final List<Mod> mods = mockModRestoreSequence(0, 1);
			mockery.checking(new Expectations() {{
				one(mods.get(1)).verify();  will(throwException(new TransformException()));
				one(rule.engine).withdrawMod("_r1.2.");
			}});
			Counter counter = injectEngineCounter("numBlocksVerified");
			assertNull(rule.restoreAndVerify(modNodeAt(2), (KeyMod) mods.get(0), false, Collections.singleton("mappings.xml")));
			assertEquals(0, counter.value());
		}
		
		@Test public void verifyTwoNodeFailure2() throws TransformException {
			final List<Mod> mods = mockModRestoreSequence(0, 1);
			mockery.checking(new Expectations() {{
				one(mods.get(1)).verify();
				one(mods.get(1)).restoreChild(blocks.get(2), blockNodeAt(2));  will(throwException(new TransformException()));
				one(rule.engine).withdrawMod("_r1.2.");
			}});
			Counter counter = injectEngineCounter("numBlocksVerified");
			assertNull(rule.restoreAndVerify(modNodeAt(2), (KeyMod) mods.get(0), false, Collections.singleton("mappings.xml")));
			assertEquals(1, counter.value());
		}
		
	}

	@Deprecated
	public static class _VerifySubtreeTest extends _RuleTest {
		private Collection<String> modifiedDocsNames = Collections.<String>emptyList();
		private Map<String, KeyMod> mods = new HashMap<String, KeyMod>();
		
		@Before public void setUp() {
			initEmptyModStore();
			rule.self = mockery.mock(Rule.Shim.class);
		}
		
		private void createMods(String... ids) {
			for (String id : ids) {
				createMod(id);
			}
		}
		
		private void createMod(final String id) {
			final KeyMod mockMod = mockery.mock(KeyMod.class, "mod_" + id);
			mods.put(id, mockMod);
			int k = id.lastIndexOf('.');
			Node parent = k == -1 ? modStore : modStore.query().presub().single("/id('_$1')", id.substring(0, k)).node();
			final Node modNode = parent.append().elem("mod").attr("xml:id", "_"+id).attr("rule", "r1").attr("stage", id.split("\\.").length-1).end("mod").commit();
			mockery.checking(new Expectations() {{
				allowing(mockMod).key();  will(returnValue("_"+id));
				allowing(mockMod).node();  will(returnValue(modNode));
			}});
		}
		
		private void expectRestoreAndVerify(final String nodeId, final String baseModId, final boolean terminal) {
			expectRestoreAndVerify(nodeId, mods.get(baseModId), terminal);
		}
		
		private void expectRestoreAndVerify(final String nodeId, final KeyMod baseMod, final boolean terminal) {
			mockery.checking(new Expectations() {{
				one(rule.self).restoreAndVerify(
						with(equal(modNode(nodeId))),
						with(same(baseMod)),
						with(equal(terminal)),
						with(same(modifiedDocsNames))
				);
				will(returnValue(mods.get(nodeId)));
			}});
		}
		
		private void expectRestore(final String id) throws TransformException {
			expectRestore(id, mods.get(id.substring(0, id.lastIndexOf('.'))));
		}
		
		private void expectRestore(final String id, final KeyMod baseMod) throws TransformException {
			mockery.checking(new Expectations() {{
				one(rule.self).restore(with(equal(modNode(id))), with(same(baseMod)));
				will(returnValue(mods.get(id)));
			}});
		}
		
		private ItemList modNodes(String... ids) {
			List<String> prefixedIds = new ArrayList<String>(ids.length);
			for (String id : ids) prefixedIds.add("_"+id);
			return modStore.query().all("/id($_1)", prefixedIds);
		}
		
		@Test public void terminal() throws TransformException {
			createMods("1", "1.1");
			expectRestoreAndVerify("1.1", "1", true);
			rule.verifySubtree(mods.get("1"), modNodes("1.1"), modifiedDocsNames);
		}
		
		@Test public void linearChain() throws TransformException {
			createMods("1", "1.1", "1.1.1");
			expectRestoreAndVerify("1.1", "1", false);
			expectRestoreAndVerify("1.1.1", "1.1", true);
			rule.verifySubtree(mods.get("1"), modNodes("1.1", "1.1.1"), modifiedDocsNames);
		}
		
		@Test public void deepLinearChain() throws TransformException {
			createMods("1", "1.1", "1.1.1", "1.1.1.1");
			expectRestoreAndVerify("1.1", "1", false);
			expectRestoreAndVerify("1.1.1", "1.1", false);
			expectRestoreAndVerify("1.1.1.1", "1.1.1", true);
			rule.verifySubtree(mods.get("1"), modNodes("1.1", "1.1.1", "1.1.1.1"), modifiedDocsNames);
		}
		
		@Test public void split() throws TransformException {
			createMods("1", "1.1", "1.2");
			expectRestoreAndVerify("1.1", "1", true);
			expectRestoreAndVerify("1.2", "1", true);
			rule.verifySubtree(mods.get("1"), modNodes("1.1", "1.2"), modifiedDocsNames);
		}
		
		@Test public void multipleSplit() throws TransformException {
			createMods("1", "1.1", "1.1.1", "1.1.2", "1.2", "1.2.1");
			expectRestoreAndVerify("1.1", "1", false);
			expectRestoreAndVerify("1.1.1", "1.1", true);
			expectRestoreAndVerify("1.1.2", "1.1", true);
			expectRestoreAndVerify("1.2", "1", false);
			expectRestoreAndVerify("1.2.1", "1.2", true);
			rule.verifySubtree(mods.get("1"), modNodes("1.1", "1.1.1", "1.1.2", "1.2", "1.2.1"), modifiedDocsNames);
		}
		
		@Test public void skipOver() throws TransformException {
			createMods("1", "1.1", "1.1.1");
			expectRestore("1.1");
			expectRestoreAndVerify("1.1.1", "1.1", true);
			rule.verifySubtree(mods.get("1"), modNodes("1.1.1"), modifiedDocsNames);
		}
		
		@Test public void commonUnchangedAncestor() throws TransformException {
			createMods("1", "1.1", "1.1.1", "1.1.2");
			expectRestore("1.1");
			expectRestoreAndVerify("1.1.1", "1.1", true);
			expectRestoreAndVerify("1.1.2", "1.1", true);
			rule.verifySubtree(mods.get("1"), modNodes("1.1.1", "1.1.2"), modifiedDocsNames);
		}
		
		@Test public void ignoresUnchangedBranches() throws TransformException {
			createMods("1", "1.1", "1.1.1", "1.2", "1.2.1");
			expectRestore("1.1");
			expectRestoreAndVerify("1.1.1", "1.1", true);
			rule.verifySubtree(mods.get("1"), modNodes("1.1.1"), modifiedDocsNames);
		}
		
		@Test public void verifiesFromBootstrap() throws TransformException {
			KeyMod bootstrap = mockMod(KeyMod.class, "_r1.", -1);
			createMods("1", "1.1");
			expectRestoreAndVerify("1", bootstrap, false);
			expectRestoreAndVerify("1.1", "1", true);
			rule.verifySubtree(bootstrap, modNodes("1", "1.1"), modifiedDocsNames);
		}

		@Test public void restoresFromBootstrap() throws TransformException {
			KeyMod bootstrap = mockMod(KeyMod.class, "_r1.", -1);
			createMods("1", "1.1");
			expectRestore("1", bootstrap);
			expectRestoreAndVerify("1.1", "1", true);
			rule.verifySubtree(bootstrap, modNodes("1.1"), modifiedDocsNames);
		}
		
		@Test(expected = TransformException.class)
		public void restoreErrorWithdrawsRule() throws TransformException {
			createMods("1", "1.1", "1.1.1");
			mockery.checking(new Expectations() {{
				one(rule.self).restore(with(equal(modNode("1.1"))), with(same(mods.get("1"))));
				will(throwException(new TransformException()));
				one(rule.engine).withdrawRule("r1");
			}});
			rule.verifySubtree(mods.get("1"), modNodes("1.1.1"), modifiedDocsNames);
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
		
		private class QueryServiceDocsMatcher extends BaseMatcher<QueryService> {
			private final Set<String> expectedNames;
			public QueryServiceDocsMatcher(Set<String> expectedNames) {
				this.expectedNames = expectedNames;
			}
			@SuppressWarnings("unchecked")
			public boolean matches(Object item) {
				try {
					queryServicePrepareContext.invoke(item, (Object) null);
					Set<String> actualNames = new TreeSet<String>();
					for (Iterator<DocumentImpl> it = ((DocumentSet) queryServiceDocs.get(item)).getDocumentIterator(); it.hasNext(); ) {
						String path = it.next().getURI().toString();
						if (path.startsWith("/db")) path = path.substring(3);
						actualNames.add(path);
					}
					return expectedNames.equals(actualNames);
				} catch (IllegalArgumentException e) {
					throw new RuntimeException(e);
				} catch (IllegalAccessException e) {
					throw new RuntimeException(e);
				} catch (InvocationTargetException e) {
					throw new RuntimeException(e);
				}
			}
			public void describeTo(Description description) {
				description.appendText("QueryService docs matching ").appendValue(expectedNames);
			}
		}
		
		private KeyMod bootstrapMod;
		private Counter numBlocksResolved, numModsCompleted;
		private Map<String, Mod> mods = new HashMap<String, Mod>();
		private Matcher<QueryService> qsDocsMatcher;
		
		@Before public void setUp() {
			// set up a confuser mod for another rule
			initLiteralModStore("<mod xml:id='_r2.2..' rule='r2' stage='0'/>");

			rule.firstDifferentStage = Integer.MAX_VALUE;
			rule.self = mockery.mock(Rule.Shim.class);
			bootstrapMod = mockMod(KeyMod.class, "_r1.", -1);
			mockery.checking(new Expectations() {{
				allowing(rule.self).bootstrapMod();  will(returnValue(bootstrapMod));
			}});
			numBlocksResolved = injectEngineCounter("numBlocksResolved");
			numModsCompleted = injectEngineCounter("numModsCompleted");
		}
		
		private void prepScenario(final boolean verifySuccessful, int numBlocks) throws TransformException {
			final Set<XMLDocument> modifiedDocs = new HashSet<XMLDocument>();
			// add some documents to list in modifiedDocs
			modifiedDocs.add(db.getFolder("/").documents().load(Name.create("somedoc"), Source.xml("<data/>")));
			modifiedDocs.add(db.createFolder("/foo").documents().load(Name.create("otherdoc"), Source.xml("<data/>")));
			
			mockery.checking(new Expectations() {{
				one(rule.modifiedDocsLocator).catchUp();  will(returnValue(modifiedDocs));
				one(rule.self).verifyMods(modifiedDocs);
				if (!verifySuccessful) will(throwException(new TransformException()));
			}});
			if (verifySuccessful) {
				Set<String> docNames = new TreeSet<String>();
				for (Document doc : modifiedDocs) docNames.add(doc.path());
				qsDocsMatcher = new QueryServiceDocsMatcher(docNames);
			} else {
				qsDocsMatcher = Expectations.aNull(QueryService.class);
			}
			
			for (int i=0; i<numBlocks; i++) {
				Block block = mockery.mock(Block.class, "block_" + i);
				rule.blocks.add(block);
			}			
		}
		
		private void processRuleExpectingCounts(int expectedNumBlocksResolved, int expectedNumModsCompleted) throws TransformException {
			rule.process(false);
			assertEquals("num blocks resolved", expectedNumBlocksResolved, numBlocksResolved.value());
			assertEquals("num blocks completed", expectedNumModsCompleted, numModsCompleted.value());
		}
		
		@Override @SuppressWarnings("unchecked")
		protected <T extends Mod> T mockMod(Class<T> clazz, String id, int stage) {
			T mod = (T) mods.get(id);
			if (mod == null) {
				mod = super.mockMod(clazz, id, stage);
				mods.put(id, mod);
			}
			return mod;
		}
		
		private enum Kind {RESOLVED, STORED, RESTORED}
		
		private void prepMod(final String id, final int stage, Kind kind, final String... resolvedIds) throws TransformException {
			final Mod mod = mockMod(kind == Kind.RESOLVED ? Mod.class : KeyMod.class, id, stage);
			if (kind == Kind.STORED || kind == Kind.RESTORED) {
				final Node modNode = modStore.append().elem("mod").attr("xml:id", id).attr("rule", "r1").attr("stage", stage).end("mod").commit();
				if (kind == Kind.RESTORED) {
					mockery.checking(new Expectations() {{
						one(rule.self).restore(with(equal(modNode)), with(same(bootstrapMod)));
						will(returnValue(mod));
					}});
				}
			}
			if (kind == Kind.RESOLVED || kind == Kind.RESTORED) {
				mockery.checking(new Expectations() {{
					Collection<Mod> resolvedMods = new ArrayList<Mod>(resolvedIds.length);
					for (String resolvedId : resolvedIds) resolvedMods.add(mockMod(Mod.class,resolvedId, stage+1));
					one(mod).resolveChildren(
							with(same(rule.blocks.get(stage+1))),
							with(equal(stage+2 == rule.blocks.size())),
							stage+1 >= rule.firstDifferentStage ? with(aNull(QueryService.class)) : with(qsDocsMatcher));
					will(returnValue(Collections.unmodifiableCollection(resolvedMods)));
				}});
			}
		}
		
		@Test public void emptyRunNothingResolved() throws TransformException {
			prepScenario(true, 1);
			prepMod("_r1.", -1, Kind.RESOLVED);
			processRuleExpectingCounts(1, 0);
		}
	
		@Test public void emptyRunSomethingResolved() throws TransformException {
			prepScenario(true, 1);
			prepMod("_r1.", -1, Kind.RESOLVED, "_r1.1");
			processRuleExpectingCounts(1, 1);
		}

		@Test public void verifyFailed() throws TransformException {
			prepScenario(false, 1);
			prepMod("_r1.", -1, Kind.RESOLVED, "_r1.1");
			processRuleExpectingCounts(1, 1);
		}

		@Test public void twoStageRun() throws TransformException {
			prepScenario(true, 2);
			prepMod("_r1.", -1, Kind.RESOLVED, "_r1.1");
			prepMod("_r1.1", 0, Kind.RESOLVED, "_r1.1.1", "_r1.1.2");
			processRuleExpectingCounts(2, 2);
		}
		
		@Test public void restoreNoAdvance() throws TransformException {
			prepScenario(true, 2);
			prepMod("_r1.", -1, Kind.RESOLVED);
			prepMod("_r1.1", 0, Kind.RESTORED);
			processRuleExpectingCounts(2, 0);
		}

		@Test public void restoreAndAdvance() throws TransformException {
			prepScenario(true, 2);
			prepMod("_r1.", -1, Kind.RESOLVED);
			prepMod("_r1.1", 0, Kind.RESTORED, "_r1.1.1");
			processRuleExpectingCounts(2, 1);
		}

		@Test public void restoreAndAdvanceComplex() throws TransformException {
			prepScenario(true, 3);
			prepMod("_r1.", -1, Kind.RESOLVED, "_r1.2");
			prepMod("_r1.1", 0, Kind.RESTORED, "_r1.1.1");
			prepMod("_r1.2", 0, Kind.RESOLVED, "_r1.2.1");
			prepMod("_r1.1.1", 1, Kind.RESOLVED, "_r1.1.1.1", "_r1.1.1.2");
			prepMod("_r1.2.1", 1, Kind.RESOLVED, "_r1.2.1.1");
			prepMod("_r1.2.2", 1, Kind.RESTORED, "_r1.2.2.1");
			processRuleExpectingCounts(6, 4);
		}
		
		@Test public void restoreResolveDuplicate() throws TransformException {
			prepScenario(true, 2);
			prepMod("_r1.", -1, Kind.RESOLVED, "_r1.1");
			prepMod("_r1.1", 0, Kind.RESOLVED, "_r1.1.1");
			prepMod("_r1.1", 0, Kind.STORED);
			processRuleExpectingCounts(2, 1);
		}

	}
}