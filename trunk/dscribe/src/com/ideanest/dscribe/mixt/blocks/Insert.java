package com.ideanest.dscribe.mixt.blocks;

import static com.ideanest.dscribe.testutil.Matchers.collection;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.io.UnsupportedEncodingException;
import java.security.*;
import java.util.*;

import org.exist.fluent.*;
import org.junit.Test;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.mixt.*;
import com.ideanest.dscribe.testutil.BlockTestCase;

public class Insert implements BlockType {

	public QName xmlName() {
		return new QName(Namespace.RULES, "insert", null);
	}
	public Block define(Node def) throws RuleBaseException {
		return new InsertBlock(def);
	}

	private static class InsertBlock implements LinearBlock {
		private static final String DIGEST_TYPE = "MD5";
		
		private final Query.Items query;
		private Collection<String> requiredVariables;
		
		private InsertBlock(Node def) throws RuleBaseException {
			query = new Query.Items(def);
		}

		public void resolve(Mod.Builder modBuilder) throws TransformException {
			// requery with self::* to force in-memory nodes to be materialized for further processing
			ItemList nodesToInsert = query.runOn(modBuilder.scope()).query().all("self::*");
			
			if (nodesToInsert.size() > 0) {
				try {
					modBuilder.supplement()
							.elem("checksum").attr("digest-type", DIGEST_TYPE)
							.text(calculateDigest(DIGEST_TYPE, nodesToInsert))
							.end("checksum");
				} catch (NoSuchAlgorithmException e) {
					throw new TransformException("missing digest algorithm", e);
				}
				
				// TODO: error if xml:id attributes detected in nodes to insert, or maybe just wipe them?
				
				int serial = nodesToInsert.size() == 1 ? -1 : 1;
				List<String> genIdList = new ArrayList<String>(nodesToInsert.size());
				for (Node node : nodesToInsert.nodes()) {
					String id = modBuilder.generateId(serial++);
					genIdList.add(id);
					node.update().attr("xml:id", id).commit();
				}
				modBuilder.parent().nearestAncestorImplementing(InsertionTarget.class)
						.contentBuilder().nodes(nodesToInsert.nodes()).commit();
				for (String id : genIdList) {
					modBuilder.affect(modBuilder.parent().globalScope().single("/id($_1)", id).node());
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

		public Seg createSeg(Mod mod) {
			return new InsertSeg(mod);
		}
		
		private class InsertSeg extends Seg {
			private String digestType;
			private String checksum;
			
			InsertSeg(Mod mod) {super(mod);}
			
			@Override public void analyze() throws TransformException {
				requiredVariables = query.analyze(mod.globalScope()).requiredVariables();
			}
			
			@Override public void restore() throws TransformException {
				Node checksumNode = mod.data().query().optional("checksum").node();
				digestType = checksumNode.query().optional("@digest-type").value();
				checksum = checksumNode.value();
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
		}
	}
	
	@Deprecated public static class _Test extends BlockTestCase {
		@Test public void parse() throws RuleBaseException {
			define("<insert><foo/></insert>");
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
			InsertBlock block = define("<insert/>");
			block.requiredVariables = Collections.emptyList();
			setModBuilderScope(content.query());
			thenCommit();
			
			block.resolve(modBuilder);
		}
		
		private void testResolve(String rule, int count, String result, String checksum) throws TransformException, RuleBaseException {
			InsertBlock block = define(rule);
			block.requiredVariables = Collections.emptyList();
			
			final Node outputNode = content.documents().load(Name.generate(), Source.xml("<output/>")).root();
			
			setModBuilderScope(content.query());
			setModBuilderParent(mod);
			setModNearestAncestorImplementing(InsertionTarget.class, new InsertionTarget() {
				public ElementBuilder<?> contentBuilder() throws TransformException {
					return outputNode.append(); 
				}
			});
			setModGlobalScope(content.query());
			
			supplement();
			generateIdsAndAffect("r1", count);
			thenCommit();
			
			block.resolve(modBuilder);
			
			checkSupplement("<checksum digest-type='MD5'>" + checksum+ "</checksum>");
			assertTrue("got " + outputNode.query().all("*"), outputNode.query().single("* = " + result).booleanValue());
		}

		@Test public void resolveOneNode() throws RuleBaseException, TransformException {
			testResolve(
					"<insert><foo/></insert>",
					1, "<foo xml:id='_r1.'/>",
					"5KZSM2zZkiS5PyrDhT/IlQ==");
		}
		
		@Test public void resolveTwoNodes() throws TransformException, RuleBaseException {
			testResolve(
					"<insert>(<node1/>, <node2/>)</insert>",
					2, "(<node1 xml:id='_r1-0'/>, <node2 xml:id='_r1-1'/>)",
					"vOCXYoXCMJLGSMTLEYeSXw==");
		}
		
		@Test public void resolveComplexNodes() throws TransformException, RuleBaseException {
			testResolve(
					"<insert>(<n1 xmlns:k='foo' name='bar'><k:n11 foo='bar'>la la <b>bla</b></k:n11><n12/></n1>, <n2 xmlns='bar'/>)</insert>",
					2, "(<n1 xml:id='_r1-1.' xmlns:k=\'foo\' name=\'bar\'><k:n11 foo=\'bar\'>la la <b>bla</b></k:n11><n12/></n1>, <n2 xml:id='_r1-2.' xmlns=\'bar\'/>)",
					"LuF6oZwiRVD4V5+DUwprgQ==");
		}

		@Test
		public void analyze() throws RuleBaseException, TransformException {
			InsertBlock block = define("<insert><uml:name xmlns:uml='xxx'>{$src/text()}</uml:name></insert>");
			setModGlobalScope(content.query());
			Seg seg = block.createSeg(mod);
			seg.analyze();
			assertThat(block.requiredVariables, is(collection("$src")));
		}
		
		@Test
		public void restore() throws RuleBaseException, TransformException {
			InsertBlock block = define("<insert><foo/></insert>");
			setModData("<block><checksum digest-type='MD5'>5KZSM2zZkiS5PyrDhT/IlQ==</checksum></block>");
			InsertBlock.InsertSeg seg = (InsertBlock.InsertSeg) block.createSeg(mod);
			seg.restore();
			assertEquals("MD5", seg.digestType);
			assertEquals("5KZSM2zZkiS5PyrDhT/IlQ==", seg.checksum);
		}
		
		@Test
		public void verifyWorks() throws RuleBaseException, TransformException {
			InsertBlock block = define("<insert><foo/></insert>");
			InsertBlock.InsertSeg seg = (InsertBlock.InsertSeg) block.createSeg(mod);
			seg.digestType = "MD5";
			seg.checksum = "5KZSM2zZkiS5PyrDhT/IlQ==";
			setModScope(content.query());
			seg.verify();
		}

		public void verifyWorksEmpty() throws RuleBaseException, TransformException {
			InsertBlock block = define("<insert></insert>");
			InsertBlock.InsertSeg seg = (InsertBlock.InsertSeg) block.createSeg(mod);
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
