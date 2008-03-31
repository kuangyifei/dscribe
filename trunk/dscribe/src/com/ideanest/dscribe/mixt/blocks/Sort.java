package com.ideanest.dscribe.mixt.blocks;
import static com.ideanest.dscribe.testutil.Matchers.collection;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.util.*;

import org.exist.fluent.*;
import org.jmock.*;
import org.junit.Test;

import com.ideanest.dscribe.mixt.*;
import com.ideanest.dscribe.mixt.Mod.Builder;
import com.ideanest.dscribe.mixt.SortController.OrderGraph;
import com.ideanest.dscribe.testutil.BlockTestCase;

public class Sort implements BlockType {

	@Override public String version() {
		return "1";
	}

	@Override public QName xmlName() {
		return new QName(Engine.RULES_NS, "sort", null);
	}

	@AllowAttributes({"priority", "by", "as", "before", "after"})
	@Override public Block define(Node def) throws RuleBaseException {
		int numDirectives = def.query().single("count(@by | @as | @before | @after)").intValue();
		if (numDirectives > 1) throw new RuleBaseException("sort block specified with more than one of @by, @as, @before and @after");
		if (numDirectives < 1) throw new RuleBaseException("sort block specified without any @by, @as, @before or @after");
		if (def.query().exists("@by")) return new SortByValueBlock(def);
		else if (def.query().exists("@as")) return new SortByProxyBlock(def);
		else return new SortBySiblingBlock(def);
	}
	
	private static abstract class SortBlock implements LinearBlock {
		final int priority;
		final Query.Items query;
		private Collection<String> requiredVariables;
		
		SortBlock(Node def) throws RuleBaseException {
			Item priorityItem = def.query().optional("@priority");
			try {
				priority = priorityItem.extant() ? priorityItem.intValue() : 0;
			} catch (DatabaseException e) {
				throw new RuleBaseException("sort block specified bad priority '" + priorityItem.value() + "'", e);
			}
			query = new Query.Items(def);
		}
		
		public void resolve(Mod.Builder modBuilder) throws TransformException {
			for (Node node : modBuilder.dependOnNearest(NodeTarget.class).unverified().get().targets().nodes()) {
				modBuilder.order(node);
				resolveOrder(modBuilder, node);
			}
			modBuilder.dependOn(requiredVariables);
			modBuilder.commit();
		}
		
		abstract void resolveOrder(Mod.Builder modBuilder, Node node) throws TransformException;
		
		protected <T> void totalOrder(List<Pair<String, T>> items, Comparator<Pair<String, T>> comparator, SortController.OrderGraph graph) {
			Collections.sort(items, comparator);
			int size = items.size();
			for (int i = 0; i < size - 1; i++) {
				Pair<String, T> item1 = items.get(i);
				for (int j = i + 1; j < size; j++) {
					Pair<String, T> item2 = items.get(j);
					int pairOrder = comparator.compare(item1, item2);
					assert pairOrder <= 0;
					if (pairOrder != 0) graph.order(item1.first, item2.first, priority);
				}
			}
		}
		
		private class SortSeg extends Seg {
			SortSeg(Mod mod) {super(mod);}
			@Override public void analyze() throws TransformException {
				requiredVariables = query.analyze(mod.globalScope()).requiredVariables();
			}
		}
	}
	
	private static class SortByValueBlock extends SortBlock implements SortingBlock<SortByValueBlock.SortByValueSeg> {
		private final boolean ascending;
		
		SortByValueBlock(Node def) throws RuleBaseException {
			super(def);
			// TODO: support collations
			String direction = def.query().single("@by").value();
			if ("ascending".equals(direction)) ascending = true;
			else if ("descending".equals(direction)) ascending = false;
			else throw new RuleBaseException("sort block @by must have value 'ascending' or 'descending', got '" + direction + "'");
		}
		
		@Override void resolveOrder(Mod.Builder modBuilder, Node node) throws TransformException {
			ItemList items = query.runOn(modBuilder.scopeWithVariablesBound(node.query()));
			if (items.size() != 1) throw new TransformException("sort by value must select one value per target, but instead selected " + items.size() + ": " + items);
			modBuilder.dependOn(node.document());
			modBuilder.supplement()
				.elem("sort-value").attr("refid", node.query().single("@xml:id").value())
					.text(items.get(0).value())
				.end("sort-value");
		}
		
		private static final Comparator<Pair<String, Item>> ASCENDING_VALUE_COMPARATOR = new Comparator<Pair<String, Item>>() {
			@Override public int compare(Pair<String, Item> o1, Pair<String, Item> o2) {
				return o1.second.comparableValue().compareTo(o2.second.comparableValue());
			}
		};
		
		private static final Comparator<Pair<String, Item>> DESCENDING_VALUE_COMPARATOR = new Comparator<Pair<String, Item>>() {
			@Override public int compare(Pair<String, Item> o1, Pair<String, Item> o2) {
				return -o1.second.comparableValue().compareTo(o2.second.comparableValue());
			}
		};
		
		public void sort(Collection<SortByValueSeg> segs, OrderGraph graph) {
			List<Pair<String, Item>> entryList = new ArrayList<Pair<String, Item>>();
			for (SortByValueSeg seg : segs) entryList.addAll(seg.values);
			totalOrder(entryList, ascending ? ASCENDING_VALUE_COMPARATOR : DESCENDING_VALUE_COMPARATOR, graph);
		}
		
		public Seg createSeg(Mod mod) {
			return new SortByValueSeg(mod);
		}
		
		private class SortByValueSeg extends SortBlock.SortSeg {
			private List<Pair<String, Item>> values;
			SortByValueSeg(Mod mod) {super(mod);}
			
			@Override public void restore() throws TransformException {
				ItemList targets = mod.nearest(NodeTarget.class).targets();
				values = new ArrayList<Pair<String, Item>>(targets.size());
				for (Node node : targets.nodes()) {
					ItemList items = query.runOn(mod.scope(node.query()));
					if (items.size() != 1) throw new TransformException("sort by value query did not select exactly one item: " + items);
					values.add(Pair.of(node.query().single("@xml:id").value(), items.get(0).toAtomicItem()));
				}
			}
			
			@Override public void verify() throws TransformException {
				for (Pair<String, Item> entry : values) {
					String storedValue = mod.supplementQuery().optional("sort-value[@refid=$_1]", entry.first).value();
					if (!entry.second.value().equals(storedValue))
						throw new TransformException("value mismatch: " + storedValue + " vs " + entry.second.value());
				}
			}
		}
		
	}
	
	private static class SortByProxyBlock extends SortBlock implements SortingBlock<SortByProxyBlock.SortByProxySeg> {
		SortByProxyBlock(Node def) throws RuleBaseException {
			super(def);
			if (!"corresponding".equals(def.query().single("@as").value()))
				throw new RuleBaseException("sort block @as must have value 'corresponding'");
		}

		@Override void resolveOrder(Mod.Builder modBuilder, Node node) throws TransformException {
			ItemList items = query.runOn(modBuilder.scopeWithVariablesBound(node.query()));
			if (items.size() != 1) throw new TransformException("sort by corresponding node must select one node per target, but instead selected " + items.size() + ": " + items);
			Item item = items.get(0);
			modBuilder.reference(item.node());
			modBuilder.dependOn(node.document());
			modBuilder.supplement()
				.elem("sort-proxy")
					.attr("refid", node.query().single("@xml:id").value())
					.attr("proxyid", item.query().single("@xml:id").value())
					.attr("proxyparentid", item.query().single("../@xml:id").value())
					.attr("position", item.query().single("count(preceding-sibling::*)").value())
				.end("sort-proxy");
		}
		
		private static final Comparator<Pair<String, Node>> NODE_ORDER_COMPARATOR = new Comparator<Pair<String, Node>>() {
			@Override public int compare(Pair<String, Node> o1, Pair<String, Node> o2) {
				return o1.second.compareDocumentOrderTo(o2.second);
			}
		};
		
		public void sort(Collection<SortByProxySeg> segs, OrderGraph graph) {
			Map<String, List<Pair<String, Node>>> docMap = new TreeMap<String, List<Pair<String, Node>>>();
			for (SortByProxySeg seg : segs) {
				for (Pair<String, Node> pair : seg.proxies) {
					String path = pair.second.document().path();
					List<Pair<String, Node>> list = docMap.get(path);
					if (list == null) {
						list = new ArrayList<Pair<String, Node>>();
						docMap.put(path, list);
					}
					list.add(pair);
				}
			}
			for (List<Pair<String, Node>> list : docMap.values()) {
				totalOrder(list, NODE_ORDER_COMPARATOR, graph);
			}
		}

		public Seg createSeg(Mod mod) {
			return new SortByProxySeg(mod);
		}
		
		private class SortByProxySeg extends SortBlock.SortSeg {
			private List<Pair<String, Node>> proxies;
			SortByProxySeg(Mod mod) {super(mod);}
			
			@Override public void restore() throws TransformException {
				List<String> refids = mod.supplementQuery().all("sort-proxy/@refid").values().asList();
				proxies = new ArrayList<Pair<String, Node>>(refids.size());
				int i=0;
				for (Node proxy : mod.references()) proxies.add(Pair.of(refids.get(i++), proxy));
			}
			
			@Override public void verify() throws TransformException {
				for (Node proxy : mod.nearest(NodeTarget.class).targets().nodes()) {
					if (!mod.supplementQuery().single(
							"let $record := sort-proxy[@proxyid=$_1/@xml:id] " +
							"return xs:integer($record/@position) eq count($_1/preceding-sibling::*) " +
							"	and $record/@proxyparentid eq $_1/../@xml:id", proxy).booleanValue())
						throw new TransformException("proxy node moved");
				}
			}
		}
	}
	
	private static class SortBySiblingBlock extends SortBlock implements SortingBlock<SortBySiblingBlock.SortBySiblingSeg> {
		private final boolean before;
		SortBySiblingBlock(Node def) throws RuleBaseException {
			super(def);
			before = def.query().exists("@before");
			String attrName = before ? "@before" : "@after";
			if (!"sibling".equals(def.query().single(attrName).value()))
				throw new RuleBaseException("sort block " + attrName + " must have value 'sibling'");
		}
		
		@Override void resolveOrder(Mod.Builder modBuilder, Node node) {
			ItemList siblings = query.runOn(modBuilder.scopeWithVariablesBound(node.query()));
			ElementBuilder<?> builder = modBuilder.supplement();
			builder.elem("sibling-list").attr("refid", node.query().single("@xml:id").value());
			for (String siblingId : siblings.query().all("@xml:id").values()) {
				builder.elem("sibling").attr("refid", siblingId).end("sibling");
			}
			builder.end("sibling-list");
		}
		
		public void sort(Collection<SortBySiblingSeg> segs, OrderGraph graph) {
			for (SortBySiblingSeg seg : segs) {
				for (Node siblingList : seg.mod.supplementQuery().all("sibling-list").nodes()) {
					String targetId = siblingList.query().single("@refid").value();
					for (String siblingId : siblingList.query().all("sibling/@refid").values()) {
						if (before) {
							graph.order(targetId, siblingId, priority);
						} else {
							graph.order(siblingId, targetId, priority);
						}
					}
				}
			}
		}
		
		public Seg createSeg(Mod mod) {
			return new SortBySiblingSeg(mod);
		}
		
		private class SortBySiblingSeg extends SortBlock.SortSeg {
			SortBySiblingSeg(Mod mod) {super(mod);}
			
			@Override public void verify() throws TransformException {
				for (Node target : mod.nearest(NodeTarget.class).targets().nodes()) {
					List<String> actualSiblingIds = query.runOn(mod.scope(target.query())).query().all("@xml:id").values().asList();
					List<String> storedSiblingIds = mod.supplementQuery().all("sibling-list[@refid=$_1/@xml:id]/sibling/@refid", target).values().asList();
					if (!actualSiblingIds.equals(storedSiblingIds))
						throw new TransformException("sibling list changed for target " + target.query().single("@xml:id").value());
				}
			}
		}
	}

	@Deprecated public static class _SortBlockTest extends BlockTestCase {
		@Test(expected = RuleBaseException.class)
		public void parse1() throws RuleBaseException {
			define("<sort>@foo</sort>");
		}

		@Test(expected = RuleBaseException.class)
		public void parse2() throws RuleBaseException {
			define("<sort by='ascending' as='corresponding'>@foo</sort>");
		}
		
		@Test public void parse3() throws RuleBaseException {
			SortBlock block = define("<sort by='ascending' priority='5'>@foo</sort>");
			assertEquals(5, block.priority);
		}

		@Test public void parse4() throws RuleBaseException {
			SortBlock block = define("<sort by='ascending' priority='-100'>@foo</sort>");
			assertEquals(-100, block.priority);
		}

		@Test public void totalOrder() throws RuleBaseException {
			SortBlock block = define("<sort by='ascending' priority='5'>@foo</sort>");
			final SortController.OrderGraph graph = mockery.mock(SortController.OrderGraph.class);
			mockery.checking(new Expectations() {{
				one(graph).order("x2", "x1", 5);
				one(graph).order("x2", "x3", 5);
				one(graph).order("x2", "x4", 5);
				one(graph).order("x1", "x4", 5);
				one(graph).order("x3", "x4", 5);
			}});
			List<Pair<String, Integer>> list = new ArrayList<Pair<String, Integer>>();
			list.add(Pair.of("x1", 23));
			list.add(Pair.of("x2", 18));
			list.add(Pair.of("x3", 23));
			list.add(Pair.of("x4", 35));
			Comparator<Pair<String, Integer>> comparator = new Comparator<Pair<String, Integer>>() {
				public int compare(Pair<String, Integer> o1, Pair<String, Integer> o2) {
					return o1.second.compareTo(o2.second);
				}
			};
			block.totalOrder(list, comparator, graph);
		}
		
		@Test public void resolve() throws RuleBaseException, TransformException {
			final Node m1 = content.query().single("/id('m1')").node();
			final Node m2 = content.query().single("/id('m2')").node();
			final SortBlock mockBlock = mockery.mock(SortBlock.class);
			SortBlock block = new SortBlock(db.getFolder("/").documents().load(Name.create("rule"), Source.xml(
					"<rule><sort by='ascending'>@foo</sort></rule>")).root().query().single("*").node()) {
				@Override void resolveOrder(Builder aModBuilder, Node node) throws TransformException {
					mockBlock.resolveOrder(aModBuilder, node);
				}
				@Override public Seg createSeg(Mod unused) {
					fail();
					return null;
				}
			};
			modBuilder = mockery.mock(Mod.Builder.class);
			mockery.checking(new Expectations() {{
				Sequence seq1 = mockery.sequence("pre-commit resolveOrder 1");
				Sequence seq2 = mockery.sequence("pre-commit resolveOrder 2");
				one(mockBlock).resolveOrder(modBuilder, m1); inSequence(seq1);
				one(mockBlock).resolveOrder(modBuilder, m2); inSequence(seq2);
				modBuilderPriors.add(seq1);  modBuilderPriors.add(seq2);
			}});
			block.requiredVariables = Arrays.asList(new String[] {"$a", "$b"});
			dependOnNearest(NodeTarget.class, false, new NodeTarget() {
				public ItemList targets() throws TransformException {
					return content.query().all("/id('m1 m2')");
				}
			});
			order("m1");
			order("m2");
			dependOnVariables("$a", "$b");
			thenCommit();
			block.resolve(modBuilder);
		}
		
		@Test public void analyze() throws RuleBaseException, TransformException {
			SortBlock block = define("<sort as='corresponding'>$src</sort>");
			setModGlobalScope(content.query());
			Seg seg = block.createSeg(mod);
			seg.analyze();
			assertThat(block.requiredVariables, is(collection("$src")));
		}
	}
	
	@Deprecated public static class _SortByValueTest extends BlockTestCase {
		@Test public void parse1() throws RuleBaseException {
			SortByValueBlock block = define("<sort by='ascending'>@foo</sort>");
			assertTrue(block.ascending);
			assertEquals(0, block.priority);
		}

		@Test public void parse2() throws RuleBaseException {
			SortByValueBlock block = define("<sort by='descending'>@foo</sort>");
			assertFalse(block.ascending);
			assertEquals(0, block.priority);
		}

		@Test(expected = RuleBaseException.class)
		public void parse3() throws RuleBaseException {
			define("<sort by='foo'>@foo</sort>");
		}

		@Test(expected = RuleBaseException.class)
		public void parse4() throws RuleBaseException {
			define("<sort by='ascending' priority='x'>@foo</sort>");
		}
		
		@Test public void resolveOrder() throws RuleBaseException, TransformException {
			SortBlock block = define("<sort by='ascending'>@name</sort>");
			Node m1 = content.query().single("/id('m1')").node();
			setModBuilderScopeWithVariablesBound(m1.query());
			dependOnDocument(m1.document());
			supplement();
			block.resolveOrder(modBuilder, m1);
			checkSupplement("<sort-value refid='m1'>start</sort-value>");
		}
		
		@Test(expected = TransformException.class)
		public void resolveOrderBadQuery() throws RuleBaseException, TransformException {
			SortBlock block = define("<sort by='ascending'>*</sort>");
			Node c1 = content.query().single("/id('c1')").node();
			setModBuilderScopeWithVariablesBound(c1.query());
			block.resolveOrder(modBuilder, c1);
		}
		
		@Test public void restore() throws RuleBaseException, TransformException {
			setModNearestAncestorImplementing(NodeTarget.class, new NodeTarget() {
				public ItemList targets() throws TransformException {
					return content.query().all("/id('m1 m2')");
				}
			});
			setModScope(content.query().single("/id('m1')").node().query(), content.query().single("/id('m2')").node().query());
			SortByValueBlock block = define("<sort by='ascending'>@name</sort>");
			SortByValueBlock.SortByValueSeg seg = (SortByValueBlock.SortByValueSeg) block.createSeg(mod);
			seg.restore();
			assertEquals(
					Arrays.asList(new Pair[] {
							Pair.of("m1", content.query().single("/id('m1')/@name").toAtomicItem()),
							Pair.of("m2", content.query().single("/id('m2')/@name").toAtomicItem())}),
					seg.values);
		}
		
		@Test(expected = TransformException.class)
		public void restoreBadQuery() throws RuleBaseException, TransformException {
			setModNearestAncestorImplementing(NodeTarget.class, new NodeTarget() {
				public ItemList targets() throws TransformException {
					return content.query().all("/id('c1')");
				}
			});
			setModScope(content.query().single("/id('c1')").node().query());
			SortByValueBlock block = define("<sort by='ascending'>*</sort>");
			SortByValueBlock.SortByValueSeg seg = (SortByValueBlock.SortByValueSeg) block.createSeg(mod);
			seg.restore();
		}
		
		@Test public void verify() throws RuleBaseException, TransformException {
			SortByValueBlock block = define("<sort by='ascending'>@name</sort>");
			SortByValueBlock.SortByValueSeg seg = (SortByValueBlock.SortByValueSeg) block.createSeg(mod);
			setModData("(<sort-value refid='m1'>start</sort-value>, <sort-value refid='m2'>end</sort-value>)");
			seg.values = new ArrayList<Pair<String, Item>>();
			seg.values.add(Pair.of("m1", content.query().single("/id('m1')/@name").toAtomicItem()));
			seg.values.add(Pair.of("m2", content.query().single("/id('m2')/@name").toAtomicItem()));
			seg.verify();
		}

		@Test(expected = TransformException.class)
		public void verifyBad() throws RuleBaseException, TransformException {
			SortByValueBlock block = define("<sort by='ascending'>@name</sort>");
			SortByValueBlock.SortByValueSeg seg = (SortByValueBlock.SortByValueSeg) block.createSeg(mod);
			setModData("(<sort-value refid='m1'>start</sort-value>, <sort-value refid='m2'>foo</sort-value>)");
			seg.values = new ArrayList<Pair<String, Item>>();
			seg.values.add(Pair.of("m1", content.query().single("/id('m1')/@name").toAtomicItem()));
			seg.values.add(Pair.of("m2", content.query().single("/id('m2')/@name").toAtomicItem()));
			seg.verify();
		}
		
		@Test public void sortAscending() throws RuleBaseException {
			SortByValueBlock block = define("<sort by='ascending'>@name</sort>");
			SortByValueBlock.SortByValueSeg seg1 = (SortByValueBlock.SortByValueSeg) block.createSeg(mod);
			SortByValueBlock.SortByValueSeg seg2 = (SortByValueBlock.SortByValueSeg) block.createSeg(mod);
			seg1.values = new ArrayList<Pair<String, Item>>();
			seg1.values.add(Pair.of("m1", content.query().single("/id('m1')/@name").toAtomicItem()));
			seg2.values = new ArrayList<Pair<String, Item>>();
			seg2.values.add(Pair.of("m2", content.query().single("/id('m2')/@name").toAtomicItem()));
			final SortController.OrderGraph graph = mockery.mock(SortController.OrderGraph.class);
			mockery.checking(new Expectations() {{
				one(graph).order("m2", "m1", 0);
			}});
			block.sort(Arrays.asList(seg1, seg2), graph);
		}

		@Test public void sortDescending() throws RuleBaseException {
			SortByValueBlock block = define("<sort by='descending'>@name</sort>");
			SortByValueBlock.SortByValueSeg seg1 = (SortByValueBlock.SortByValueSeg) block.createSeg(mod);
			SortByValueBlock.SortByValueSeg seg2 = (SortByValueBlock.SortByValueSeg) block.createSeg(mod);
			seg1.values = new ArrayList<Pair<String, Item>>();
			seg1.values.add(Pair.of("m1", content.query().single("/id('m1')/@name").toAtomicItem()));
			seg2.values = new ArrayList<Pair<String, Item>>();
			seg2.values.add(Pair.of("m2", content.query().single("/id('m2')/@name").toAtomicItem()));
			final SortController.OrderGraph graph = mockery.mock(SortController.OrderGraph.class);
			mockery.checking(new Expectations() {{
				one(graph).order("m1", "m2", 0);
			}});
			block.sort(Arrays.asList(seg1, seg2), graph);
		}
	}
	
	@Deprecated public static class _SortByProxyTest extends BlockTestCase {
		@Test public void parse1() throws RuleBaseException {
			SortByProxyBlock block = define("<sort as='corresponding'>$source</sort>");
			assertEquals(0, block.priority);
		}

		@Test(expected = RuleBaseException.class)
		public void parse2() throws RuleBaseException {
			define("<sort as='foo'>@foo</sort>");
		}
		
		@Test public void resolveOrder() throws RuleBaseException, TransformException {
			SortBlock block = define("<sort as='corresponding'>$source</sort>");
			Node um1 = content.query().single("/id('um1')").node();
			Node m1 = content.query().single("/id('m1')").node();
			setModBuilderScopeWithVariablesBound(um1.query().let("$source", m1));
			reference(m1);
			dependOnDocument(um1.document());
			supplement();
			block.resolveOrder(modBuilder, um1);
			checkSupplement("<sort-proxy refid='um1' proxyid='m1' proxyparentid='c1' position='0'/>");
		}
		
		@Test(expected = TransformException.class)
		public void resolveOrderBadQuery() throws RuleBaseException, TransformException {
			SortBlock block = define("<sort by='ascending'>*</sort>");
			Node uc1 = content.query().single("/id('uc1')").node();
			setModBuilderScopeWithVariablesBound(uc1.query());
			block.resolveOrder(modBuilder, uc1);
		}
		
		@Test public void restore() throws RuleBaseException, TransformException {
			setModData("(<sort-proxy refid='um1' proxyid='m1' proxyparentid='c1' position='0'/>, <sort-proxy refid='um2' proxyid='m2' proxyparentid='c1' position='1'/>)");
			setModReferences(content.query().single("/id('m1')").node(), content.query().single("/id('m2')").node());
			SortByProxyBlock block = define("<sort as='corresponding'>$source</sort>");
			SortByProxyBlock.SortByProxySeg seg = (SortByProxyBlock.SortByProxySeg) block.createSeg(mod);
			seg.restore();
			assertEquals(
					Arrays.asList(new Pair[] {
							Pair.of("um1", content.query().single("/id('m1')").node()),
							Pair.of("um2", content.query().single("/id('m2')").node())}),
					seg.proxies);
		}
		
		private void runVerifyScenario(String modData) throws TransformException, RuleBaseException {
			setModNearestAncestorImplementing(NodeTarget.class, new NodeTarget() {
				public ItemList targets() throws TransformException {
					return content.query().all("/id('um1 um2')");
				}
			});
			setModData(modData);
			SortByProxyBlock block = define("<sort as='corresponding'>$source</sort>");
			SortByProxyBlock.SortByProxySeg seg = (SortByProxyBlock.SortByProxySeg) block.createSeg(mod);
			seg.proxies = new ArrayList<Pair<String, Node>>();
			seg.proxies.add(Pair.of("um1", content.query().single("/id('m1')").node()));
			seg.proxies.add(Pair.of("um2", content.query().single("/id('m2')").node()));
			seg.verify();
		}
		
		@Test public void verify() throws RuleBaseException, TransformException {
			runVerifyScenario("(<sort-proxy refid='um1' proxyid='m1' proxyparentid='c1' position='0'/>, <sort-proxy refid='um2' proxyid='m2' proxyparentid='c1' position='1'/>)");
		}

		@Test(expected = TransformException.class)
		public void verifyBadPosition() throws RuleBaseException, TransformException {
			runVerifyScenario("(<sort-proxy refid='um1' proxyid='m1' proxyparentid='c1' position='1'/>, <sort-proxy refid='um2' proxyid='m2' proxyparentid='c1' position='1'/>)");
		}
		
		@Test(expected = TransformException.class)
		public void verifyBadParent() throws RuleBaseException, TransformException {
			runVerifyScenario("(<sort-proxy refid='um1' proxyid='m1' proxyparentid='c1' position='0'/>, <sort-proxy refid='um2' proxyid='m2' proxyparentid='m1' position='1'/>)");
		}
		
//		@Test public void sortAscending() throws RuleBaseException {
//			SortByValueBlock block = define("<sort by='ascending'>@name</sort>");
//			SortByValueBlock.SortByValueSeg seg1 = (SortByValueBlock.SortByValueSeg) block.createSeg(mod);
//			SortByValueBlock.SortByValueSeg seg2 = (SortByValueBlock.SortByValueSeg) block.createSeg(mod);
//			seg1.values = new ArrayList<Pair<String, Item>>();
//			seg1.values.add(Pair.of("m1", content.query().single("/id('m1')/@name").toAtomicItem()));
//			seg2.values = new ArrayList<Pair<String, Item>>();
//			seg2.values.add(Pair.of("m2", content.query().single("/id('m2')/@name").toAtomicItem()));
//			final SortController.OrderGraph graph = mockery.mock(SortController.OrderGraph.class);
//			mockery.checking(new Expectations() {{
//				one(graph).order("m2", "m1", 0);
//			}});
//			block.sort(Arrays.asList(seg1, seg2), graph);
//		}
//
//		@Test public void sortDescending() throws RuleBaseException {
//			SortByValueBlock block = define("<sort by='descending'>@name</sort>");
//			SortByValueBlock.SortByValueSeg seg1 = (SortByValueBlock.SortByValueSeg) block.createSeg(mod);
//			SortByValueBlock.SortByValueSeg seg2 = (SortByValueBlock.SortByValueSeg) block.createSeg(mod);
//			seg1.values = new ArrayList<Pair<String, Item>>();
//			seg1.values.add(Pair.of("m1", content.query().single("/id('m1')/@name").toAtomicItem()));
//			seg2.values = new ArrayList<Pair<String, Item>>();
//			seg2.values.add(Pair.of("m2", content.query().single("/id('m2')/@name").toAtomicItem()));
//			final SortController.OrderGraph graph = mockery.mock(SortController.OrderGraph.class);
//			mockery.checking(new Expectations() {{
//				one(graph).order("m1", "m2", 0);
//			}});
//			block.sort(Arrays.asList(seg1, seg2), graph);
//		}
	}
}
