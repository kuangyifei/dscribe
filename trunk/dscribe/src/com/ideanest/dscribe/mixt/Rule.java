package com.ideanest.dscribe.mixt;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.*;

import org.apache.log4j.Logger;
import org.exist.fluent.*;
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
	private int firstDifferentStage;
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
		int processSubtree(Mod mod, QueryService touchedScope, ItemList modsToVerify) throws TransformException;
	}
	
	private void initDefaultShim() {
		this.self = new Shim() {
			@Override public Mod rootMod() {
				return Rule.this.bootstrapMod();
			}
			@Override public int processSubtree(Mod mod, QueryService touchedScope, ItemList modsToVerify) throws TransformException {
				return Rule.this.processSubtree(mod, touchedScope, modsToVerify);
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
				
		int lastStageNumModsResolved = self.processSubtree(self.rootMod(), touchedScope, modsToVerify);
		
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
	
	private int processSubtree(Mod mod, QueryService touchedScope, ItemList modsToVerify) throws TransformException {
		final int nextStage = mod.stage + 1;
		final Block block = blocks.get(nextStage);
		final boolean lastBlock = nextStage == blocks.size() - 1;
		final QueryService stageScope = nextStage >= firstDifferentStage ? null : touchedScope;
		
		int numFullyResolved = 0;

		if (!(modsToVerify == null && lastBlock)) {
			for (Node childNode : mod.node().query().unordered("*").nodes()) {
				if (!(childNode.extant() && nodeIsMod(childNode))) continue;
				// Calculating whether the branch is complete is not worth it, since running the query (or storing a
				// precomputed flag) is expensive, and the benefit is low for typical rules that have a bunch of key
				// blocks followed by one or two cheap linear ones.
				// boolean branchComplete = verifyOnly || (nextStage == lastKeyStage && rootModNode.query().exists("//mod[@stage=$_1] intersect $_2/descendant::*", blocks.size()-1, childNode));
				boolean mustVerifyChild = modsToVerify != null && engine.utilQuery().exists("$_1 intersect $_2", modsToVerify, childNode);
				// boolean mustVerifyDescendant = !resolveOnly && modsToVerify != null && engine.utilQuery().exists("$_1 intersect $_2/descendant::*", modsToVerify, childNode);
				
				Mod childMod = null;
				if (mustVerifyChild) {
					try {
						childMod = mod.restoreChild(block, childNode);
						engine.stats.numBlocksRestored.increment();
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
				} else if (!lastBlock) {
					try {
						childMod = mod.restoreChild(block, childNode);
						engine.stats.numBlocksRestored.increment();
					} catch (TransformException e) {
						LOG.error("failed to restore " + this + " " + childNode + " with no modified dependencies", e);
						// This should never happen, but we could try to recover by withdrawing the rule and
						// recomputing all mods from scratch.
						// engine.withdrawRule(id);
						throw e;
					}
				}
				if (childMod != null && !lastBlock) {
					numFullyResolved += processSubtree(childMod, touchedScope, modsToVerify);
				}
			}
		}
			
		if (mod.node().extant() && (block instanceof KeyBlock || !hasModChild(mod.node()))) {
			LOG.debug("resolving children of " + mod);
			List<Mod> resolvedChildren = lastBlock ? null : new ArrayList<Mod>(); 
			int numResolved = mod.resolveChildren(block, lastBlock, stageScope, resolvedChildren);
			engine.stats.numBlocksResolved.increment();
			if (lastBlock) {
				numFullyResolved += numResolved;
			} else {
				for (Mod childMod : resolvedChildren) {
					numFullyResolved += processSubtree(childMod, null, null);
				}
			}
		}
		
		return numFullyResolved;
	}

	private Mod bootstrapMod() {
		Mod rootMod = Mod.bootstrap(this);
		rootMod.node().namespaceBindings().put("", Engine.MOD_NS);
		return rootMod;
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
		engine.stats.numBlocksRestored.increment();
		return mod;
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
			injectEngineCounter("numBlocksRestored");
			assertSame(mods.get(2), rule.restoreMod(modNodeAt(1), null));
			assertEquals(1, rule.engine.stats.numBlocksRestored.value());
		}

		@Test public void restoreMod2() throws TransformException {
			final List<Mod> mods = mockModRestoreSequence(2);
			injectEngineCounter("numBlocksRestored");
			assertSame(mods.get(3), rule.restoreMod(modNodeAt(2), null));
			assertEquals(1, rule.engine.stats.numBlocksRestored.value());
		}
		
		@Test public void restoreMod3() throws TransformException {
			injectEngineCounter("numBlocksRestored");
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
			assertEquals(1, rule.engine.stats.numBlocksRestored.value());
		}
		
	}

}