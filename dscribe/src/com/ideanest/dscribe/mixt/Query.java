package com.ideanest.dscribe.mixt;


import static org.junit.Assert.assertEquals;

import org.exist.fluent.*;
import org.junit.Test;

public abstract class Query {
	private final String query;
	private final NamespaceMap namespaceMap;
	
	private Query(Node def, String query) throws RuleBaseException {
		if (query.trim().isEmpty()) throw new RuleBaseException("query string is empty");
		this.query = query;
		this.namespaceMap = def.inScopeNamespaces();
	}
	
	public QName parseQName(String qname) {
		return QName.parse(qname, namespaceMap);
	}
	
	public NamespaceMap namespaceBindings() {
		return namespaceMap;
	}
	
	public String contentsAsString() {
		return query;
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
		return buf.toString().replace("&quot;", "\"").replace("&apos;", "'").replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&");
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
		public Items(Node def) throws RuleBaseException {
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
		public Text(Node def) throws RuleBaseException {
			super(def, "<text value='" + serializeContents(def).replace("'", "''") + "'/>");
		}
		
		public String runOn(QueryService qs) {
			return super.execute(qs).get(0).query().single("@value").value();
		}
	}
	
	@Deprecated @SuppressWarnings("deprecation") @DatabaseTestCase.ConfigFile("test/conf.xml")
	public static class _Test extends DatabaseTestCase {
		@Test public void serializeContents1() {
			assertEquals("test", serializeContents(db.getFolder("/").query().single("<root>test</root>").node()));
		}
		@Test public void serializeContents2() {
			assertEquals("<child>test</child>", serializeContents(db.getFolder("/").query().single("<root><child>test</child></root>").node()));
		}
		@Test public void serializeContents3() {
			assertEquals("before<child>test<deepchild/>\n</child>after",
					serializeContents(db.getFolder("/").query().single("<root>before<child>test<deepchild/></child>after</root>").node()));
		}
		@Test public void defineItemsQuery() throws RuleBaseException {
			Query query = new Query.Items(db.getFolder("/").documents().build(Name.create("foo"))
					.elem("root").text("//foo/bar[@baz]").end("root").commit().root());
			assertEquals("//foo/bar[@baz]", query.query);
		}
		@Test public void defineItemsQueryWithNamespaces() throws RuleBaseException {
			Query query = new Query.Items(db.getFolder("/").documents().build(Name.create("foo"))
					.elem("root")
						.attr("xmlns:ns1", "http://example.com/ns1")
						.attr("xmlns:ns2", "http://example.com/ns2")
						.text("//ns1:foo/ns2:bar[@baz]")
					.end("root").commit().root());
			assertEquals("//ns1:foo/ns2:bar[@baz]", query.query);
			assertEquals("http://example.com/ns1", query.namespaceMap.get("ns1"));
			assertEquals("http://example.com/ns2", query.namespaceMap.get("ns2"));
		}
	}
}
