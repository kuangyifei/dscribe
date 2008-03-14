package com.ideanest.dscribe.mixt;

import static org.junit.Assert.assertEquals;

import java.util.*;

import org.apache.log4j.Logger;
import org.exist.fluent.*;
import org.junit.*;


public class SortController {
	private static final Logger LOG = Logger.getLogger(SortController.class);
	
	private final Engine engine;
	private final Accumulator.Locator<XMLDocument> modifiedDocsLocator;
	private Set<XMLDocument> docsPending = new HashSet<XMLDocument>();
	private Map<String, Node> nodesPending = new HashMap<String, Node>();
	
	SortController(Engine engine, Accumulator.Locator<XMLDocument> modifiedDocsLocator) {
		this.engine = engine;
		this.modifiedDocsLocator = modifiedDocsLocator;
	}
	
	void eventuallySort(Node node) {
		if (!docsPending.contains(node.document())) nodesPending.put(node.query().single("@xml:id").value(), node);
	}
	
	void executeEndOfCycle() throws TransformException {
		Set<XMLDocument> documentsChangedLastCycle = modifiedDocsLocator.catchUp();
		for (Iterator<Node> it = nodesPending.values().iterator(); it.hasNext(); ) {
			Node node = it.next();
			if (!node.extant() || docsPending.contains(node.document())) {
				it.remove();
			} else if (!documentsChangedLastCycle.contains(node)) {
				sort(node);
				it.remove();
			}
		}
		docsPending.removeAll(documentsChangedLastCycle);
		for (String parentId : engine.modStore().query().unordered(
				"distinct-values(//order[@doc=$_1]/@refid)", docsPending).values()) {
			sort(parentId);
		}
		// ignore documents that were only modified because of sorting
		modifiedDocsLocator.catchUp();
		docsPending.clear();
		docsPending.addAll(documentsChangedLastCycle);
		for (Iterator<XMLDocument> it = docsPending.iterator(); it.hasNext(); ) {
			if (!engine.modStore().query().exists("//order[@doc=$_1]", engine.relativePath(it.next()))) {
				it.remove();
			}
		}
	}
	
	boolean done() {
		return docsPending.isEmpty();
	}
	
	private void sort(String nodeId) throws TransformException {
		sort(engine.globalScope().single("/id($_1)", nodeId).node());
	}
	
	private void sort(Node node) throws TransformException {
		OrderGraph graph = new OrderGraph(node);
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
		graph.applyOrder();
	}
	
	public static class OrderGraph {
		private final Node target;
		private final int numNodes;
		private final Map<String, Integer> nodeIndex = new HashMap<String, Integer>();
		private final int[] edges;
		private final int[] maxes;
		private final boolean[] placed;
		
		OrderGraph(Node target) {
			this.target = target;
			numNodes = target.query().single("count(*)").intValue();
			edges = new int[numNodes*numNodes];
			Arrays.fill(edges, Integer.MIN_VALUE);
			maxes = new int[numNodes];
			placed = new boolean[numNodes];
			int i = 0;
			for (String id : target.query().all("*/@xml:id").values()) nodeIndex.put(id, i++);
		}
		
		public void order(Node first, Node second, int priority) {
			order(first.query().single("@xml:id").value(), second.query().single("@xml:id").value(), priority);
		}
		
		public void order(String firstNodeId, String secondNodeId, int priority) {
			if (!nodeIndex.containsKey(firstNodeId)) throw new IllegalArgumentException("unknown node id " + firstNodeId);
			if (!nodeIndex.containsKey(secondNodeId)) throw new IllegalArgumentException("unknown node id " + secondNodeId);
			int fromIndex = nodeIndex.get(firstNodeId), toIndex = nodeIndex.get(secondNodeId);
			if (fromIndex == toIndex) throw new IllegalArgumentException("can't order a node relative to itself: position " + fromIndex);
			if (edge(fromIndex, toIndex) < priority) setEdge(fromIndex, toIndex, priority);
		}
		
		public <T> void totalOrderPairs(List<Pair<String, T>> list, int priority) {
			if (!(list instanceof RandomAccess)) LOG.warn("OrderGraph.totalOrderPairs() received a list that doesn't support random access, slowness ahead");
			for (int i=0; i<list.size()-1; i++) {
				for (int j=i+1; j<list.size(); j++) {
					order(list.get(i).first, list.get(j).first, priority);
				}
			}
		}
		
		public void totalOrderNodeIds(List<String> list, int priority) {
			if (!(list instanceof RandomAccess)) LOG.warn("OrderGraph.totalOrderNodeIds() received a list that doesn't support random access, slowness ahead");
			for (int i=0; i<list.size()-1; i++) {
				for (int j=i+1; j<list.size(); j++) {
					order(list.get(i), list.get(j), priority);
				}
			}
		}
		
		int applyOrder() {
			calculateMaxes();
			Arrays.fill(placed, false);
			int numMoved = 0;
			for (int i = 0; i < numNodes - numMoved - 1;) {
				if (placed[i]) continue;
				int selected = -1;
				int min = minMax();
				if (maxes[i] > min) {
					for (int j = 0; j < numNodes; j++) {
						if (!placed[j] && maxes[j] <= min) {
							selected = j;
							break;
						}
					}
					assert selected != -1;
					target.query().run(
							"let $node1 := $_3/*[$_1], $node2 := $_3/*[$_2] " +
							"return (update insert $node1 preceding $node2, update delete $node1)",
							selected + numMoved + 1, i + numMoved + 1, target);
					numMoved++;
				} else {
					selected = i++;
				}
				placed[selected] = true;
				for (int j = 0; j < numNodes; j++) {
					if (edge(selected, j) > Integer.MIN_VALUE) calculateMaxIncomingPriority(j);
				}
			}
			return numMoved;
		}
		
		private int minMax() {
			int min = Integer.MAX_VALUE;
			for (int i = 0; i < numNodes; i++) if (!placed[i] && maxes[i] < min) min = maxes[i];
			return min;
		}
		
		private void calculateMaxes() {
			for (int toIndex = 0; toIndex < numNodes; toIndex++) calculateMaxIncomingPriority(toIndex);
		}

		private void calculateMaxIncomingPriority(int toIndex) {
			int max = Integer.MIN_VALUE;
			for (int fromIndex = 0; fromIndex < numNodes; fromIndex++) {
				if (placed[fromIndex]) continue;
				int priority = edge(fromIndex, toIndex);
				if (priority > max) max = priority;
			}
			maxes[toIndex] = max;
		}
		
		private int edge(int fromIndex, int toIndex) {
			return edges[fromIndex * numNodes + toIndex];
		}
		
		private void setEdge(int fromIndex, int toIndex, int priority) {
			edges[fromIndex * numNodes + toIndex] = priority;
		}

	}
	
	@Deprecated @DatabaseTestCase.ConfigFile("test/conf.xml")
	public static class _OrderGraphTest extends DatabaseTestCase {
		private Node target;
		private OrderGraph graph;
		
		@Before public void setup() {
			target = db.getFolder("/").documents().load(Name.generate(), Source.xml(
					"<container><child0 xml:id='c0'/><child1 xml:id='c1'/><child2 xml:id='c2'/></container>")).root();
			graph = new OrderGraph(target);
		}
		
		private void assertOrder(int numMoves, String... expectedIds) {
			assertEquals(numMoves, graph.applyOrder());
			ItemList children = target.query().all("*");
			ItemList actualIds = target.query().all("*/@xml:id");
			assertEquals("some children lost their ids: " + children, children.size(), actualIds.size());
			assertEquals("wrong number of children: " + children, expectedIds.length, children.size());
			for (int i=0; i<expectedIds.length; i++) {
				assertEquals(expectedIds[i], actualIds.get(i).value());
			}
		}
		
		@Test public void orderNodes1() {
			graph.order(target.query().single("/id('c1')").node(), target.query().single("/id('c2')").node(), 0);
			assertEquals(0, graph.edge(1, 2));
			assertEquals(Integer.MIN_VALUE, graph.edge(2, 1));
		}

		@Test public void orderNodes2() {
			graph.order(target.query().single("/id('c1')").node(), target.query().single("/id('c2')").node(), 5);
			graph.order(target.query().single("/id('c1')").node(), target.query().single("/id('c2')").node(), 2);
			assertEquals(5, graph.edge(1, 2));
			assertEquals(Integer.MIN_VALUE, graph.edge(2, 1));
		}

		@Test public void orderNodes3() {
			graph.order(target.query().single("/id('c1')").node(), target.query().single("/id('c2')").node(), 5);
			graph.order(target.query().single("/id('c2')").node(), target.query().single("/id('c1')").node(), 2);
			assertEquals(5, graph.edge(1, 2));
			assertEquals(2, graph.edge(2, 1));
		}
		
		@Test public void calculateMaxIncomingPriority1() {
			graph.order("c0", "c1", 2);
			graph.order("c2", "c1", 5);
			graph.calculateMaxIncomingPriority(1);
			assertEquals(5, graph.maxes[1]);
		}

		@Test public void calculateMaxIncomingPriority2() {
			graph.order("c0", "c1", 2);
			graph.order("c2", "c1", 5);
			graph.placed[2] = true;
			graph.calculateMaxIncomingPriority(1);
			assertEquals(2, graph.maxes[1]);
		}
		
		@Test public void calculateMaxes1() {
			graph.order("c0", "c1", 2);
			graph.order("c2", "c1", 5);
			graph.order("c1", "c0", 8);
			graph.order("c1", "c0", 10);
			graph.calculateMaxes();
			assertEquals(10, graph.maxes[0]);
			assertEquals(5, graph.maxes[1]);
			assertEquals(Integer.MIN_VALUE, graph.maxes[2]);
			assertEquals(Integer.MIN_VALUE, graph.minMax());
		}
		
		@Test public void calculateMaxes2() {
			graph.order("c0", "c1", 2);
			graph.order("c2", "c1", 5);
			graph.order("c1", "c0", 8);
			graph.order("c1", "c0", 10);
			graph.order("c0", "c2", 3);
			graph.calculateMaxes();
			assertEquals(10, graph.maxes[0]);
			assertEquals(5, graph.maxes[1]);
			assertEquals(3, graph.maxes[2]);
			assertEquals(3, graph.minMax());
		}

		@Test public void calculateMaxes3() {
			graph.order("c0", "c1", 5);
			graph.order("c1", "c0", 8);
			graph.order("c1", "c0", 10);
			graph.order("c0", "c2", 3);
			graph.placed[2] = true;
			graph.calculateMaxes();
			assertEquals(10, graph.maxes[0]);
			assertEquals(5, graph.maxes[1]);
			assertEquals(3, graph.maxes[2]);
			assertEquals(5, graph.minMax());
		}
				
		@Test public void applyOrderEmptyDoesNothing() {
			assertOrder(0, "c0", "c1", "c2");
		}
		
		@Test public void applyOrderConsistentDoesNothing() {
			graph.order("c0", "c1", 0);
			graph.order("c1", "c2", 0);
			assertOrder(0, "c0", "c1", "c2");
		}

		@Test public void applyOrderSwitchOne() {
			graph.order("c2", "c1", 0);
			assertOrder(1, "c0", "c2", "c1");
		}

		@Test public void applyOrderSwitchTwo() {
			graph.order("c2", "c1", 0);
			graph.order("c1", "c0", 0);
			assertOrder(2, "c2", "c1", "c0");
		}

		@Test public void applyOrderResolveConflict() {
			graph.order("c2", "c1", 1);
			graph.order("c1", "c2", 0);
			assertOrder(1, "c0", "c2", "c1");
		}

		@Test public void applyOrderBreakCycle() {
			graph.order("c1", "c0", 1);
			graph.order("c2", "c1", 2);
			graph.order("c0", "c2", 3);
			assertOrder(1, "c0", "c2", "c1");
		}

	}

}
