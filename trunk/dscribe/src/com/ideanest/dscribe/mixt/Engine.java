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

import com.ideanest.dscribe.Namespace;

public class Engine {
	
	static final Logger LOG = Logger.getLogger(Engine.class);
	
	private final Map<String,Rule> ruleMap = new HashMap<String,Rule>();
	private final List<Rule> rules = new ArrayList<Rule>();
	
	private final Folder workspace;
	private final QueryService globalScope;
	private final QueryService utilQuery;

	private final Node modStore;

	private boolean didWork;
	private final Accumulator<Document> modifiedDocs = new Accumulator<Document>(); 

	final Counter
		numCycles = Counter.english("cycle", "cycles", null),
		numBlocksVerified = Counter.english("block", "blocks", "verified"),
		numBlocksResolved = Counter.english("block", "blocks", "resolved"),
		numModsCompleted = Counter.english("mod", "mods", "completed"),
		numModsWithdrawn = Counter.english("mod", "mods", "withdrawn");
	
	private final Counter
		modCountFormatter = Counter.english("mod", "mods", null),
		affectedCountFormatter = Counter.english("affected element", "affected elements", null);
	
	private final Document.Listener modifiedDocListener = new Document.Listener() {
		public void handle(org.exist.fluent.Document.Event ev) {
			if (ev.document.equals(modStore.document())) {
				didWork = true;
			} else {
				modifiedDocs.add(ev.document);
			}
		}
	};

	Engine(Folder rulespace, Folder prevrulespace, Folder workspace) throws RuleBaseException {
		LOG.debug("initializing engine");
		
		this.workspace = workspace;
		assert Namespace.MOD.equals(workspace.namespaceBindings().get("mod"));
		utilQuery = workspace.database().query();
		// Need to clone the workspace to avoid wiping out namespace bindings of the folder's shared query service.
		globalScope = workspace.cloneWithoutNamespaceBindings().query();
		
		this.modStore = initModStore();
		parseRules(rulespace, prevrulespace);
		
		LOG.debug("withdrawing mods of obsolete rules");
		withdrawMods(workspace.query().unordered("for $mod in //mod:mod where not(exists($_1/id($mod/@rule))) return $mod", rulespace));
		LOG.debug("withdrawing mods on obsolete documents");
		withdrawMods(workspace.query().unordered("//mod:mod[some $dep in .//mod:dependency/@doc satisfies not(doc-available($dep))]"));

		// TODO: sort rules into best-effort dependency order
		
	}
	
	// Constructor for testing only.
	private Engine(Folder workspace, Node modStore) {
		this.workspace = workspace;
		utilQuery = workspace.database().query();
		globalScope = null;
		if (modStore != null) {
			modStore.namespaceBindings().clear();
			modStore.namespaceBindings().put("", Namespace.MOD);
			modStore.namespaceBindings().put("mod", Namespace.MOD);
		}
		this.modStore = modStore;
	}
	
	private Node initModStore() {
		Node modsRoot = workspace.query().optional("/mod:mods").node();
		if (!modsRoot.extant()) modsRoot = workspace.documents().build(Name.adjust("modStore.xml"))
			.elem("mod:mods").end("mod:mods").commit().root();
		modsRoot.namespaceBindings().clear();
		modsRoot.namespaceBindings().put("", Namespace.MOD);
		modsRoot.namespaceBindings().put("mod", Namespace.MOD);
		return modsRoot;
	}

	private void parseRules(Folder rulespace, Folder prevrulespace) throws RuleBaseException {
		assert Namespace.RULES.equals(rulespace.namespaceBindings().get(""));
		assert Namespace.RULES.equals(prevrulespace.namespaceBindings().get(""));
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
	
	String relativePath(Document doc) {
		return workspace.relativePath(doc.path());
	}

	Folder workspace() {return workspace;}
	QueryService globalScope() {return globalScope;}
	QueryService utilQuery() {return utilQuery;}
	Node modStore() {return modStore;}

	/**
	 * Run the transformation.
	 * @param initialModifiedDocs 
	 * @throws TransformException 
	 * @throws InterruptedException 
	 */
	void executeTransform(Collection<Document> initialModifiedDocs) throws TransformException, InterruptedException {
		modifiedDocs.addAll(initialModifiedDocs);	// MUST happen after parsing rules, otherwise locators in wrong position
		workspace.listeners().add(
				EnumSet.of(Trigger.AFTER_CREATE, Trigger.AFTER_UPDATE),
				modifiedDocListener);
		try {
			do {
				numCycles.increment();
				LOG.debug("running MIXT transformation cycle " + numCycles.value());
				didWork = false;
				for (Rule rule : rules) {
					if (Thread.interrupted()) throw new InterruptedException();
					rule.process();
				}
				// TODO: detect livelock loops 				
			} while (didWork);
			LOG.info("MIXT transformation complete; "
					+ numCycles + ", " + numBlocksResolved + ", "
					+ numBlocksVerified + ", " + numModsCompleted + ", " + numModsWithdrawn);
		} finally {
			workspace.listeners().remove(modifiedDocListener);
		}
	}

	void withdrawMod(String key) {
		LOG.debug("withdrawing mod[" + key + "]");
		withdrawMods(modStore.query().unordered("/id($_1)/self::mod", key));
	}
	
	void withdrawRule(String ruleId) {
		LOG.debug("withdrawing all mods for rule[" + ruleId + "]");
		withdrawMods(modStore.query().unordered("//mod[@rule=$_1]", ruleId));
	}

	void withdrawMods(ItemList newMods)	{
		ItemList mods = workspace.query().all("()");
		ItemList affected = workspace.query().all("()");
		
		while (newMods.size() > 0) {
			newMods = modStore.query().unordered("$_1 union //mod[ancestor/@refid=$_1/@xml:id]", newMods);
			ItemList newAffected = workspace.query().unordered("/id($_1//mod:affected/@refid)", newMods);
			affected = utilQuery.unordered("$_1 union $_2", affected, newAffected);
			mods = utilQuery.unordered("$_1 union $_2", mods, newMods);
			newMods = modStore.query().unordered(
					"//mod[.//reference/@refid=$_1/descendant-or-self::*/@xml:id] except $_2",
					newAffected, mods);
		}
		
		for (String ruleId : utilQuery.unordered("distinct-values($_1/@rule)", mods).values()) {
			Rule rule = ruleMap.get(ruleId);
			if (rule == null) continue;
			ItemList docPaths = utilQuery.unordered("distinct-values($_1[@rule=$_2]//mod:dependency/@doc)", mods, ruleId);
			List<Document> docs = new ArrayList<Document>(docPaths.size());
			for (String docPath : docPaths.values()) {
				if (workspace.documents().contains(docPath)) {
					docs.add(workspace.documents().get(docPath));
				}
			}
			rule.addTouched(docs);
		}
		
		LOG.debug("deleting " + modCountFormatter.format(mods.size()) + " and " + affectedCountFormatter.format(affected.size()));
		
		numModsWithdrawn.increment(mods.size());
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
			workspace.namespaceBindings().put("mod", Namespace.MOD);
			rulespace = db.createFolder("/rulespace");
			rulespace.namespaceBindings().put("", Namespace.RULES);
			prevrulespace = db.createFolder("/prevrulespace");
			prevrulespace.namespaceBindings().put("", Namespace.RULES);
		}
		
		@Test public void initModStoreFresh() throws RuleBaseException {
			Engine engine = new Engine(workspace, null);
			Node modsRoot = engine.initModStore();
			assertEquals(Namespace.MOD, modsRoot.namespaceBindings().get(""));
			assertTrue(modsRoot.query().optional("self::mods").extant());
			assertEquals(workspace.query().optional("/mod:mods").node(), modsRoot);
		}

		@Test public void initModStoreExisting() throws RuleBaseException {
			workspace.documents().load(Name.generate(), Source.xml("<mods xmlns='" + Namespace.MOD + "'><mod xml:id='_foo.'/></mods>"));
			Engine engine = new Engine(workspace, null);
			Node modsRoot = engine.initModStore();
			assertEquals(Namespace.MOD, modsRoot.namespaceBindings().get(""));
			assertTrue(modsRoot.query().optional("self::mods").extant());
			assertEquals(workspace.query().optional("/mod:mods").node(), modsRoot);
			assertTrue(modsRoot.query().optional("mod").extant());
		}
		
		@Test public void parseRules() throws RuleBaseException, IllegalArgumentException, IllegalAccessException {
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Namespace.RULES + "'>" +
					"  <rule xml:id='r1' name='myrule'><create-doc>foobar</create-doc></rule>" +
					"</rules>"
			));
			Node modStore = workspace.documents().load(Name.generate(), Source.xml("<mods xmlns='" + Namespace.MOD + "'/>")).root();
			Engine engine = new Engine(workspace, modStore);
			engine.parseRules(rulespace, prevrulespace);
			assertEquals(1, engine.rules.size());
			assertSame(engine.rules.get(0), engine.ruleMap.get("r1"));
			assertEquals(0, firstDifferentStage.get(engine.rules.get(0)));
		}
		
		@Test public void create() throws RuleBaseException, IllegalArgumentException {
			rulespace.documents().load(Name.generate(), Source.xml(
					"<rules xmlns= '" + Namespace.RULES + "'>" +
					"  <rule xml:id='r1' name='myrule'><create-doc>foobar</create-doc></rule>" +
					"</rules>"
			));
			workspace.documents().load(Name.generate(), Source.xml(
					"<mods xmlns='" + Namespace.MOD + "'>" +
					"<mod xml:id='_r1.1' rule='r1'/>" +
					"<mod xml:id='_r1.2' rule='r1'><dependency doc='foo'/></mod>" +
					"<mod xml:id='_r1.3' rule='r1'><dependency doc='bar/foo'/></mod>" +
					"<mod xml:id='_r1.4' rule='r1'><dependency doc='bar/baz'/></mod>" +
					"<mod xml:id='_r2.1' rule='r2'/>" +
					"</mods>"));
			workspace.documents().load(Name.create("foo"), Source.xml("<foo/>"));
			workspace.children().create("bar").documents().load(Name.create("foo"), Source.xml("<foo/>"));
			Engine engine = new Engine(rulespace, prevrulespace, workspace);
			assertEquals(1, engine.rules.size());
			assertTrue("withdrew mod of live rule", workspace.query().exists("/id('_r1.1')"));
			assertTrue("withdrew mod of available documents", workspace.query().exists("/id('_r1.2')"));
			assertTrue("withdrew mod of available documents", workspace.query().exists("/id('_r1.3')"));
			assertFalse("did not withdraw mod of unavailable document", workspace.query().exists("/id('_r1.4')"));
			assertFalse("did not withdraw mod of dead rule", workspace.query().exists("/id('_r2.1')"));
		}
		
		@Test public void executeTransform() throws TransformException, InterruptedException {
			final XMLDocument olddoc1 = workspace.documents().load(Name.create("olddoc1"), Source.xml("<bar/>"));
			final XMLDocument olddoc2 = workspace.documents().load(Name.create("olddoc2"), Source.xml("<bar/>"));
			final Node modStore = workspace.documents().load(Name.generate(), Source.xml("<mods xmlns='" + Namespace.MOD + "'/>")).root();
			final Rule rule = mockery.mock(Rule.class);
			Engine engine = new Engine(workspace, modStore);
			final Accumulator.Locator<Document> locator = engine.modifiedDocs.anchor();
			mockery.checking(new Expectations() {{
				exactly(2).of(rule).process(); will(onConsecutiveCalls(
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
			engine.executeTransform(Collections.singleton((Document) olddoc1));
			assertEquals(new HashSet<Document>(Arrays.asList(new Document[] {olddoc2, workspace.documents().get("newdoc")})), locator.catchUp());
			assertEquals(2, engine.numCycles.value());
		}
		
		@Test(expected = InterruptedException.class)
		public void executeTransformCanBeInterrupted() throws TransformException, InterruptedException {
			final Node modStore = workspace.documents().load(Name.generate(), Source.xml("<mods xmlns='" + Namespace.MOD + "'/>")).root();
			final Rule rule = mockery.mock(Rule.class);
			Engine engine = new Engine(workspace, modStore);
			mockery.checking(new Expectations() {{
				one(rule).process(); will(onConsecutiveCalls(
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
			engine.executeTransform(Collections.<Document>emptySet());
		}
		
		@Test public void withdrawModsSimple() {
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<mods xmlns='" + Namespace.MOD + "'>" +
					"  <mod xml:id='m1' rule='r1'/>" +
					"  <mod xml:id='m2' rule='r1'/>" +
					"</mods>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawMods(modStore.query().all("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("*").size());
			assertEquals(1, engine.numModsWithdrawn.value());
		}

		@Test public void withdrawModsWithDescendants() {
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<mods xmlns='" + Namespace.MOD + "'>" +
					"  <mod xml:id='m1' rule='r1'/>" +
					"  <mod xml:id='m1.1' rule='r1'><ancestor refid='m1'/></mod>" +
					"  <mod xml:id='m2' rule='r1'/>" +
					"</mods>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawMods(modStore.query().all("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m1.1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("*").size());
			assertEquals(2, engine.numModsWithdrawn.value());
		}
		
		@Test public void withdrawModsWithAffectedDocs() {
			final Document doc = workspace.documents().load(Name.generate(), Source.xml(
					"<foo><bar xml:id='b1'/><bar xml:id='b2'/></foo>"));
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<mods xmlns='" + Namespace.MOD + "'>" +
					"  <mod xml:id='m1' rule='r1'><affected refid='b1'/></mod>" +
					"  <mod xml:id='m2' rule='r1'/>" +
					"</mods>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawMods(modStore.query().all("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("*").size());
			assertFalse(workspace.query().exists("/id('b1')"));
			assertTrue(workspace.query().exists("/id('b2')"));
			assertEquals(1, doc.query().all("*").size());
			assertEquals(1, engine.numModsWithdrawn.value());
		}

		@Test public void withdrawModsWithAffectedDocsAndDescendantDependencies() {
			final Document doc = workspace.documents().load(Name.create("b"), Source.xml(
					"<foo><bar xml:id='b1'/><bar xml:id='b2'/></foo>"));
			workspace.documents().load(Name.create("c"), Source.xml(
					"<foo><baz xml:id='c1'/></foo>"));
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<mods xmlns='" + Namespace.MOD + "'>" +
					"  <mod xml:id='m1' rule='r1'><affected refid='b1'/></mod>" +
					"  <mod xml:id='m1.1' rule='r1'><ancestor refid='m1'/><dependency doc='b'/></mod>" +
					"  <mod xml:id='m2' rule='r1'><dependency doc='c'/></mod>" +
					"</mods>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				one(r1).addTouched(with(aCollectionOf(doc)));
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawMods(modStore.query().all("/id('m1')"));
			assertFalse(modStore.query().exists("/id('m1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("*").size());
			assertFalse(workspace.query().exists("/id('b1')"));
			assertTrue(workspace.query().exists("/id('b2')"));
			assertEquals(1, doc.query().all("*").size());
			assertEquals(2, engine.numModsWithdrawn.value());
		}
		
		@Test public void withdrawModsWithAffectedDocsAndKnockOnReferences() {
			final Document doc = workspace.documents().load(Name.generate(), Source.xml(
					"<foo><bar xml:id='b1'><baz xml:id='b1a'/></bar><bar xml:id='b2'/><xyz xml:id='b3'/></foo>"));
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<mods xmlns='" + Namespace.MOD + "'>" +
					"  <mod xml:id='m1' rule='r1'><affected refid='b1'/></mod>" +
					"  <mod xml:id='m3' rule='r2'><reference refid='b1'/></mod>" +
					"  <mod xml:id='m4' rule='r2'><reference refid='b1a'/><affected refid='b3'/></mod>" +
					"  <mod xml:id='m2' rule='r1'/>" +
					"</mods>")).root();
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
			assertEquals(1, modStore.query().all("*").size());
			assertFalse(workspace.query().exists("/id('b1')"));
			assertTrue(workspace.query().exists("/id('b2')"));
			assertFalse(workspace.query().exists("/id('b3')"));
			assertEquals(1, doc.query().all("*").size());
			assertEquals(3, engine.numModsWithdrawn.value());
		}

		@Test public void withdrawMod() {
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<mods xmlns='" + Namespace.MOD + "'>" +
					"  <mod xml:id='m1' rule='r1'/>" +
					"  <mod xml:id='m2' rule='r1'/>" +
					"</mods>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawMod("m1");
			assertFalse(modStore.query().exists("/id('m1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("*").size());
			assertEquals(1, engine.numModsWithdrawn.value());
		}

		@Test public void withdrawRule() {
			Node modStore = workspace.documents().load(Name.generate(), Source.xml(
					"<mods xmlns='" + Namespace.MOD + "'>" +
					"  <mod xml:id='m1' rule='r1'/>" +
					"  <mod xml:id='m2' rule='r2'/>" +
					"</mods>")).root();
			Engine engine = new Engine(workspace, modStore);
			final Rule r1 = mockery.mock(Rule.class, "r1"); engine.rules.add(r1); engine.ruleMap.put("r1", r1);
			mockery.checking(new Expectations() {{
				allowing(r1).addTouched(with(anEmptyCollection(Document.class)));
			}});
			engine.withdrawRule("r1");
			assertFalse(modStore.query().exists("/id('m1')"));
			assertTrue(modStore.query().exists("/id('m2')"));
			assertEquals(1, modStore.query().all("*").size());
			assertEquals(1, engine.numModsWithdrawn.value());
		}

		private <E> Matcher<Collection<E>> aCollectionOf(E... items) {
			final Set<E> itemList = new HashSet<E>(Arrays.asList(items));
			return new TypeSafeMatcher<Collection<E>>() {
				@Override public boolean matchesSafely(Collection<E> item) {
					return new HashSet<E>(item).equals(itemList);
				}
				public void describeTo(Description description) {
					description.appendText("collection contents are " + itemList);
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
