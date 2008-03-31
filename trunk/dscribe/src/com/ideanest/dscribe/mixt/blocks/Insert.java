package com.ideanest.dscribe.mixt.blocks;

import static com.ideanest.dscribe.mixt.test.Matchers.collection;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.io.UnsupportedEncodingException;
import java.security.*;
import java.util.*;

import org.exist.fluent.*;
import org.jmock.Expectations;
import org.junit.Test;

import com.ideanest.dscribe.mixt.*;
import com.ideanest.dscribe.mixt.SortController.OrderGraph;
import com.ideanest.dscribe.mixt.test.BlockTestCase;

public class Insert implements BlockType {

	public QName xmlName() {
		return new QName(Engine.RULES_NS, "insert", null);
	}
	
	public String version() {
		return "1";
	}

	@AllowAttributes({"in", "priority"})
	public Block define(Node def) throws RuleBaseException {
		return new InsertBlock(def);
	}

	private static class InsertBlock implements LinearBlock, SortingBlock<InsertBlock.InsertSeg> {
		private static final String DIGEST_TYPE = "MD5";
		
		private final Query.Items query;
		private Collection<String> requiredVariables;
		private final boolean inOrder;
		private final int priority;
		
		private InsertBlock(Node def) throws RuleBaseException {
			String inAttribute = def.query().optional("@in").value();
			if (inAttribute != null && !inAttribute.equals("order"))
				throw new RuleBaseException("@in attribute must have value 'order' if present, found '" + inAttribute + "'");
			inOrder = inAttribute != null;
			Item priorityItem = def.query().optional("@priority");
			if (priorityItem.extant()) {
				if (!inOrder) throw new RuleBaseException("@priority must only be specified when @in='order'");
				try {
					priority = priorityItem.intValue();
				} catch (DatabaseException e) {
					throw new RuleBaseException("insert block specified bad priority '" + priorityItem.value() + "'", e);
				}
			} else {
				priority = 0;
			}
			query = new Query.Items(def);
		}

		public void resolve(Mod.Builder modBuilder) throws TransformException {
			ItemList nodesToInsert = query.runOn(modBuilder.scope());
			
			if (nodesToInsert.size() > 0) {
				try {
					modBuilder.supplement()
							.elem("checksum").attr("digest-type", DIGEST_TYPE)
							.text(calculateDigest(DIGEST_TYPE, nodesToInsert))
							.end("checksum");
				} catch (NoSuchAlgorithmException e) {
					throw new TransformException("missing digest algorithm", e);
				}
				
				// TODO: error if (duplicate) xml:id attributes detected in nodes to insert, or maybe just wipe them?
				
				InsertionTarget target = modBuilder.dependOnNearest(InsertionTarget.class).unverified().get();
				boolean insertMultiple = nodesToInsert.size() > 1;
				if (!target.canInsertMultiple() && insertMultiple)
					throw new TransformException("inserting multiple nodes not allowed in this context");
				int serial = insertMultiple ? 1 : -1;
				for (Node node : nodesToInsert.nodes()) {
					Node insertedNode = target.insert(node);
					String id = modBuilder.generateId(serial++);
					insertedNode.update().attr("xml:id", id).commit();
					modBuilder.affect(insertedNode);
					if (inOrder && insertMultiple) modBuilder.order(insertedNode);
				}

			}
			
			modBuilder.dependOn(requiredVariables);
			modBuilder.commit();
		}
		
		private String calculateDigest(String digestType, ItemList nodesToInsert) throws NoSuchAlgorithmException {
			try {
				// TODO: canonicalize serialization to avoid spurious mismatches
				return DataUtils.toXMLString(MessageDigest.getInstance(digestType)
						.digest(nodesToInsert.toString().getBytes("UTF-8")));
			} catch (UnsupportedEncodingException e) {
				throw new RuntimeException("missing character encoding", e);
			}
		}

		public void sort(Collection<InsertSeg> segs, OrderGraph graph) {
			assert inOrder;
			for (InsertSeg seg : segs) {
				for (int i = 0; i < seg.inserted.size() - 1; i++) {
					for (int j = i + 1; j < seg.inserted.size(); j++) {
						graph.order(seg.inserted.get(i), seg.inserted.get(j), priority);
					}
				}
			}
		}
		
		public Seg createSeg(Mod mod) {
			return new InsertSeg(mod);
		}
		
		private class InsertSeg extends Seg implements NodeTarget {
			private String digestType;
			private String checksum;
			private List<String> inserted;
			
			InsertSeg(Mod mod) {super(mod);}
			
			@Override public void analyze() throws TransformException {
				requiredVariables = query.analyze(mod.globalScope()).requiredVariables();
			}
			
			@Override public void restore() throws TransformException {
				Node checksumNode = mod.supplementQuery().optional("checksum").node();
				digestType = checksumNode.query().optional("@digest-type").value();
				checksum = checksumNode.value();
				inserted = mod.affectedIds();
			}
			
			@Override public void verify() throws TransformException {
				ItemList nodesToInsert = query.runOn(mod.scope(null));
				try {
					if (!(checksum == null ? nodesToInsert.size() == 0 : checksum.equals(calculateDigest(digestType, nodesToInsert)))) {
						throw new TransformException("inserted node checksum mismatch");
					}
				} catch (NoSuchAlgorithmException e) {
					throw new TransformException("missing old digest algorithm, assuming mismatch", e);
				}
			}
			
			public ItemList targets() {
				return mod.globalScope().all("/id($_1)", mod.affectedIds());
			}
		}
	}
	
	@Deprecated public static class _Test extends BlockTestCase {
		@Test public void parse1() throws RuleBaseException {
			define("<insert><foo/></insert>");
		}
		
		@Test public void parse2() throws RuleBaseException {
			define("<insert in='order'><foo/></insert>");
		}
		
		@Test public void parse3() throws RuleBaseException {
			define("<insert in='order' priority='5'><foo/></insert>");
		}
		
		@Test(expected = RuleBaseException.class)
		public void parse4() throws RuleBaseException {
			define("<insert in='foobar'><foo/></insert>");
		}
		
		@Test(expected = RuleBaseException.class)
		public void parse5() throws RuleBaseException {
			define("<insert priority='5'><foo/></insert>");
		}
		
		@Test(expected = RuleBaseException.class)
		public void parse6() throws RuleBaseException {
			define("<insert in='order' priority='xx'><foo/></insert>");
		}
		
		@Test(expected = RuleBaseException.class)
		public void parse7() throws RuleBaseException {
			define("<insert>  </insert>");
		}
		
		@Test public void calculateDigestMD5() throws RuleBaseException, NoSuchAlgorithmException {
			InsertBlock block = define("<insert><foo/></insert>");
			XMLDocument doc = content.documents().load(Name.generate(), Source.xml(
					"<file xmlns='http://example.com' xml:id='j1'>" +
					"  <element xml:id='j2' name='foo'/>" +
					"  <element xml:id='j3' name='bar'>hello there</element>" +
					"</file>"));
			String digest = block.calculateDigest("MD5", doc.query().namespace("", "http://example.com").all("/file"));
			assertEquals("6lUpoz56bN71o+14Y7uP4A==", digest);
		}

		@Test(expected = NoSuchAlgorithmException.class)
		public void calculateDigestUnknownAlgorithm() throws RuleBaseException, NoSuchAlgorithmException {
			InsertBlock block = define("<insert><foo/></insert>");
			block.calculateDigest("FOO", null);
		}
		
		@Test public void resolveNoNodes() throws RuleBaseException, TransformException {
			InsertBlock block = define("<insert>()</insert>");
			block.requiredVariables = Collections.emptyList();
			setModBuilderScope(content.query());
			thenCommit();
			
			block.resolve(modBuilder);
		}
		
		private void testResolve(String rule, int count, boolean inOrder, final boolean multipleOK, String result, String checksum) throws TransformException, RuleBaseException {
			InsertBlock block = define(rule);
			block.requiredVariables = Collections.emptyList();
			
			final Node outputNode = content.documents().load(Name.generate(), Source.xml("<output/>")).root();
			
			setModBuilderScope(content.query());
			setModBuilderParent(mod);
			setModGlobalScope(content.query());
			
			dependOnNearest(InsertionTarget.class, false, new InsertionTarget() {
				public Node insert(Node node) throws TransformException {
					return outputNode.append().node(node).commit(); 
				}
				public boolean canInsertMultiple() {
					return multipleOK;
				}
			});
			supplement();
			generateIdsAndAffect("r1", count, inOrder);
			thenCommit();
			
			block.resolve(modBuilder);
			
			checkSupplement("<checksum digest-type='MD5'>" + checksum+ "</checksum>");
			assertTrue("got " + outputNode.query().all("*"), outputNode.query().single("* = " + result).booleanValue());
		}

		@Test public void resolveOneNode() throws RuleBaseException, TransformException {
			testResolve(
					"<insert><foo/></insert>",
					1, false, false, "<foo xml:id='_r1.'/>",
					"5KZSM2zZkiS5PyrDhT/IlQ==");
		}
		
		@Test public void resolveOneOrderedNode() throws RuleBaseException, TransformException {
			testResolve(
					"<insert in='order'><foo/></insert>",
					1, false, false, "<foo xml:id='_r1.'/>",
					"5KZSM2zZkiS5PyrDhT/IlQ==");
		}
		
		@Test public void resolveTwoNodes() throws TransformException, RuleBaseException {
			testResolve(
					"<insert>(<node1/>, <node2/>)</insert>",
					2, false, true, "(<node1 xml:id='_r1-0'/>, <node2 xml:id='_r1-1'/>)",
					"/UBBH4crvHJQiCQxRye4TQ==");
		}
		
		@Test(expected = TransformException.class)
		public void resolveTwoNodesNotAllowed() throws TransformException, RuleBaseException {
			testResolve(
					"<insert>(<node1/>, <node2/>)</insert>",
					2, false, false, "(<node1 xml:id='_r1-0'/>, <node2 xml:id='_r1-1'/>)",
					"/UBBH4crvHJQiCQxRye4TQ==");
		}
		
		@Test public void resolveTwoOrderedNodes() throws TransformException, RuleBaseException {
			testResolve(
					"<insert in='order'>(<node1/>, <node2/>)</insert>",
					2, true, true, "(<node1 xml:id='_r1-0'/>, <node2 xml:id='_r1-1'/>)",
					"/UBBH4crvHJQiCQxRye4TQ==");
		}
		
		@Test public void resolveComplexNodes() throws TransformException, RuleBaseException {
			testResolve(
					"<insert>(<n1 xmlns:k='foo' name='bar'><k:n11 foo='bar'>la la <b>bla</b></k:n11><n12/></n1>, <n2 xmlns='bar'/>)</insert>",
					2, false, true, "(<n1 xml:id='_r1-1.' xmlns:k='foo' name='bar'><k:n11 foo='bar'>la la <b>bla</b></k:n11><n12/></n1>, <n2 xml:id='_r1-2.' xmlns='bar'/>)",
					"Sl+F6eU9sYgaSTCX6eKvFg==");
		}
		
		@Test public void sortOneSegNoPriority() throws RuleBaseException {
			InsertBlock block = define("<insert in='order'><foo/></insert>");
			InsertBlock.InsertSeg seg = (InsertBlock.InsertSeg) block.createSeg(mod);
			seg.inserted = Arrays.asList("m1", "m2", "m3");
			final SortController.OrderGraph graph = mockery.mock(SortController.OrderGraph.class);
			mockery.checking(new Expectations() {{
				one(graph).order("m1", "m2", 0);
				one(graph).order("m1", "m3", 0);
				one(graph).order("m2", "m3", 0);
			}});
			block.sort(Collections.singletonList(seg), graph);
		}

		@Test public void sortTwoSegsWithPriority() throws RuleBaseException {
			InsertBlock block = define("<insert in='order' priority='5'><foo/></insert>");
			InsertBlock.InsertSeg seg1 = (InsertBlock.InsertSeg) block.createSeg(mod);
			seg1.inserted = Arrays.asList("m1", "m2");
			InsertBlock.InsertSeg seg2 = (InsertBlock.InsertSeg) block.createSeg(mod);
			seg2.inserted = Arrays.asList("c1", "c2");
			final SortController.OrderGraph graph = mockery.mock(SortController.OrderGraph.class);
			mockery.checking(new Expectations() {{
				one(graph).order("m1", "m2", 5);
				one(graph).order("c1", "c2", 5);
			}});
			block.sort(Arrays.asList(seg1, seg2), graph);
		}

		@Test public void analyze() throws RuleBaseException, TransformException {
			InsertBlock block = define("<insert><uml:name xmlns:uml='xxx'>{$src/text()}</uml:name></insert>");
			setModGlobalScope(content.query());
			Seg seg = block.createSeg(mod);
			seg.analyze();
			assertThat(block.requiredVariables, is(collection("$src")));
		}
		
		@Test public void restore() throws RuleBaseException, TransformException {
			InsertBlock block = define("<insert><foo/></insert>");
			setModData("<checksum digest-type='MD5'>5KZSM2zZkiS5PyrDhT/IlQ==</checksum>");
			setModAffectedIds("m1", "m2");
			InsertBlock.InsertSeg seg = (InsertBlock.InsertSeg) block.createSeg(mod);
			seg.restore();
			assertEquals("MD5", seg.digestType);
			assertEquals("5KZSM2zZkiS5PyrDhT/IlQ==", seg.checksum);
			assertEquals(Arrays.asList("m1", "m2"), seg.inserted);
		}
		
		@Test public void verifyWorks() throws RuleBaseException, TransformException {
			InsertBlock block = define("<insert><foo/></insert>");
			InsertBlock.InsertSeg seg = (InsertBlock.InsertSeg) block.createSeg(mod);
			seg.digestType = "MD5";
			seg.checksum = "5KZSM2zZkiS5PyrDhT/IlQ==";
			setModScope(content.query());
			seg.verify();
		}

		@Test(expected = TransformException.class)
		public void verifyFailsBadAlgorithm() throws RuleBaseException, TransformException {
			InsertBlock block = define("<insert><foo/></insert>");
			InsertBlock.InsertSeg seg = (InsertBlock.InsertSeg) block.createSeg(mod);
			seg.digestType = "foo";
			seg.checksum = "5KZSM2zZkiS5PyrDhT/IlQ==";
			setModScope(content.query());
			seg.verify();
		}

		@Test(expected = TransformException.class)
		public void verifyFailsBadChecksum1() throws RuleBaseException, TransformException {
			InsertBlock block = define("<insert><foobar/></insert>");
			InsertBlock.InsertSeg seg = (InsertBlock.InsertSeg) block.createSeg(mod);
			seg.digestType = "MD5";
			seg.checksum = "5KZSM2zZkiS5PyrDhT/IlQ==";
			setModScope(content.query());
			seg.verify();
		}

		@Test(expected = TransformException.class)
		public void verifyFailsBadChecksum2() throws RuleBaseException, TransformException {
			InsertBlock block = define("<insert><foo/></insert>");
			InsertBlock.InsertSeg seg = (InsertBlock.InsertSeg) block.createSeg(mod);
			setModScope(content.query());
			seg.verify();
		}
	}
}
