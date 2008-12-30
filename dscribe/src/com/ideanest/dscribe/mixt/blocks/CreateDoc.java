package com.ideanest.dscribe.mixt.blocks;

import static com.ideanest.dscribe.mixt.test.Matchers.collection;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.util.*;

import org.exist.fluent.*;
import org.junit.Test;

import com.ideanest.dscribe.mixt.*;
import com.ideanest.dscribe.mixt.test.BlockTestCase;

public class CreateDoc implements BlockType {

	public QName xmlName() {
		return new QName(Engine.MIXT_NS, "create-doc", null);
	}
	
	public String version() {
		return "1";
	}

	public Block define(Node def) throws RuleBaseException {
		return new CreateDocBlock(def);
	}
	
	private static class CreateDocBlock implements LinearBlock {
		private final Query.Text query;
		private Collection<QName> requiredVariables;
		
		CreateDocBlock(Node def) throws RuleBaseException {
			query = def.query().exists("node()") ? new Query.Text(def) : null;
		}
		
		public void resolve(Mod.Builder modBuilder) throws TransformException {
			modBuilder.supplement().elem("docname")
				.text(resolveName(modBuilder.parent(), modBuilder.closedScope()))
			.end("docname");
			modBuilder.dependOn(requiredVariables);
			modBuilder.commit();
		}

		private String resolveName(Mod keyMod, QueryService scope) throws TransformException {
			String name = ((query == null) ? keyMod.key() + "xml" : query.runOn(scope)).trim();
			if (name == null || name.length() == 0)
				throw new TransformException("create-doc failed to resolve document name");
			if (name.charAt(0) == '/') throw new TransformException("create-doc document name cannot begin with a slash");
			if (name.charAt(name.length()-1) == '/') throw new TransformException("create-doc document name cannot end with a slash");
			return name;
		}
		
		public Seg createSeg(Mod mod) {return new CreateDocSeg(mod);}
		
		private class CreateDocSeg extends Seg implements InsertionTarget {
			private String name;
			
			CreateDocSeg(Mod mod) {super(mod);}
			
			@Override public void restore() throws TransformException {
				name = mod.supplementQuery().single("docname").value();
			}
			
			@Override public QueryService.QueryAnalysis analyze() {
				QueryService.QueryAnalysis analysis = query.analyze(mod.globalScope());
				requiredVariables = analysis.requiredVariables();
				return analysis;
			}
			
			@Override public void verify() throws TransformException {
				String resolvedName = resolveName(mod, mod.scope(null));
				if (!name.equals(resolvedName))
					throw new TransformException("stored document name '" + name + "' doesn't match recalculated document name '" + resolvedName + "'");
			}
			
			public Node insert(Node node) throws TransformException {
				String docName = name;
				Folder folder;
				int k = docName.lastIndexOf('/');
				if (k == -1) {
					folder = mod.workspace();
				} else {
					folder = mod.workspace().children().create(docName.substring(0, k));
					docName = docName.substring(k+1);
				}
				return folder.documents().build(Name.adjust(docName)).node(node).commit().root();
			}
			
			public boolean canInsertMultiple() {
				return false;
			}
		}
		
	}
	
	@Deprecated public static class _Test extends BlockTestCase {
		@Test public void parseWithName() throws RuleBaseException {
			CreateDocBlock block = define("<create-doc>hello</create-doc>");
			assertNotNull(block.query);
		}

		@Test public void parseNoName() throws RuleBaseException {
			CreateDocBlock block = define("<create-doc/>");
			assertNull(block.query);
		}
		
		@Test public void resolveNameConstant() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc>  constant </create-doc>");
			assertEquals("constant", block.resolveName(mod, content.query()));
		}
		
		@Test public void resolveNameVariable() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc>foo-{$x}</create-doc>");
			assertEquals("foo-bar", block.resolveName(mod, content.query().let("$x", "bar")));
		}
		
		@Test public void resolveNameNull() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc/>");
			setModKey("_r1.");
			assertEquals("_r1.xml", block.resolveName(mod, content.query()));
		}

		@Test(expected = TransformException.class)
		public void resolveBadName1() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc>{()}</create-doc>");
			block.resolveName(mod, content.query());
		}

		@Test(expected = TransformException.class)
		public void resolveBadName2() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc>/foo</create-doc>");
			block.resolveName(mod, content.query());
		}

		@Test(expected = TransformException.class)
		public void resolveBadName3() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc>foo/</create-doc>");
			block.resolveName(mod, content.query());
		}
		
		@Test public void resolve() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc>constant</create-doc>");
			block.requiredVariables = Collections.emptyList();
			setModBuilderParent(mod);
			setModBuilderClosedScope(content.query());
			supplement();
			thenCommit();
			block.resolve(modBuilder);
			checkSupplement("<docname>constant</docname>");
		}
		
		@Test public void restore() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc>constant</create-doc>");
			setModData("<docname>hellothere</docname>");
			CreateDocBlock.CreateDocSeg seg = (CreateDocBlock.CreateDocSeg) block.createSeg(mod);
			seg.restore();
			assertEquals("hellothere", seg.name);
		}
		
		@Test public void analyze() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc>{$x}-{$y}</create-doc>");
			setModGlobalScope(content.query());
			block.createSeg(mod).analyze();
			assertThat(block.requiredVariables, is(collection(new QName(null, "x", null), new QName(null, "y", null))));
		}
		
		@Test public void verifyWorks() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc>hello</create-doc>");
			setModScope(content.query());
			CreateDocBlock.CreateDocSeg seg = (CreateDocBlock.CreateDocSeg) block.createSeg(mod);
			seg.name = "hello";
			seg.verify();
		}

		@Test(expected = TransformException.class)
		public void verifyFails() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc>hello</create-doc>");
			setModScope(content.query());
			CreateDocBlock.CreateDocSeg seg = (CreateDocBlock.CreateDocSeg) block.createSeg(mod);
			seg.name = "goodbye";
			seg.verify();
		}
		
		@Test public void insert1() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc>hello</create-doc>");
			setModWorkspace(content);
			CreateDocBlock.CreateDocSeg seg = (CreateDocBlock.CreateDocSeg) block.createSeg(mod);
			seg.name = "hello";
			Node root = seg.insert(db.query().single("<root/>").node());
			assertEquals("/content/hello", root.document().path());
			assertEquals("<root/>", root.toString());
		}

		@Test public void insert2() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc>hello</create-doc>");
			setModWorkspace(content);
			CreateDocBlock.CreateDocSeg seg = (CreateDocBlock.CreateDocSeg) block.createSeg(mod);
			seg.name = "foo/bar/hello";
			Node root = seg.insert(db.query().single("<root/>").node());
			assertEquals("/content/foo/bar/hello", root.document().path());
			assertEquals("<root/>", root.toString());
		}

		@Test public void insert3() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc>hello</create-doc>");
			setModWorkspace(content);
			content.documents().load(Name.create("hello"), Source.xml("<foo/>"));
			CreateDocBlock.CreateDocSeg seg = (CreateDocBlock.CreateDocSeg) block.createSeg(mod);
			seg.name = "hello";
			Node root = seg.insert(db.query().single("<root/>").node());
			assertTrue(root.document().path().startsWith("/content/hello"));
			assertFalse(root.document().path().equals("/content/hello"));
			assertEquals("<root/>", root.toString());
		}

	}
}
