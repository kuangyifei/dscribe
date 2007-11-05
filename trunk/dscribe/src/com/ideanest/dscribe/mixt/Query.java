package com.ideanest.dscribe.mixt;

import java.util.Iterator;

import org.exist.fluent.*;


public abstract class Query {
	private final String query;
	private final NamespaceMap namespaceMap = new NamespaceMap();
	
	private Query(Node def, String query) {
		this.query = query;
		for (Iterator<String> it = def.query().all(
				"for $prefix in in-scope-prefixes($_1) return ($prefix, namespace-uri-for-prefix($prefix, $_1))", def).values().iterator(); it.hasNext(); ) {
			namespaceMap.put(it.next(), it.next());
		}
	}
	
	protected final QueryService prep(QueryService qs) {
		qs.namespaceBindings().replaceWith(namespaceMap);
		return qs;
	}
	
	private ItemList execute(QueryService qs) {
		return prep(qs).all(query);
	}
	
	private boolean exists(QueryService qs) {
		return prep(qs).exists(query);
	}
	
	public QueryService.QueryAnalysis analyze(QueryService qs) {
		return prep(qs).analyze(query);
	}
	
	private static String serializeContents(Node node) {
		StringBuilder buf = new StringBuilder();
		for (Item item : node.query().all("node()")) buf.append(item);
		return buf.toString();
	}

	@Override public boolean equals(Object o) {
		if (getClass() != o.getClass()) return false;
		Query that = (Query) o;
		return this.query.equals(that.query) && this.namespaceMap.equals(that.namespaceMap);
	}
	
	@Override public int hashCode() {
		return query.hashCode();	// it's unlikely two queries will only differ by namespaces, and this is cheaper
	}

	public static class Items extends Query {
		public Items(Node def) {
			super(def, serializeContents(def));
		}
		
		public ItemList runOn(QueryService qs) {
			return super.execute(qs);
		}
		
		public boolean runExists(QueryService qs) {
			return super.exists(qs);
		}
	}

	public static class Text extends Query {
		public Text(Node def) {
			super(def, "<text>" + serializeContents(def) + "</text>");
		}
		
		public String runOn(QueryService qs) {
			return super.execute(qs).get(0).value();
		}
	}
	
	@Deprecated @SuppressWarnings("deprecation") @DatabaseTestCase.ConfigFile("test/conf.xml")
	public static class Test extends DatabaseTestCase {
		public void testSerializeContents1() {
			assertEquals("test", serializeContents(db.getFolder("/").query().single("<root>test</root>").node()));
		}
		public void testSerializeContents2() {
			assertEquals("<child>test</child>", serializeContents(db.getFolder("/").query().single("<root><child>test</child></root>").node()));
		}
		public void testSerializeContents3() {
			assertEquals("before<child>test<deepchild/>\n</child>after", serializeContents(db.getFolder("/").query().single("<root>before<child>test<deepchild/></child>after</root>").node()));
		}
	}
}
