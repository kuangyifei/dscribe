package com.ideanest.dscribe.mixt;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.*;

import org.apache.log4j.Logger;
import org.exist.fluent.*;
import org.hamcrest.*;
import org.jmock.*;
import org.jmock.api.*;
import org.jmock.integration.junit4.*;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.*;
import org.junit.runner.RunWith;

import com.ideanest.dscribe.mixt.blocks.*;


public class Engine {
	
	static final Logger LOG = Logger.getLogger(Engine.class);
	private static final String VERSION = "1";
	
	public static class Stats {
		public final Counter
				numCycles = Counter.english("cycle", "cycles", null),
				numBlocksVerified = Counter.english("block", "blocks", "verified"),
				numBlocksResolved = Counter.english("block", "blocks", "resolved"),
				numModsCompleted = Counter.english("mod", "mods", "completed"),
				numModsWithdrawn = Counter.english("mod", "mods", "withdrawn");
	}
	
	private final Map<String,Rule> ruleMap = new HashMap<String,Rule>();
	private final List<Rule> rules = new ArrayList<Rule>();
	
	private final Folder workspace;
	private final QueryService globalScope;
	private final QueryService utilQuery;
	private final Node modStore;

	private String autoGenIdPrefix;
	private boolean didWork, docsModified;
	final Stats stats = new Stats();
	private final Accumulator<XMLDocument> modifiedDocs = new Accumulator<XMLDocument>(); 
	
	private final Random random = new Random();
	private final Counter
		modCountFormatter = Counter.english("mod", "mods", null),
		affectedCountFormatter = Counter.english("affected element", "affected elements", null);
	
	public Engine(Resource rulespace, Resource prevrulespace, Folder workspace, Node modStore) throws RuleBaseException {
		LOG.debug("initializing engine");
		
		this.workspace = workspace;
		this.workspace.namespaceBindings().put("mod", Transformer.MOD_NS);
		utilQuery = workspace.database().query();
		// Need to clone the workspace to avoid wiping out namespace bindings of the folder's shared query service.
		globalScope = workspace.cloneWithoutNamespaceBindings().query();
		
		this.modStore = modStore;
		modStore.namespaceBindings().put("", Transformer.MOD_NS);
		modStore.namespaceBindings().put("mod", Transformer.MOD_NS);
		
		rulespace.namespaceBindings().put("", Transformer.RULES_NS);
		prevrulespace.namespaceBindings().put("", Transformer.RULES_NS);
		prevrulespace.namespaceBindings().put("record", Transformer.RULES_NS);
		invalidateIncompatibleBlocks(prevrulespace);
		parseRules(rulespace, prevrulespace);
		
		LOG.debug("withdrawing mods of obsolete rules");
		withdrawMods(modStore.query().unordered("for $mods in mods where not(exists($_1/id($mods/@rule))) return $mods", rulespace));
		LOG.debug("withdrawing mods on obsolete documents");
		// must run in workspace context to provide correct base URI for doc-available()
		withdrawMods(workspace.query()
				.unordered("$_1//mod:mod[some $dep in ./mod:dependency/@doc satisfies not(doc-available($dep))]", modStore));

		// TODO: sort rules into best-effort dependency order
	}
	
	// Constructor for testing only.
	private Engine(Folder workspace, Node modStore) {
		this.workspace = workspace;
		utilQuery = workspace.database().query();
		globalScope = workspace.cloneWithoutNamespaceBindings().query();
		if (modStore != null) {
			modStore.namespaceBindings().clear();
			modStore.namespaceBindings().put("", Transformer.MOD_NS);
			modStore.namespaceBindings().put("mod", Transformer.MOD_NS);
		}
		this.modStore = modStore;
	}

	private void parseRules(Resource rulespace, Resource prevrulespace) throws RuleBaseException {
		LOG.debug("parsing rules");
		ItemList ruleItems = rulespace.query().all("//rule");
		for (Node ruleDef : ruleItems.nodes()) {
			Node prevDef = prevrulespace.query().optional("/id($_1/@xml:id)", ruleDef).node();
			Rule rule = new Rule(ruleDef, prevDef, this, modifiedDocs.anchor());
			ruleMap.put(rule.id, rule);
			rules.add(rule);
		}
		LOG.debug("parsed " + ruleItems.size() + " rules");
	}
	
	private void invalidateIncompatibleBlocks(Resource prevrulespace) {
		for (QName badBlockName : Rule.verifyBlockTypeVersions(prevrulespace)) {
			for (Node badBlock : prevrulespace.query()
					.namespace("", badBlockName.getNamespaceURI()).presub()
					.all("//$1", badBlockName.getLocalPart()).nodes()) {
				// Adding a spurious attribute to the block will cause it to mismatch when
				// the rule compares it to the current one later.
				badBlock.update().namespace("record", Transformer.RECORD_NS)
						.attr("record:version-mismatch", "true").commit();
			}
		}		
	}
	
	/**
	 * Return whether the currently loaded MIXT processing code is the same version as
	 * that used for the last run of the transformation.
	 * 
	 * @param prevrulespace an ancestor of the resource to which versions were recorded on the last run
	 * @return <code>true</code> if the engine version matches, <code>false</code> otherwise
	 */
	public static boolean isSameVersionAs(Resource prevrulespace) {
		return VERSION.equals(prevrulespace.query().namespace("record", Transformer.RECORD_NS)
				.optional("//record:engine/@version").value());
	}
	
	/**
	 * Record the versions of the various currently loaded pieces of MIXT processing code.
	 * 
	 * @param builder a builder on the resource to write to
	 */
	public static void recordVersions(ElementBuilder<?> builder) {
		builder.namespace("record", Transformer.RECORD_NS)
			.elem("record:engine").attr("version", VERSION).end("record:engine");
		Rule.writeBlockTypeVersions(builder);
	}
	
	String relativePath(Document doc) {
		return workspace.relativePath(doc.path());
	}

	Folder workspace() {return workspace;}
	QueryService globalScope() {return globalScope;}
	QueryService utilQuery() {return utilQuery;}
	Node modStore() {return modStore;}
	
	public void autoGenerateIdsWithPrefix(String prefix) {
		if (!(Character.isLetter(prefix.charAt(0)) || prefix.charAt(0) == '_'))
			throw new IllegalArgumentException("xml id prefix must start with letter or underscore, got '" + prefix + "'");
		autoGenIdPrefix = prefix;
	}
	
	boolean ensureNodeHasXmlId(Node node) {
		if (node.query().exists("@xml:id")) return true;
		if (autoGenIdPrefix == null) return false;
		String id;
		do {
			id = autoGenIdPrefix + Integer.toString(random.nextInt(Integer.MAX_VALUE), Character.MAX_RADIX);
		} while (globalScope.exists("/id($_1)", id));
		node.update().attr("xml:id", id).commit();
		return true;
	}

	private final Document.Listener modifiedDocListener = new Document.Listener() {
		public void handle(org.exist.fluent.Document.Event ev) {
			if (ev.document.equals(modStore.document())) {
				didWork = true;
			} else {
				try {
					modifiedDocs.add(ev.document.xml());
					docsModified = true;
				} catch (DatabaseException e) {
					// must have been a blob document, ignore
				}
			}
		}
	};

	/**
	 * Run the transformation.
	 * @param initialModifiedDocs the docs that may have been modified since the last run; if <code>null</code>, don't do incremental processing
	 * @return the date as of which the transformation is current; any documents modified after this date may not have been considered
	 * @throws TransformException 
	 * @throws InterruptedException 
	 */
	public Date executeTransform(Collection<XMLDocument> initialModifiedDocs) throws TransformException, InterruptedException {
		if (initialModifiedDocs != null) modifiedDocs.addAll(initialModifiedDocs);	// MUST happen after parsing rules, otherwise locators in wrong position
		workspace.listeners().add(	EnumSet.of(Trigger.AFTER_CREATE, Trigger.AFTER_UPDATE), modifiedDocListener);
		if (!workspace.contains(modStore.document())) modStore.document().listeners().add(Trigger.AFTER_UPDATE, modifiedDocListener);
		Date lastRun;
		try {
			do {
				lastRun = new Date();
				stats.numCycles.increment();
				LOG.debug("running MIXT transformation cycle " + stats.numCycles.value());
				didWork = false;
				docsModified = false;
				for (Rule rule : rules) {
					if (Thread.interrupted()) throw new InterruptedException();
					rule.process(stats.numCycles.value() == 1 && initialModifiedDocs == null);
				}
				// TODO: detect livelock loops 				
			} while (didWork || docsModified);
			LOG.info("MIXT transformation complete; "
					+ stats.numCycles + ", " + stats.numBlocksResolved + ", "
					+ stats.numBlocksVerified + ", " + stats.numModsCompleted + ", " + stats.numModsWithdrawn);
			return lastRun;
		} finally {
			workspace.listeners().remove(modifiedDocListener);
			if (!workspace.contains(modStore.document())) modStore.document().listeners().remove(modifiedDocListener);
		}
	}
	
	public Stats stats() {return stats;}
	
	void withdrawMod(Node modNode) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("withdrawing mod " + modNode.query().single("./ancestor-or-self::mod[@xml:id][1]/@xml:id").value() + " at stage " + modNode.query().single("@stage").value());
		}
		withdrawMods(modNode.query().unordered("self::*"));
	}
	
	void withdrawRule(String ruleId) {
		LOG.debug("withdrawing all mods for rule[" + ruleId + "]");
		withdrawMods(modStore.query().unordered("mods[@rule=$_1]", ruleId));
	}

	void withdrawMods(ItemList newMods)	{
		ItemList mods = workspace.query().all("()");
		ItemList affected = workspace.query().all("()");
		
		while (newMods.size() > 0) {
			ItemList newAffected = workspace.query().unordered("/id($_1//mod:affected/@refid)", newMods);
			affected = utilQuery.unordered("$_1 union $_2", affected, newAffected);
			mods = utilQuery.unordered("$_1 union $_2", mods, newMods);
			// TODO: refactor query to use idref() once we can declare @refid as type IDREF
			newMods = modStore.query().unordered(
					"//mod[reference/@refid=$_1/descendant-or-self::*/@xml:id] except $_2",
					newAffected, mods);
		}
		
		mods = mods.query().namespace("", Transformer.MOD_NS).unordered("$_1 except $_1/descendant::*", mods);
		
		for (String ruleId : utilQuery.unordered("distinct-values($_1/ancestor-or-self::*/@rule)", mods).values()) {
			Rule rule = ruleMap.get(ruleId);
			if (rule == null) continue;
			// Touch only the dependencies of the highest withdrawn mods, since any children will be resolved in global scope anyway.
			ItemList docPaths = utilQuery.unordered("distinct-values($_1[ancestor-or-self::*/@rule=$_2]/mod:dependency/@doc)", mods, ruleId);
			List<Document> docs = new ArrayList<Document>(docPaths.size());
			for (String docPath : docPaths.values()) {
				if (workspace.documents().contains(docPath)) docs.add(workspace.documents().get(docPath));
			}
			rule.addTouched(docs);
		}
		
		LOG.debug("deleting " + modCountFormatter.format(mods.size()) + " and " + affectedCountFormatter.format(affected.size()));
		
		stats.numModsWithdrawn.increment(
				mods.query().namespace("", Transformer.MOD_NS).single("count(descendant-or-self::mod)").intValue());
		affected.deleteAllNodes();
		mods.deleteAllNodes();
	}
	
	@Deprecated @RunWith(JMock.class) @DatabaseTestCase.ConfigFile("test/conf.xml") 
	public static class _Test extends DatabaseTestCase {
		private static Field firstDifferentStage;
		@BeforeClass public static void setupAccessors() throws SecurityException, NoSuchFieldException {
			firstDifferentStage = Rule.class.getDeclaredField("firstDifferentStage");
			firstDifferentStage.setAccessible(true);
		}
		
		protected final Mockery mockery = new JUnit4Mockery() {{
			setImposteriser(ClassImposteriser.INSTANCE);
		}};
		private Folder workspace, rulespace, prevrulespace;
		
		@Before public void createSpaces() {
			workspace = db.createFolder("/workspace");
			workspace.namespaceBindings().put("mod", Transformer.MOD_NS);
			rulespace = db.createFolder("/rulespace");
			rulespace.namespaceBindings().put("", Transformer.RULES_NS);
			prevrulespace = db.createFolder("/prevrulespace");
			prevrulespace.namespaceBindings().put("", Transformer.RULES_NS);
		}
		
		@Test public void invalidateIncompatibleBlocks() {
			db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<root xmlns='" + Transformer.RECORD_NS + "'>" +
					"  <block-type class='com.ideanest.dscribe.mixt.blocks.For' version='" + new For().version() + "'/>" +
					"  <block-type class='com.ideanest.dscribe.mixt.blocks.With' version='" + new With().version() + "foo'/>" +
					"</root>"));
			db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<rules xmlns='" + Transformer.RULES_NS + "'>" +
					"  <rule xml:id='r1'>" +
					"    <for each='$x'>/foo</for>" +
					"    <with some='$y'>$x/bar</with>" +
					"    <insert><xyz/></insert>" +
					"  </rule>" +
					"</rules>"));
			Engine engine = new Engine(workspace, null);
			engine.invalidateIncompatibleBlocks(db.getFolder("/"));
			Node rule = db.getFolder("/").query()
					.namespace("", Transformer.RULES_NS)
					.namespace("record", Transformer.RECORD_NS)
					.single("/id('r1')").node();
			assertFalse(rule.query().exists("for[@record:version-mismatch]"));
			assertTrue(rule.query().exists("with[@record:version-mismatch]"));
			assertTrue(rule.query().exists("insert[@record:version-mismatch]"));
		}
		
		@Test public void recordAndVerifyVersion() {
			ElementBuilder<?> builder = db.getFolder("/").documents().build(Name.generate()).elem("root");
			Engine.recordVersions(builder);
			builder.end("root").commit();
			assertTrue(Engine.isSameVersionAs(db.getFolder("/")));
			assertTrue(db.getFolder("/").query().namespace("", Transformer.RECORD_NS).exists("//block-type"));
		}
		
		@Test public void verifyBadVersion() {
			db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<root xmlns='" + Transformer.RECORD_NS + "'>" +
					"  <engine version='" + Engine.VERSION + "foo'/>" +
					"</root>"));
			assertFalse(Engine.isSameVersionAs(db.getFolder("/")));
		}
		
		@Test public void parseRules() throws RuleBaseException, IllegalArgumentException, IllegalAccessException {
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Transformer.RULES_NS + "'>" +
					"  <rule xml:id='r1' name='myrule'><create-doc>foobar</create-doc></rule>" +
					"</rules>"
			));
			Node modStore = workspace.documents().load(Name.generate(), Source.xml("<modstore xmlns='" + Transformer.MOD_NS + "'/>")).root();
			Engine engine = new Engine(workspace, modStore);
			engine.parseRules(rulespace, prevrulespace);
			assertEquals(1, engine.rules.size());
			assertSame(engine.rules.get(0), engine.ruleMap.get("r1"));
			assertEquals(0, firstDifferentStage.get(engine.rules.get(0)));
		}
		
		@Test public void create() throws RuleBaseException, IllegalArgumentException {
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Transformer.RULES_NS + "'>" +
					"  <rule xml:id='r1' name='myrule'><create-doc>foobar</create-doc></rule>" +
					"</rules>"
			));
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Transformer.MOD_NS + "'>" +
					"<mods rule='r1'>" +
					"	<mod xml:id='_r1.1'/>" +
					"	<mod xml:id='_r1.2'><dependency doc='foo'/></mod>" +
					"	<mod xml:id='_r1.3'><dependency doc='bar/foo'/></mod>" +
					"	<mod xml:id='_r1.4'><dependency doc='bar/baz'/></mod>" +
					"</mods>" +
					"<mods rule='r2'>" +
					"	<mod xml:id='_r2.1'/>" +
					"</mods>" +
					"</modstore>")).root();
			modStore.namespaceBindings().put("", Transformer.MOD_NS);
			modStore.namespaceBindings().put("mod", Transformer.MOD_NS);
			workspace.documents().load(Name.create("foo"), Source.xml("<foo/>"));
			workspace.children().create("bar").documents().load(Name.create("foo"), Source.xml("<foo/>"));
			Engine engine = new Engine(rulespace, prevrulespace, workspace, modStore);
			assertEquals(1, engine.rules.size());
			assertTrue("withdrew mod of live rule", workspace.query().exists("/id('_r1.1')"));
			assertTrue("withdrew mod of available documents", workspace.query().exists("/id('_r1.2')"));
			assertTrue("withdrew mod of available documents", workspace.query().exists("/id('_r1.3')"));
			assertFalse("did not withdraw mod of unavailable document", workspace.query().exists("/id('_r1.4')"));
			assertFalse("did not withdraw mod of dead rule", workspace.query().exists("/id('_r2.1')"));
		}
		
		@Test public void ensureNodeHasXmlId() {
			Engine engine = new Engine(workspace, null);
			assertTrue(engine.ensureNodeHasXmlId(workspace.documents().load(Name.generate(), Source.xml("<foo xml:id='f'/>")).root()));
		}
		
		@Test public void ensureNodeHasXmlIdNoIdNoAutogen() {
			Engine engine = new Engine(workspace, null);
			assertFalse(engine.ensureNodeHasXmlId(workspace.documents().load(Name.generate(), Source.xml("<foo/>")).root()));
		}
		
		@Test public void ensureNodeHasXmlIdNoIdWithAutogen() {
			Engine engine = new Engine(workspace, null);
			engine.autoGenerateIdsWithPrefix("mixt-");
			Node node = workspace.documents().load(Name.generate(), Source.xml("<foo/>")).root();
			assertTrue(engine.ensureNodeHasXmlId(node));
			assertTrue(node.query().single("@xml:id").value().startsWith("mixt-"));
		}
		
		@Test public void executeTransform() throws TransformException, InterruptedException {
			final XMLDocument olddoc1 = workspace.documents().load(Name.create("olddoc1"), Source.xml("<bar/>"));
			final XMLDocument olddoc2 = workspace.documents().load(Name.create("olddoc2"), Source.xml("<bar/>"));
			final Node modStore = workspace.documents().load(Name.generate(), Source.xml("<modstore xmlns='" + Transformer.MOD_NS + "'/>")).root();
			final Rule rule = mockery.mock(Rule.class);
			Engine engine = new Engine(workspace, modStore);
			final Accumulator.Locator<XMLDocument> locator = engine.modifiedDocs.anchor();
			mockery.checking(new Expectations() {{
				exactly(2).of(rule).process(false); will(onConsecutiveCalls(
						new Action() {
							public Object invoke(Invocation invocation) throws Throwable {
								assertEquals(Collections.singleton(olddoc1), locator.catchUp());
								workspace.documents().load(Name.create("newdoc"), Source.xml("<foo/>"));
								olddoc2.root().append().elem("bar").end("bar").commit();
								modStore.append().elem("mod").end("mod").commit();
								return null;
							}
							public void describeTo(Description description) {}
						},
						returnValue(null)
				));
			}});
			engine.rules.add(rule);
			engine.executeTransform(Collections.singleton(olddoc1));
			assertEquals(new HashSet<Document>(Arrays.asList(new Document[] {olddoc2, workspace.documents().get("newdoc")})), locator.catchUp());
			assertEquals(2, engine.stats.numCycles.value());
		}
		
		@Test(expected = InterruptedException.class)
		public void executeTransformCanBeInterrupted() throws TransformException, InterruptedException {
			final Node modStore = workspace.documents().load(Name.generate(), Source.xml("<modstore xmlns='" + Transformer.MOD_NS + "'/>")).root();
			final Rule rule = mockery.mock(Rule.class);
			Engine engine = new Engine(workspace, modStore);
			mockery.checking(new Expectations() {{
				one(rule).process(false); will(onConsecutiveCalls(
						new Action() {
							public Object invoke(Invocation invocation) throws Throwable {
								modStore.append().elem("mod").end("mod").commit();
								Thread.currentThread().interrupt();
								return null;
							}
							public void describeTo(Description description) {}
						},
						returnValue(null)
				));
			}});
			engine.rules.add(rule);
			engine.executeTransform(Collections.<XMLDocument>emptySet());
		}
		
		@Test public void withdrawModsSimple() {
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Transformer.MOD_NS + "'>" +
					"<mods rule='r1'>" +
					"  <mod xml:id='m1'/>" +
					"  <mod xml:id='m2'/>" +
					"</mods>" +
					"</modstore>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawMods(modStore.query().all("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("//mod").size());
			assertEquals(1, engine.stats.numModsWithdrawn.value());
		}

		@Test public void withdrawModsWithDescendants() {
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Transformer.MOD_NS + "'>" +
					"<mods rule='r1'>" +
					"  <mod xml:id='m1'>" +
					"  	<mod xml:id='m1.1'/>" +
					"	</mod>" +
					"  <mod xml:id='m2'/>" +
					"</mods>" +
					"</modstore>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawMods(modStore.query().all("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m1.1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("//mod").size());
			assertEquals(2, engine.stats.numModsWithdrawn.value());
		}
		
		@Test public void withdrawModsWithAffectedDocs() {
			final Document doc = workspace.documents().load(Name.generate(), Source.xml(
					"<foo><bar xml:id='b1'/><bar xml:id='b2'/></foo>"));
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Transformer.MOD_NS + "'>" +
					"<mods rule='r1'>" +
					"  <mod xml:id='m1'><affected refid='b1'/></mod>" +
					"  <mod xml:id='m2'/>" +
					"</mods>" +
					"</modstore>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawMods(modStore.query().all("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("//mod").size());
			assertFalse(workspace.query().exists("/id('b1')"));
			assertTrue(workspace.query().exists("/id('b2')"));
			assertEquals(1, doc.query().all("*").size());
			assertEquals(1, engine.stats.numModsWithdrawn.value());
		}

		@Test public void withdrawModsWithAffectedDocsAndDependencies() {
			final Document doc = workspace.documents().load(Name.create("a"), Source.xml(
			"<foo><baz xml:id='a1'/></foo>"));
			workspace.documents().load(Name.create("b"), Source.xml(
					"<foo><bar xml:id='b1'/><bar xml:id='b2'/></foo>"));
			workspace.documents().load(Name.create("c"), Source.xml(
					"<foo><baz xml:id='c1'/></foo>"));
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Transformer.MOD_NS + "'>" +
					"<mods rule='r1'>" +
					"  <mod xml:id='m1'><affected refid='b1'/><dependency doc='a'/>" +
					"  	<mod xml:id='m1.1'><dependency doc='c'/></mod>" +
					"	</mod>" +
					"  <mod xml:id='m2'><dependency doc='b'/></mod>" +
					"</mods>" +
					"</modstore>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				one(r1).addTouched(with(aCollectionOf(doc)));
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawMods(modStore.query().all("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("//mod").size());
			assertFalse(workspace.query().exists("/id('b1')"));
			assertTrue(workspace.query().exists("/id('b2')"));
			assertEquals(1, doc.query().all("*").size());
			assertEquals(2, engine.stats.numModsWithdrawn.value());
		}
		
		@Test public void withdrawModsWithAffectedDocsAndKnockOnReferences() {
			final Document doc = workspace.documents().load(Name.generate(), Source.xml(
					"<foo><bar xml:id='b1'><baz xml:id='b1a'/></bar><bar xml:id='b2'/><xyz xml:id='b3'/></foo>"));
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Transformer.MOD_NS + "'>" +
					"<mods rule='r1'>" +
					"  <mod xml:id='m1'><affected refid='b1'/></mod>" +
					"  <mod xml:id='m2'/>" +
					"</mods>" +
					"<mods rule='r2'>" +
					"  <mod xml:id='m3'><reference refid='b1'/></mod>" +
					"  <mod xml:id='m4'><reference refid='b1a'/><affected refid='b3'/></mod>" +
					"</mods>" +
					"</modstore>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawMods(modStore.query().all("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m3')"));
			assertFalse(modStore.query().exists("/id('m4')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("//mod").size());
			assertFalse(workspace.query().exists("/id('b1')"));
			assertTrue(workspace.query().exists("/id('b2')"));
			assertFalse(workspace.query().exists("/id('b3')"));
			assertEquals(1, doc.query().all("*").size());
			assertEquals(3, engine.stats.numModsWithdrawn.value());
		}

		@Test public void withdrawRule() {
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Transformer.MOD_NS + "'>" +
					"<mods rule='r1'>" +
					"  <mod xml:id='m1'/>" +
					"</mods>" +
					"<mods rule='r2'>" +
					"  <mod xml:id='m2'/>" +
					"</mods>" +
					"</modstore>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawRule("r1");
			assertFalse(modStore.query().exists("/id('m1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("//mod").size());
			assertEquals(1, engine.stats.numModsWithdrawn.value());
		}

		@Test public void withdrawMod() {
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<modstore xmlns='" + Transformer.MOD_NS + "'>" +
					"<mods rule='r1'>" +
					"  <mod xml:id='m1'/>" +
					"  <mod xml:id='m2'/>" +
					"</mods>" +
					"</modstore>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawMod(modStore.query().single("/id('m1')").node());
			assertFalse(modStore.query().exists("/id('m1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("//mod").size());
			assertEquals(1, engine.stats.numModsWithdrawn.value());
		}

		private <E> Matcher<Collection<E>> aCollectionOf(E... items) {
			final Set<E> itemList = new HashSet<E>(Arrays.asList(items));
			return new TypeSafeMatcher<Collection<E>>() {
				@Override public boolean matchesSafely(Collection<E> item) {
					return new HashSet<E>(item).equals(itemList);
				}
				public void describeTo(Description description) {
					description.appendText("collection of  " + itemList);
				}
			};
		}

		private <E> Matcher<Collection<E>> anEmptyCollection(Class<E> clazz) {
			return new TypeSafeMatcher<Collection<E>>() {
				@Override public boolean matchesSafely(Collection<E> item) {
					return item.isEmpty();
				}
				public void describeTo(Description description) {
					description.appendText("an empty collection");
				}
			};
		}
	}
}
