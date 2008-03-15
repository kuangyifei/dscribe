package com.ideanest.dscribe.mixt.blocks;

import static com.ideanest.dscribe.testutil.Matchers.collection;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.util.*;

import org.exist.fluent.*;
import org.junit.Test;

import com.ideanest.dscribe.mixt.*;
import com.ideanest.dscribe.testutil.BlockTestCase;

public class CreateDoc implements BlockType {

	public QName xmlName() {
		return new QName(Engine.RULES_NS, "create-doc", null);
	}
	
	public String version() {
		return "1";
	}

	public Block define(Node def) throws RuleBaseException {
		return new CreateDocBlock(def);
	}
	
	private static class CreateDocBlock implements LinearBlock {
		private final Query.Text query;
		private Collection<String> requiredVariables;
		
		CreateDocBlock(Node def) throws RuleBaseException {
			query = def.query().exists("node()") ? new Query.Text(def) : null;
		}
		
		public void resolve(Mod.Builder modBuilder) throws TransformException {
			modBuilder.supplement().elem("docname")
				.text(resolveName(modBuilder.parent(), modBuilder.scope()))
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
				name = mod.node().query().single("docname").value();
			}
			
			@Override public void analyze() {
				requiredVariables = query.analyze(mod.globalScope()).requiredVariables();
			}
			
			@Override public void verify() throws TransformException {
				String resolvedName = resolveName(mod, mod.scope(null));
				if (!name.equals(resolvedName))
					throw new TransformException("stored document name '" + name + "' doesn't match recalculated document name '" + resolvedName + "'");
			}
			
			public ElementBuilder<?> contentBuilder() throws TransformException {
				String docName = name;
				Folder folder;
				int k = docName.lastIndexOf('/');
				if (k == -1) {
					folder = mod.workspace();
				} else {
					folder = mod.workspace().children().create(docName.substring(0, k));
					docName = docName.substring(k+1);
				}
				return folder.documents().build(Name.adjust(docName));
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
			setModBuilderScope(content.query());
			supplement();
			thenCommit();
			block.resolve(modBuilder);
			checkSupplement("<docname>constant</docname>");
		}
		
		@Test public void restore() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc>constant</create-doc>");
			setModData("<block><docname>hellothere</docname></block>");
			CreateDocBlock.CreateDocSeg seg = (CreateDocBlock.CreateDocSeg) block.createSeg(mod);
			seg.restore();
			assertEquals("hellothere", seg.name);
		}
		
		@Test public void analyze() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc>{$x}-{$y}</create-doc>");
			setModGlobalScope(content.query());
			block.createSeg(mod).analyze();
			assertThat(block.requiredVariables, is(collection("$x", "$y")));
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
		
		@Test public void contentBuilder1() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc>hello</create-doc>");
			setModWorkspace(content);
			CreateDocBlock.CreateDocSeg seg = (CreateDocBlock.CreateDocSeg) block.createSeg(mod);
			seg.name = "hello";
			XMLDocument doc = (XMLDocument) seg.contentBuilder().elem("root").end("root").commit();
			assertEquals("/content/hello", doc.path());
		}

		@Test public void contentBuilder2() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc>hello</create-doc>");
			setModWorkspace(content);
			CreateDocBlock.CreateDocSeg seg = (CreateDocBlock.CreateDocSeg) block.createSeg(mod);
			seg.name = "foo/bar/hello";
			XMLDocument doc = (XMLDocument) seg.contentBuilder().elem("root").end("root").commit();
			assertEquals("/content/foo/bar/hello", doc.path());
		}

		@Test public void contentBuilder3() throws RuleBaseException, TransformException {
			CreateDocBlock block = define("<create-doc>hello</create-doc>");
			setModWorkspace(content);
			content.documents().load(Name.create("hello"), Source.xml("<foo/>"));
			CreateDocBlock.CreateDocSeg seg = (CreateDocBlock.CreateDocSeg) block.createSeg(mod);
			seg.name = "hello";
			XMLDocument doc = (XMLDocument) seg.contentBuilder().elem("root").end("root").commit();
			assertTrue(doc.path().startsWith("/content/hello"));
			assertFalse(doc.path().equals("/content/hello"));
		}

	}
}
