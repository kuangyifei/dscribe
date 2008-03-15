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
	private final Mod rootMod;

	private Shim self;  // for testing only

	Rule(Node def, Node prevDef, Engine engine, Accumulator.Locator<XMLDocument> modifiedDocsLocator) throws RuleBaseException {
		this.engine = engine;
		this.modifiedDocsLocator = modifiedDocsLocator;
		initDefaultShim();
		
		validateAttributes(def, Collections.singleton("name"));
		
		try {
			this.id = def.query().single("@xml:id").value();
		} catch (DatabaseException e) {
			throw new RuleBaseException("a rule " + def.name() + " has no xml:id");
		}

		toString = buildToString(def.query().optional("@name").value());
		LOG.debug("reading in " + this);
		
		rootMod = Mod.bootstrap(this);
		rootMod.node().namespaceBindings().put("", Engine.MOD_NS);
		firstDifferentStage = parseBlocks(def, prevDef);
		
		if (firstDifferentStage < Integer.MAX_VALUE) {
			LOG.info(this + " has changed starting at stage " + firstDifferentStage + ", withdrawing affected mods");
			engine.withdrawMods(rootMod.node().query().unordered(".//mod[xs:integer(@stage) >= $_1]", firstDifferentStage));
		}
	}

	// for testing only
	private Rule(Engine engine, Accumulator.Locator<XMLDocument> modifiedDocsLocator) {
		this.engine = engine;
		this.modifiedDocsLocator = modifiedDocsLocator;
		this.id = "r1";
		toString = "rule[r1]";
		this.rootMod = null;
		initDefaultShim();
	}

	private interface Shim {
		Mod rootMod();
		Mod restoreMod(Node modNode) throws TransformException;
		void restoreModsAtStage(Collection<Mod> mods, int stage) throws TransformException;
		Collection<Mod> resolveModsAtStage(Collection<Mod> mods, int stage, QueryService touchedScope) throws TransformException;
		void verifyMods(Set<XMLDocument> modifiedDocs) throws TransformException;
		void verifyModTree(ItemList modsToVerify, Collection<String> modifiedDocsNames) throws TransformException;
	}
	
	private void initDefaultShim() {
		this.self = new Shim() {
			public Mod rootMod() {
				return Rule.this.bootstrapMod();
			}
			public Mod restoreMod(Node modNode) throws TransformException {
				return Rule.this.restoreMod(modNode);
			}
			public void restoreModsAtStage(Collection<Mod> mods, int stage) throws TransformException {
				Rule.this.restoreModsAtStage(mods, stage);
			}
			public Collection<Mod> resolveModsAtStage(Collection<Mod> mods, int stage, QueryService touchedScope) throws TransformException {
				return Rule.this.resolveModsAtStage(mods, stage, touchedScope);
			}
			public void verifyMods(Set<XMLDocument> modifiedDocs) throws TransformException {
				Rule.this.verifyMods(modifiedDocs);
			}
			public void verifyModTree(ItemList modsToVerify, Collection<String> modifiedDocsNames) throws TransformException {
				Rule.this.verifyModTree(modsToVerify, modifiedDocsNames);
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
			Mod mod = self.rootMod();
			Iterator<Node> prevBlocksIterator = prevDef.query().all("* except alias").nodes().iterator();
			for (Node blockDef : def.query().all("* except alias").nodes()) {
				
				if (firstDiff == Integer.MAX_VALUE) {
					Node prevBlockDef = prevBlocksIterator.hasNext() ? prevBlocksIterator.next() : null;
					if (prevBlockDef == null || !equalBlocks(blockDef, prevBlockDef)) {
						if (LOG.isDebugEnabled()) {
							LOG.debug(
									this + " blocks differ at stage " + blocks.size() + ";\n" +
									"--- current block:\n" + blockDef + " with ns prefixes " + blockDef.query().all("in-scope-prefixes(.)") + "\n" +
									"--- previous block:\n" + prevBlockDef + (prevBlockDef == null ? "" : " with ns prefixes " + prevBlockDef.query().all("in-scope-prefixes(.)")));
						}
						firstDiff = blocks.size();
					}
				}
				
				Block block = defineBlock(blockDef);
				mod = mod.deriveChild(block, block instanceof KeyBlock ? "fakeKey" : null);		// a bogus key, just to make the mod believable
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
	
	<D extends Document> void addTouched(Collection<D> docs) {
		touched.addAll(docs);
	}
	
	@SuppressWarnings("unchecked")  // each block is only fed segs that it produced; see SortController.sort(Node)
	void sortBlock(int stage, Collection<Seg> segs, SortController.OrderGraph graph) throws TransformException {
		SortingBlock block;
		try {
			 block = (SortingBlock) blocks.get(stage);
		} catch (ClassCastException e) {
			throw new TransformException("not a sorting block: " + this + ", block " + stage);
		}
		block.sort(segs, graph);
	}
	
	void process(boolean doGlobalProcessing) throws TransformException {
		LOG.debug("processing " + this);
		if (blocks.size() == 0) return;
		
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
		
		// The stage counter is the child stage that we'll be resolving at each iteration, based on stage-1.
		for (int stage = 0; stage < blocks.size(); stage++) {
			LOG.debug(this + " processing stage " + stage);
			self.restoreModsAtStage(mods, stage - 1);
			mods = self.resolveModsAtStage(mods, stage, touchedScope);
		}
		
		engine.stats.numModsCompleted.increment(mods.size());
		firstDifferentStage = Integer.MAX_VALUE;
	}

	private void restoreModsAtStage(Collection<Mod> mods, int stage) throws TransformException {
		LOG.debug(this + " restoring mods in progress at stage " + stage);
		boolean mustRestore = blocks.get(stage + 1) instanceof KeyBlock;
		if (stage == -1) {
			if (mustRestore || !self.rootMod().node().query().exists("mod")) mods.add(self.rootMod());
			return;
		}
		
		Set<String> modKeys = new HashSet<String>();
		for (Mod mod : mods) modKeys.add(mod.key());
		for (Node node : self.rootMod().node().query().unordered(
				".//mod[xs:integer(@stage)=$_1]" + (mustRestore ? "" : "[not(mod)]"), stage).nodes()) {
			if (modKeys.contains(node.query().single("ancestor-or-self::*[@xml:id][last()]/@xml:id").value())) continue;
			Mod mod = self.restoreMod(node);
			mods.add(mod);
			LOG.debug("restored " + mod);
		}
	}

	Mod restoreMod(Node node) throws TransformException {
		Mod mod = self.rootMod();
		for (Node historyNode : engine.modStore().query().all(
				"for $mod in ($_1/ancestor-or-self::* except (/modstore, $_2)) " +
				"order by xs:integer($mod/@stage) return $mod", node, mod.node()).nodes()) {
			mod = mod.restoreChild(blocks.get(mod.stage+1), historyNode);
		}
		return mod;
	}

	private Collection<Mod> resolveModsAtStage(Collection<Mod> mods, int stage, QueryService touchedScope) throws TransformException {
		assert stage >= 0;
		LOG.debug(this + " resolving mods at stage " + stage);
		final Block block = blocks.get(stage);
		boolean lastBlock = stage == blocks.size()-1;
		Collection<Mod> nextStageMods = new ArrayList<Mod>();
		for (Mod mod : mods) {
			nextStageMods.addAll(mod.resolveChildren(block, lastBlock, (stage >= firstDifferentStage) ? null : touchedScope));
		}
		LOG.debug(this + " mods resolved: " + nextStageMods);
		engine.stats.numBlocksResolved.increment(nextStageMods.size());
		return nextStageMods;
	}

	private Mod bootstrapMod() {
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
		
		ItemList modsToVerify = rootMod.node().query().unordered(
				".//mod[dependency/@doc=$_1]", modifiedDocsNames);
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
			ItemList childNodes = mod.node().query().unordered("mod[exists(descendant-or-self::mod intersect $_1)]", modsToVerify);
			for (Node childNode : engine.utilQuery().unordered("$_1 intersect $_2", childNodes, modsToVerify).nodes()) {
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
			for (Node childNode : engine.utilQuery().unordered("$_1 except $_2", childNodes, modsToVerify).nodes()) {
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
				allowing(engine).globalScope(); will(returnValue(db.getFolder("/").query()));
				allowing(engine).modStore(); will(returnValue(modStore));
				allowing(engine).utilQuery(); will(returnValue(db.query()));
			}});
		}
		
		private Node makeRule(String attributes, String xml) {
			return db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<rule " + attributes + " xmlns='" + Engine.RULES_NS + "'>" + xml + "</rule>")).root();
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
				one(engine).withdrawMods(modStore.query().all("//mods[@rule='r1']//mod"));
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
		}	

		protected Node modNodeAt(int stage) {
			return modStore.query().single("//mod[@stage=$_1]", stage).node();
		}
		
		protected Node modNode(String id) {
			return modStore.query().single("/id($_1)", "_"+id).node();
		}
		
		protected <T extends Mod> T mockMod(Class<T> clazz, final String id, final int stage) {
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
					"<blocks xmlns='" + Engine.RULES_NS + "'>" + xml + "</blocks>")).root().query().single("*").node();
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
			NamespaceMap ns = new NamespaceMap("", Engine.RULES_NS);
			assertFalse("good version", badBlockNames.contains(QName.parse("for", ns)));
			assertTrue("bad version", badBlockNames.contains(QName.parse("with", ns)));
			assertTrue("missing record", badBlockNames.contains(QName.parse("insert", ns)));
		}
	}

	@Deprecated
	public static class _ParseBlocksTest extends _RuleTest {
		private Node makeRule(String xml) {
			return db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<rule xmlns='" + Engine.RULES_NS + "'>" + xml + "</rule>")).root();
		}
		
		@Before public void overrideMockShim() throws SecurityException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
			initEmptyModStore();
			Field rule_rootMod = Rule.class.getDeclaredField("rootMod");
			rule_rootMod.setAccessible(true);
			rule_rootMod.set(rule, Mod.bootstrap(rule));
			rule.initDefaultShim();
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
					"<mod stage='0'>" + 
					"	<dependency doc='model/something.java'/>" + 
					"	<reference refid='j-230'/>" + 
					"	<mod stage='1' xml:id='_r1.1.'>" + 
					"		<dependency doc='mappings.xml'/>" + 
					"		<reference refid='m-12'/>" + 
					"		<mod stage='2'>" + 
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
			assertSame(mods.get(2), rule.restoreMod(modNodeAt(1)));
		}

		@Test public void restoreMod2() throws TransformException {
			final List<Mod> mods = mockModRestoreSequence(2);
			assertSame(mods.get(3), rule.restoreMod(modNodeAt(2)));
		}

		@Test public void restoreModsAtRootStageNoKeyHasMod() throws TransformException {
			mockMod(KeyMod.class, "_r1.", -1);
			List<Mod> nextStageMods = new ArrayList<Mod>();
			rule.restoreModsAtStage(nextStageMods, -1);
			assertTrue(nextStageMods.isEmpty());
		}
		
		@Test public void restoreModsAtRootStageIsKeyHasMod() throws TransformException {
			final KeyMod rootMod = mockMod(KeyMod.class, "_r1.", -1);
			blocks.set(0, mockery.mock(KeyBlock.class, "b0k"));
			rule.blocks.clear();  rule.blocks.addAll(blocks);
			List<Mod> nextStageMods = new ArrayList<Mod>();
			rule.restoreModsAtStage(nextStageMods, -1);
			assertEquals(Collections.singletonList(rootMod), nextStageMods);
		}
		
		@Test public void restoreModsAtRootStageNoKeyNoMod() throws TransformException {
			final KeyMod rootMod = mockMod(KeyMod.class, "_r1.", -1);
			modNodeAt(0).delete();
			List<Mod> nextStageMods = new ArrayList<Mod>();
			rule.restoreModsAtStage(nextStageMods, -1);
			assertEquals(Collections.singletonList(rootMod), nextStageMods);
		}
		
		@Test public void restoreModsAtRootStageIsKeyNoMod() throws TransformException {
			final KeyMod rootMod = mockMod(KeyMod.class, "_r1.", -1);
			blocks.set(0, mockery.mock(KeyBlock.class, "b0k"));
			rule.blocks.clear();  rule.blocks.addAll(blocks);
			modNodeAt(0).delete();
			List<Mod> nextStageMods = new ArrayList<Mod>();
			rule.restoreModsAtStage(nextStageMods, -1);
			assertEquals(Collections.singletonList(rootMod), nextStageMods);
		}
		
		@Test public void restoreModsAtPreKeyStage() throws TransformException {
			mockMod(KeyMod.class, "_r1.", -1);
			final Mod resultMod = mockMod(Mod.class, "_r1.2.", 2);
			mockery.checking(new Expectations() {{
				one(rule.self).restoreMod(modNodeAt(2));  will(returnValue(resultMod));
			}});
			List<Mod> nextStageMods = new ArrayList<Mod>();
			rule.restoreModsAtStage(nextStageMods, 2);
			assertEquals(Collections.singletonList(resultMod), nextStageMods);
		}
		
		@Test public void restoreModsAtPreKeyStageDuplicateKey() throws TransformException {
			mockMod(KeyMod.class, "_r1.", -1);
			List<Mod> nextStageMods = new ArrayList<Mod>();
			nextStageMods.add(mockMod(Mod.class, "_r1.1.", 2));
			nextStageMods = Collections.unmodifiableList(nextStageMods);
			rule.restoreModsAtStage(nextStageMods, 2);
		}
		
		@Test public void restoreModsAtPreLinearStageHasMod() throws TransformException {
			mockMod(KeyMod.class, "_r1.", -1);
			List<Mod> nextStageMods = new ArrayList<Mod>();
			rule.restoreModsAtStage(nextStageMods, 1);
			assertTrue(nextStageMods.isEmpty());
		}
		
		@Test public void restoreModsAtPreLinearStageNoMod() throws TransformException {
			mockMod(KeyMod.class, "_r1.", -1);
			final Mod resultMod = mockMod(KeyMod.class, "_r1.1.", 1);
			mockery.checking(new Expectations() {{
				one(rule.self).restoreMod(modNodeAt(1));  will(returnValue(resultMod));
			}});
			modNodeAt(2).delete();
			List<Mod> nextStageMods = new ArrayList<Mod>();
			rule.restoreModsAtStage(nextStageMods, 1);
			assertEquals(Collections.singletonList(resultMod), nextStageMods);
		}
		
		@Test public void resolveModsAtStage() throws TransformException {
			final List<Mod> mods = new ArrayList<Mod>();
			mods.add(mockery.mock(Mod.class, "m0"));
			mods.add(mockery.mock(Mod.class, "m1"));
			final Mod[] rmods = new Mod[] {mockery.mock(Mod.class, "rm0"), mockery.mock(Mod.class, "rm1"), mockery.mock(Mod.class, "rm2")};
			final QueryService scope = modStore.query();
			injectEngineCounter("numBlocksResolved");
			mockery.checking(new Expectations() {{
				one(mods.get(0)).resolveChildren(blocks.get(0), false, scope);
				will(returnValue(Arrays.asList(rmods).subList(0, 1)));
				one(mods.get(1)).resolveChildren(blocks.get(0), false, scope);
				will(returnValue(Arrays.asList(rmods).subList(1, 3)));
			}});
			Collection<Mod> nextStageMods = rule.resolveModsAtStage(mods, 0, scope);
			assertEquals(new HashSet<Mod>(Arrays.asList(rmods)), new HashSet<Mod>(nextStageMods));
			assertEquals(3, rule.engine.stats.numBlocksResolved.value());
		}

		@Test public void resolveModsAtLastStage() throws TransformException {
			final List<Mod> mods = new ArrayList<Mod>();
			mods.add(mockery.mock(Mod.class, "m0"));
			final Mod rmod = mockery.mock(Mod.class, "rm0");
			final QueryService scope = modStore.query();
			injectEngineCounter("numBlocksResolved");
			mockery.checking(new Expectations() {{
				one(mods.get(0)).resolveChildren(blocks.get(3), true, scope);
				will(returnValue(Collections.singleton(rmod)));
			}});
			Collection<Mod> nextStageMods = rule.resolveModsAtStage(mods, 3, scope);
			assertEquals(Collections.singleton(rmod), new HashSet<Mod>(nextStageMods));
			assertEquals(1, rule.engine.stats.numBlocksResolved.value());
		}

		@Test public void resolveModsAfterDifferentStage() throws TransformException {
			rule.firstDifferentStage = 2;
			final List<Mod> mods = new ArrayList<Mod>();
			mods.add(mockery.mock(Mod.class, "m0"));
			final Mod rmod = mockery.mock(Mod.class, "rm0");
			final QueryService scope = modStore.query();
			injectEngineCounter("numBlocksResolved");
			mockery.checking(new Expectations() {{
				one(mods.get(0)).resolveChildren(blocks.get(2), false, null);
				will(returnValue(Collections.singleton(rmod)));
			}});
			Collection<Mod> nextStageMods = rule.resolveModsAtStage(mods, 2, scope);
			assertEquals(Collections.singleton(rmod), new HashSet<Mod>(nextStageMods));
			assertEquals(1, rule.engine.stats.numBlocksResolved.value());
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
		
		private Matcher<QueryService> qsDocsMatcher;
		private Sequence seq;
		private List<Mod> prevMods;
		
		@Before public void setUp() {
			// set up a confuser mod for another rule
			initEmptyModStore();
			modStore.query().run("update insert <mods rule='r2' xml:id='_r2.'><mod stage='0'/></mods> into .");

			rule.firstDifferentStage = Integer.MAX_VALUE;
			mockMod(KeyMod.class, "_r1.", -1);
			injectEngineCounter("numModsCompleted");
			
			seq = mockery.sequence("process");
			prevMods = Collections.<Mod>emptyList();
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
			if (!doGlobalProcessing && verifySuccessful) {
				Set<String> docNames = new TreeSet<String>();
				for (Document doc : modifiedDocs) docNames.add(doc.path());
				qsDocsMatcher = new QueryServiceDocsMatcher(docNames);
			} else {
				qsDocsMatcher = Expectations.aNull(QueryService.class);
			}
		}
		
		private void prepIteration(Class<? extends Block> blockType, int numModsToResolve) throws TransformException {
			final int stage = rule.blocks.size();
			final Block block = mockery.mock(blockType, "block_" + rule.blocks.size());
			rule.blocks.add(block);
			final List<Mod> nextMods = new ArrayList<Mod>(numModsToResolve);
			for (int i=0; i<numModsToResolve; i++) {
				nextMods.add(mockery.mock(block instanceof KeyBlock ? KeyMod.class : Mod.class, "mod_" + stage + "-" + i));
			}
			mockery.checking(new Expectations() {{
				one(rule.self).restoreModsAtStage(prevMods, stage-1); inSequence(seq);
				one(rule.self).resolveModsAtStage(with(equal(prevMods)), with(equal(stage)), with(qsDocsMatcher));
				will(returnValue(nextMods));
			}});
			prevMods = nextMods;
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