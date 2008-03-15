package com.ideanest.dscribe.mixt.blocks;

import java.util.*;

import org.exist.fluent.*;

import com.ideanest.dscribe.mixt.*;
import com.ideanest.dscribe.mixt.SortController.OrderGraph;

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
		
		@Override public void resolve(Mod.Builder modBuilder) throws TransformException {
			for (Node node : modBuilder.nearestAncestorImplementing(NodeTarget.class).targets().nodes()) {
				modBuilder.order(node);
				resolveOrder(modBuilder, node);
			}
			modBuilder.dependOn(requiredVariables);
			modBuilder.commit();
		}
		
		abstract void resolveOrder(Mod.Builder modBuilder, Node node) throws TransformException;
		
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
		
		private static final Comparator<Pair<String, Item>> VALUE_COMPARATOR = new Comparator<Pair<String, Item>>() {
			@Override public int compare(Pair<String, Item> o1, Pair<String, Item> o2) {
				return o1.second.comparableValue().compareTo(o2.second.comparableValue());
			}
		};
		
		private static final Comparator<Pair<String, Item>> REVERSE_VALUE_COMPARATOR = new Comparator<Pair<String, Item>>() {
			@Override public int compare(Pair<String, Item> o1, Pair<String, Item> o2) {
				return -o1.second.comparableValue().compareTo(o2.second.comparableValue());
			}
		};
		
		public void sort(Collection<SortByValueSeg> segs, OrderGraph graph) {
			List<Pair<String, Item>> entryList = new ArrayList<Pair<String, Item>>();
			for (SortByValueSeg seg : segs) entryList.addAll(seg.values);
			Collections.sort(entryList, ascending ? VALUE_COMPARATOR : REVERSE_VALUE_COMPARATOR);
			graph.totalOrderPairs(entryList, priority);
		}
		
		@Override public Seg createSeg(Mod mod) {
			return new SortByValueSeg(mod);
		}
		
		private class SortByValueSeg extends SortBlock.SortSeg {
			private List<Pair<String, Item>> values;
			SortByValueSeg(Mod mod) {super(mod);}
			
			@Override public void restore() throws TransformException {
				ItemList targets = mod.nearestAncestorImplementing(NodeTarget.class).targets();
				values = new ArrayList<Pair<String, Item>>(targets.size());
				for (Node node : targets.nodes()) {
					ItemList items = query.runOn(mod.scope(node.query()));
					if (items.size() != 1) throw new TransformException("sort by value query did not select exactly one item: " + items);
					values.add(Pair.of(node.query().single("@xml:id").value(), items.get(0)));
				}
			}
			
			@Override public void verify() throws TransformException {
				for (Pair<String, Item> entry : values) {
					String storedValue = mod.node().query().optional("sort-value[@refid=$_1]", entry.first).value();
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
				Collections.sort(list, NODE_ORDER_COMPARATOR);
				graph.totalOrderPairs(list, priority);
			}
		}

		@Override public Seg createSeg(Mod mod) {
			return new SortByProxySeg(mod);
		}
		
		private class SortByProxySeg extends SortBlock.SortSeg {
			private List<Pair<String, Node>> proxies;
			SortByProxySeg(Mod mod) {super(mod);}
			
			@Override public void restore() throws TransformException {
				List<String> refids = mod.node().query().all("sort-proxy/@refid").values().asList();
				proxies = new ArrayList<Pair<String, Node>>(refids.size());
				int i=0;
				for (Node proxy : mod.references()) proxies.add(Pair.of(refids.get(i++), proxy));
			}
			
			@Override public void verify() throws TransformException {
				for (Node node : mod.nearestAncestorImplementing(NodeTarget.class).targets().nodes()) {
					if (!mod.node().query().single(
							"let $record := sort-proxy[@proxyid=$_1/@xml:id] " +
							"return xs:integer($record/@position) eq count($_1/preceding-sibling::*) " +
							"	and $record/@proxyparentid eq $_1/../@xml:id", node).booleanValue())
						throw new TransformException("proxy node moved");
				}
			}
		}
	}
	
	private static class SortBySiblingBlock extends SortBlock {
		private final boolean before;
		SortBySiblingBlock(Node def) throws RuleBaseException {
			super(def);
			before = def.query().exists("@before");
			String attrName = before ? "@before" : "@after";
			if (!"sibling".equals(def.query().single(attrName).value()))
				throw new RuleBaseException("sort block " + attrName + " must have value 'sibling'");
		}
		
		@Override void resolveOrder(Mod.Builder modBuilder, Node node) {
			
		}
		
		@Override public Seg createSeg(Mod mod) {
			return new SortBySiblingSeg(mod);
		}
		
		private class SortBySiblingSeg extends SortBlock.SortSeg {
			SortBySiblingSeg(Mod mod) {super(mod);}

			@Override public void restore() throws TransformException {
				
			}
			
			@Override public void verify() throws TransformException {
				
			}
		}
	}

}
