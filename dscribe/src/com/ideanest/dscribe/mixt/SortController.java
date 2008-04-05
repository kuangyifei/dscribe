package com.ideanest.dscribe.mixt;

import static org.junit.Assert.*;

import java.lang.reflect.Field;
import java.util.*;

import org.exist.fluent.*;
import org.jmock.*;
import org.jmock.integration.junit4.*;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.*;
import org.junit.runner.RunWith;


public class SortController {
	private final Engine engine;
	private final Accumulator.Locator<XMLDocument> modifiedDocsLocator;
	private Set<XMLDocument> docsPending = new HashSet<XMLDocument>();
	private Map<String, Node> nodesPending = new HashMap<String, Node>();
	
	private Shim self;  // for testing only
	
	SortController(Engine engine, Accumulator.Locator<XMLDocument> modifiedDocsLocator) {
		this.engine = engine;
		this.modifiedDocsLocator = modifiedDocsLocator;
		initDefaultShim();
	}
	
	private interface Shim {
		void sort(Node node) throws TransformException;
		OrderGraph createGraph(Node target);
	}
	
	private void initDefaultShim() {
		this.self = new Shim() {
			public void sort(Node node) throws TransformException {
				SortController.this.sort(node);
			}
			public OrderGraph createGraph(Node target) {
				return new OrderGraph(target);
			}
		};
	}
	
	void eventuallySort(Node node) {
		if (!docsPending.contains(node.document())) nodesPending.put(node.query().single("@xml:id").value(), node);
	}
	
	boolean done() {
		return docsPending.isEmpty();
	}
	
	void executeEndOfCycle() throws TransformException {
		Set<XMLDocument> documentsChangedLastCycle = modifiedDocsLocator.catchUp();
		processNodes(documentsChangedLastCycle);
		processDocuments(documentsChangedLastCycle);
		recomputeDocsPending(documentsChangedLastCycle);
		// ignore documents that were only modified because of sorting
		modifiedDocsLocator.catchUp();
	}

	private void processNodes(Set<XMLDocument> documentsChangedLastCycle) throws TransformException {
		for (Iterator<Node> it = nodesPending.values().iterator(); it.hasNext(); ) {
			Node node = it.next();
			if (!node.extant() || docsPending.contains(node.document())) {
				it.remove();
			} else if (!documentsChangedLastCycle.contains(node.document())) {
				self.sort(node);
				it.remove();
			}
		}
	}
	
	private void processDocuments(Set<XMLDocument> documentsChangedLastCycle) throws TransformException {
		List<String> docPathsToSort = new ArrayList<String>();
		for (XMLDocument doc : docsPending) {
			if (!documentsChangedLastCycle.contains(doc)) docPathsToSort.add(engine.relativePath(doc));
		}
		for (String parentId : engine.modStore().query().unordered(
				"distinct-values(//order[@doc=$_1]/@refid)", docPathsToSort).values()) {
			sort(parentId);
		}
	}

	private void recomputeDocsPending(Set<XMLDocument> documentsChangedLastCycle) {
		docsPending.clear();
		for (XMLDocument doc : documentsChangedLastCycle) {
			if (engine.modStore().query().exists("//order[@doc=$_1]", engine.relativePath(doc))) {
				docsPending.add(doc);
			}
		}
	}

	private void sort(String nodeId) throws TransformException {
		self.sort(engine.globalScope().single("/id($_1)", nodeId).node());
	}
	
	private void sort(Node node) throws TransformException {
		OrderGraph graph = self.createGraph(node);
		Rule prevRule = null;
		int prevStage = -1;
		Collection<Seg> segs = new ArrayList<Seg>();
		for (Node modNode : engine.modStore().query().all(
				"for $mod in //mod[order/@refid=$_1/@xml:id] order by $mod/ancestor::mods/@rule, $mod/@stage return $mod", node).nodes()) {
			int stage = modNode.query().single("@stage").intValue();
			Rule rule = engine.findRule(modNode.query().single("ancestor::mods/@rule").value());
			if (prevRule != rule || prevStage != stage) {
				if (prevRule != null) prevRule.sortBlock(prevStage, segs, graph);
				segs.clear();
				prevRule = rule;
				prevStage = stage;
			}
			segs.add(rule.restoreMod(modNode).seg());
		}
		if (!segs.isEmpty()) {
			prevRule.sortBlock(prevStage, segs, graph);
			engine.stats().numOrdersChecked.increment();
			engine.stats().numElementsMoved.increment(graph.applyOrder());
		}
	}

	@Deprecated @RunWith(JMock.class) @DatabaseTestCase.ConfigFile("test/conf.xml") 
	public static class _Test extends DatabaseTestCase {
		private final Mockery mockery = new JUnit4Mockery() {{
			setImposteriser(ClassImposteriser.INSTANCE);
		}};
		private SortController sortController;
		private Engine engine;
		private Accumulator.Locator<XMLDocument> locator;
		private Folder workspace;
		private XMLDocument doc1, doc2, doc3;
		private Node modStore, e1, e2, f1, g1;
		
		@SuppressWarnings("unchecked")
		@Before public void setUp() {
			engine = mockery.mock(Engine.class);
			locator = mockery.mock(Accumulator.Locator.class);
			sortController = new SortController(engine, locator);
			sortController.self = mockery.mock(SortController.Shim.class);
			
			workspace = db.createFolder("/workspace");
			doc1 = db.getFolder("/workspace").documents().load(Name.create("doc1"), Source.xml(
					"<foo><e1 xml:id='e1'><c xml:id='e1-3' n='3'/><c xml:id='e1-2' n='2'/><c xml:id='e1-1' n='1'/></e1><e2 xml:id='e2'/></foo>"));
			e1 = doc1.query().single("/id('e1')").node();
			e2 = doc1.query().single("/id('e2')").node();
			doc2 = workspace.documents().load(Name.create("doc2"), Source.xml(
					"<foo><f1 xml:id='f1'/></foo>"));
			f1 = doc2.query().single("/id('f1')").node();
			doc3 = workspace.documents().load(Name.create("doc3"), Source.xml("<foo xml:id='g1'/>"));
			g1 = doc3.query().single("/id('g1')").node();
			workspace.namespaceBindings().put("", "http://example.com");
			
			modStore = db.getFolder("/").documents().load(
					Name.generate(), Source.xml("<modstore xmlns='" + Engine.MOD_NS + "'/>")).root();
			modStore.namespaceBindings().put("", Engine.MOD_NS);

			injectEngineCounter("numOrdersChecked");
			injectEngineCounter("numElementsMoved");
			mockery.checking(new Expectations() {{
				allowing(engine).relativePath(doc1);  will(returnValue(db.getFolder("/workspace").relativePath(doc1.path())));
				allowing(engine).relativePath(doc2);  will(returnValue(db.getFolder("/workspace").relativePath(doc2.path())));
				allowing(engine).relativePath(doc3);  will(returnValue(db.getFolder("/workspace").relativePath(doc3.path())));
				allowing(engine).modStore();  will(returnValue(modStore));
				allowing(engine).workspace();  will(returnValue(workspace));
				allowing(engine).globalScope();  will(returnValue(workspace.query()));
				allowing(engine).stats();  will(returnValue(engine.stats));
				allowing(engine).ensureWorkspaceNodeHasXmlId(doc1.query().single("//e1").node());  will(returnValue(true));
				allowing(engine).ensureWorkspaceNodeHasXmlId(doc1.query().single("//e2").node());  will(returnValue(true));
			}});
		}
		
		private Counter injectEngineCounter(String fieldName) {
			try {
				if (engine.stats == null) {
					Field field = Engine.class.getDeclaredField("stats");
					field.setAccessible(true);
					field.set(engine, new Engine.Stats());
				}
				final Counter counter = new Counter(fieldName + " = {0,number,integer}");
				Field field = Engine.Stats.class.getDeclaredField(fieldName);
				field.setAccessible(true);
				field.set(engine.stats, counter);
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
		
		@Test public void done() {
			assertTrue(sortController.done());
			sortController.docsPending.add(doc1);
			assertFalse(sortController.done());
		}
		
		@Test public void eventuallySort() {
			sortController.eventuallySort(e1);
			assertEquals(1, sortController.nodesPending.size());
			assertEquals(e1, sortController.nodesPending.get("e1"));
		}
		
		@Test public void eventuallySortDocAlreadyPending() {
			sortController.docsPending.add(doc1);
			sortController.eventuallySort(e1);
			assertEquals(Collections.emptyMap(), sortController.nodesPending);
		}
		
		@Test public void sortDelegation() throws TransformException {
			mockery.checking(new Expectations() {{
				one(sortController.self).sort(e1);
			}});
			sortController.sort("e1");
		}
		
		@Test public void executeEndOfCycleDocsOnly1() throws TransformException {
			sortController.docsPending.add(doc1);
			modStore.append().nodes(db.query().all(
					"<mods xmlns='" + Engine.MOD_NS + "' rule='r1'>" +
					"	<mod stage='0'>" +
					"		<mod stage='1'><order refid='e1' doc='doc1'/></mod>" +
					"		<mod stage='1'><order refid='e1' doc='doc1'/></mod>" +
					"	</mod>" +
					"	<mod stage='0'>" +
					"		<mod stage='1'><order refid='e2' doc='doc1'/></mod>" +
					"	</mod>" +
					"</mods>"
				).nodes()).commit();
			mockery.checking(new Expectations() {{
				exactly(2).of(locator).catchUp();
					will(onConsecutiveCalls(returnValue(Collections.emptySet()), returnValue(null)));
				one(sortController.self).sort(e1);
				one(sortController.self).sort(e2);
			}});
			sortController.executeEndOfCycle();
			assertEquals(Collections.emptySet(), sortController.docsPending);
		}
		
		@Test public void executeEndOfCycleDocsOnly2() throws TransformException {
			sortController.docsPending.add(doc1);
			sortController.docsPending.add(doc2);
			modStore.append().nodes(db.query().all(
					"<mods xmlns='" + Engine.MOD_NS + "' rule='r1'>" +
					"	<mod stage='0'>" +
					"		<mod stage='1'><order refid='e1' doc='doc1'/></mod>" +
					"		<mod stage='1'><order refid='e1' doc='doc1'/></mod>" +
					"	</mod>" +
					"	<mod stage='0'>" +
					"		<mod stage='1'><order refid='f1' doc='doc2'/></mod>" +
					"	</mod>" +
					"</mods>"
				).nodes()).commit();
			mockery.checking(new Expectations() {{
				exactly(2).of(locator).catchUp();
					will(onConsecutiveCalls(returnValue(new HashSet<XMLDocument>(Arrays.asList(doc2, doc3))), returnValue(null)));
				one(sortController.self).sort(e1);
			}});
			sortController.executeEndOfCycle();
			assertEquals(Collections.singleton(doc2), sortController.docsPending);
		}
		
		@Test public void executeEndOfCycleNodes() throws TransformException {
			sortController.nodesPending.put("e1", e1);
			sortController.nodesPending.put("e2", e2);
			sortController.nodesPending.put("f1", f1);
			sortController.nodesPending.put("g1", g1);
			e2.delete();
			sortController.docsPending.add(doc2);
			mockery.checking(new Expectations() {{
				exactly(2).of(locator).catchUp();
					will(onConsecutiveCalls(returnValue(Collections.singleton(doc3)), returnValue(null)));
				one(sortController.self).sort(e1);
			}});
			sortController.executeEndOfCycle();
			assertEquals(Collections.singletonMap("g1", g1), sortController.nodesPending);
		}
		
		@Test public void sort() throws TransformException {
			modStore.append().nodes(db.query().all(
					"(<mods xmlns='" + Engine.MOD_NS + "' rule='r1'>" +
					"	<mod stage='0'>" +
					"		<mod stage='1' xml:id='r1.1'><order refid='e1' doc='doc1'/></mod>" +
					"		<mod stage='1' xml:id='r1.2'><order refid='e1' doc='doc1'/></mod>" +
					"		<mod stage='1' xml:id='r1.3'><mod stage='2' xml:id='r1.3.1'><order refid='e1' doc='doc1'/></mod></mod>" +
					"	</mod>" +
					"</mods>, " +
					"<mods xmlns='" + Engine.MOD_NS + "' rule='r2'>" +
					"	<mod stage='0'>" +
					"		<mod stage='1' xml:id='r2.1'><order refid='e1' doc='doc1'/></mod>" +
					"	</mod>" +
					"</mods>)"
				).nodes()).commit();
			
			mockery.checking(new Expectations() {{
				Rule r1 = mockery.mock(Rule.class, "r1"), r2 = mockery.mock(Rule.class, "r2");
				allowing(engine).findRule("r1");  will(returnValue(r1));
				allowing(engine).findRule("r2");  will(returnValue(r2));
				
				Mod m11 = mockery.mock(Mod.class, "m11"), m12 = mockery.mock(Mod.class, "m12"), m131 = mockery.mock(Mod.class, "m131"), m21 = mockery.mock(Mod.class, "m21");
				Seg s11 = mockery.mock(Seg.class, "s11"), s12 = mockery.mock(Seg.class, "s12"), s131 = mockery.mock(Seg.class, "s131"), s21 = mockery.mock(Seg.class, "s21");
				one(r1).restoreMod(modStore.query().single("/id('r1.1')").node());  will(returnValue(m11));
				allowing(m11).seg();  will(returnValue(s11));
				one(r1).restoreMod(modStore.query().single("/id('r1.2')").node());  will(returnValue(m12));
				allowing(m12).seg();  will(returnValue(s12));
				one(r1).restoreMod(modStore.query().single("/id('r1.3.1')").node());  will(returnValue(m131));
				allowing(m131).seg();  will(returnValue(s131));
				one(r2).restoreMod(modStore.query().single("/id('r2.1')").node());  will(returnValue(m21));
				allowing(m21).seg();  will(returnValue(s21));
				
				OrderGraph graph = mockery.mock(OrderGraph.class);
				one(sortController.self).createGraph(e1);  will(returnValue(graph));
				one(graph).applyOrder();
				
				one(r1).sortBlock(1, Arrays.asList(s11, s12), graph);
				one(r1).sortBlock(2, Arrays.asList(s131), graph);
				one(r2).sortBlock(1, Arrays.asList(s21), graph);
			}});
			
			sortController.sort(e1);
		}

		@Test public void sortNoMods() throws TransformException {
			mockery.checking(new Expectations() {{
				OrderGraph graph = mockery.mock(OrderGraph.class);
				one(sortController.self).createGraph(e1);  will(returnValue(graph));
			}});
			
			sortController.sort(e1);
		}
	}
}
