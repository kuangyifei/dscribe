package com.ideanest.dscribe.mixt.blocks;

import static com.ideanest.dscribe.testutil.Matchers.collection;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.util.*;

import org.exist.fluent.*;
import org.junit.Test;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.mixt.*;
import com.ideanest.dscribe.testutil.BlockTestCase;

public class For implements BlockType {
	
	public QName xmlName() {
		return new QName(Namespace.RULES, "for", null);
	}

	public Block define(Node def) throws RuleBaseException {
		boolean each = def.query().exists("@each"), one = def.query().exists("@one");
		if (each && one) throw new RuleBaseException("for block specified with both @each and @one");
		if (!(each || one)) throw new RuleBaseException("for block specified with neither @each nor @one");
		return each ? new ForEachBlock(def) : new ForOneBlock(def); 
	}
	
	private static class ForEachBlock extends ForBlock implements KeyBlock {
		ForEachBlock(Node def) throws RuleBaseException {
			super(def, "@each");
		}
		public void resolve(KeyMod.Builder modBuilder) throws TransformException {
			for (Node node : query.runOn(modBuilder.scope()).nodes()) {
				modBuilder.referenceKey(node);
				modBuilder.dependOn(requiredVariables);
				modBuilder.commit();
			}
		}
	}
	
	private static class ForOneBlock extends ForBlock implements LinearBlock {
		ForOneBlock(Node def) throws RuleBaseException {
			super(def, "@one");
		}
		public void resolve(Mod.Builder modBuilder) throws TransformException {
			ItemList items = query.runOn(modBuilder.scope());
			if (items.size() == 0) return;
			if (items.size() > 1) throw new TransformException("for-one got " + items.size() + " results from query");
			modBuilder.reference(items.get(0).node());
			modBuilder.dependOn(requiredVariables);
			modBuilder.commit();
		}
	}
	
	private static abstract class ForBlock implements Block {
		private final String variableName;
		final Query.Items query;
		private final boolean target;
		Collection<String> requiredVariables;
		
		ForBlock(Node def, String varAttrName) throws RuleBaseException {
			String varName = def.query().single(varAttrName).value();
			target = varName.equals("target");
			if (target) {
				varName = null;
			} else {
				if (!varName.startsWith("$"))  // TODO: verify variable name syntax
					throw new RuleBaseException("unrecognized for block variable keyword '" + varName + "'");
			}
			variableName = varName;
			query = new Query.Items(def);	
		}
		
		public Seg createSeg(Mod mod) {
			return new ForSeg(mod);
		}
		
		private class ForSeg extends Seg implements InsertionTarget {
			ForSeg(Mod mod) {super(mod);}
			
			private void bindVariable(Object value) throws TransformException {
				if (variableName != null) mod.bindVariable(variableName, value);
			}
			
			@Override public void analyze() throws TransformException {
				requiredVariables = query.analyze(mod.globalScope()).requiredVariables();
				bindVariable(null);
			}
			
			@Override public void restore() throws TransformException {
				bindVariable(mod.references().get(0));
			}
			
			@Override public void verify() throws TransformException {
				Node node = mod.references().get(0);
				QueryService scope = mod.scope(node.document().query());
				if (!query.runOn(scope).query().single("@xml:id=$_1/@xml:id", node).booleanValue())
					throw new TransformException("query didn't select node with id " + node.query().single("@xml:id").value());
			}
				
			public ElementBuilder<?> contentBuilder() throws TransformException {
				if (target) return mod.references().get(0).append();
				return mod.nearestAncestorImplementing(InsertionTarget.class).contentBuilder();
			}
		}

	}

	@Deprecated
	public static class _Test extends BlockTestCase {		
		
		@Test(expected = RuleBaseException.class)
		public void parseNoVariableAttribute() throws RuleBaseException {
			define("<for> //foo </for>");
		}

		@Test(expected = RuleBaseException.class)
		public void parseMultipleVariableAttributes() throws RuleBaseException {
			define("<for each='$x' one='$x'> //foo </for>");
		}
		
		@Test
		public void parseForEachBlock() throws RuleBaseException {
			ForBlock block = define("<for each='$x'> //foo </for>");
			assertTrue(block instanceof ForEachBlock);
			assertEquals("$x", block.variableName);
			assertFalse(block.target);
		}
		
		@Test
		public void parseForOneBlock() throws RuleBaseException {
			ForBlock block = define("<for one='$x'> //foo </for>");
			assertTrue(block instanceof For.ForOneBlock);
			assertEquals("$x", block.variableName);
			assertFalse(block.target);
		}
		
		@Test
		public void parseTargetKeyword() throws RuleBaseException {
			ForBlock block = define("<for each='target'> //foo </for>");
			assertTrue(block instanceof For.ForEachBlock);
			assertNull(block.variableName);
			assertTrue(block.target);
		}
		
		@Test(expected = RuleBaseException.class)
		public void parseBadKeyword() throws RuleBaseException {
			define("<for each='foo'> //foo </for>");
		}

		@Test(expected = TransformException.class)
		public void resolveOneFailsOnMultipleResults() throws RuleBaseException, TransformException {
			ForOneBlock block = define("<for one='$x'> //java:method </for>");
			setModBuilderScope(content.query());
			block.resolve(modBuilder);
		}
		
		@Test
		public void resolveOneDoesNothingOnNoResult() throws RuleBaseException, TransformException {
			ForOneBlock block = define("<for one='$x'> //java:foobar </for>");
			setModBuilderScope(content.query());
			block.resolve(modBuilder);
		}

		@Test
		public void resolveOneWorks() throws RuleBaseException, TransformException {
			ForOneBlock block = define("<for one='$x'> //java:method[@name='start'] </for>");
			block.requiredVariables = Collections.emptyList();

			setModBuilderScope(content.query());
			reference(content.query().single("//java:method[@name='start']").node());
			thenCommit();
			
			block.resolve(modBuilder);
		}
		
		@Test
		public void resolveEachWorks() throws RuleBaseException, TransformException {
			ForEachBlock block = define("<for each='$x'> //java:method </for>");
			block.requiredVariables = Collections.emptyList();

			setModBuilderScope(content.query());
			for (Node node : content.query().all("//java:method").nodes()) {
				referenceKey(node);
				thenCommit();
			}
			
			block.resolve(keyModBuilder);
		}
		
		@Test
		public void analyzeWorksWithVariable() throws RuleBaseException, TransformException {
			ForOneBlock block = define("<for one='$x'> //java:method[@name=$other] </for>");
			setModGlobalScope(content.query());
			bindVariable("$x", null);
			Seg seg = block.createSeg(mod);
			seg.analyze();
			assertThat(block.requiredVariables, is(collection("$other")));
		}
		
		@Test
		public void analyzeWorksWithKeyword() throws RuleBaseException, TransformException {
			ForOneBlock block = define("<for one='target'> //java:method[@name='start'] </for>");
			setModGlobalScope(content.query());
			Seg seg = block.createSeg(mod);
			seg.analyze();
			assertTrue(block.requiredVariables.isEmpty());
		}
		
		@Test
		public void restoreWorksWithVariable() throws RuleBaseException, TransformException {
			ForOneBlock block = define("<for one='$x'> //java:method[@name=$other] </for>");
			final Node variableValue = content.query().single("//java:method[@name='start']").node();
			setModReferences(variableValue);
			bindVariable("$x", variableValue);
			block.createSeg(mod).restore();
		}

		@Test
		public void restoreWorksWithKeyword() throws RuleBaseException, TransformException {
			ForOneBlock block = define("<for one='target'> //java:method[@name='start'] </for>");
			setModReferences((Node) null);
			block.createSeg(mod).restore();
		}
		
		@Test
		public void verifyWorks() throws RuleBaseException, TransformException {
			ForOneBlock block = define("<for one='$x'> //java:class </for>");
			final Node selectedNode = content.query().single("//java:class").node();
			setModReferences(selectedNode);
			setModScope(selectedNode.document().query());
			block.createSeg(mod).verify();
		}

		@Test(expected = TransformException.class)
		public void verifyFails() throws RuleBaseException, TransformException {
			ForOneBlock block = define("<for one='$x'> //java:class[@name='FooBar'] </for>");
			final Node selectedNode = content.query().single("//java:class").node();
			setModReferences(selectedNode);
			setModScope(selectedNode.document().query());
			block.createSeg(mod).verify();
		}
		
		@Test
		public void contentBuilderWithTarget() throws RuleBaseException, TransformException {
			ForOneBlock block = define("<for one='target'> //java:class </for>");
			final Node selectedNode = content.query().single("//java:class").node();
			setModReferences(selectedNode);
			((ForBlock.ForSeg) block.createSeg(mod)).contentBuilder()
				.elem("java:method").attr("xml:id", "m3").attr("name", "between").end("java:method").commit();
			assertTrue(selectedNode.query().exists("java:method[@xml:id='m3']"));
		}

		@Test
		public void contentBuilderNotTarget() throws RuleBaseException, TransformException {
			ForOneBlock block = define("<for one='$x'> //java:class </for>");
			final ElementBuilder<?> contentBuilder = ElementBuilder.createScratch(null);
			InsertionTarget insertionTarget = new InsertionTarget() {
				public ElementBuilder<?> contentBuilder() throws TransformException {
					return contentBuilder;
				}
			};
			setModNearestAncestorImplementing(InsertionTarget.class, insertionTarget);
			assertSame(contentBuilder, ((ForBlock.ForSeg) block.createSeg(mod)).contentBuilder());
			contentBuilder.commit();
		}

	}
}
