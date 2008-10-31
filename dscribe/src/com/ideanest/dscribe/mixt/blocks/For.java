package com.ideanest.dscribe.mixt.blocks;

import static com.ideanest.dscribe.mixt.test.Matchers.collection;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import java.util.*;

import org.exist.fluent.*;
import org.junit.Test;

import com.ideanest.dscribe.mixt.*;
import com.ideanest.dscribe.mixt.test.BlockTestCase;

public class For implements BlockType {
	
	public QName xmlName() {
		return new QName(Engine.RULES_NS, "for", null);
	}

	public String version() {
		return "1";
	}

	@AllowAttributes({"each", "one", "all"})
	public Block define(Node def) throws RuleBaseException {
		int numDirectives = def.query().single("count(@each | @one | @all)").intValue();
		if (numDirectives > 1) throw new RuleBaseException("for block specified with more than one of @each, @one and @all");
		if (numDirectives <1) throw new RuleBaseException("for block specified without any @each, @one or @all");
		return
			def.query().exists("@each") ? new ForEachBlock(def)
			: def.query().exists("@one") ? new ForOneBlock(def)
			: new ForAllBlock(def);
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
		public Seg createSeg(Mod mod) {
			return new ForEachSeg(mod);
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
		public Seg createSeg(Mod mod) {
			return new ForEachSeg(mod);
		}
	}
	
	private static class ForAllBlock extends ForBlock implements LinearBlock {
		private static final Comparator<Node> XML_ID_COMPARATOR = new Comparator<Node>() {
			@Override public int compare(Node n1, Node n2) {
				return n1.query().single("@xml:id").value().compareTo(n2.query().single("@xml:id").value());
			}
		};
		
		ForAllBlock(Node def) throws RuleBaseException {
			super(def, "@all");
			if (target) throw new RuleBaseException("for-all cannot be used with 'target'");
		}
		
		private static Node[] nodesToCanonicalArray(ItemList items) {
			Node[] nodes = items.nodes().toArray();
			Arrays.sort(nodes, XML_ID_COMPARATOR);
			return nodes;
		}
		
		public void resolve(Mod.Builder modBuilder) throws TransformException {
			ItemList items = query.runOn(modBuilder.scope());
			if (items.size() == 0) return;
			for (Node node : nodesToCanonicalArray(items)) modBuilder.reference(node);
			modBuilder.dependOn(requiredVariables);
			modBuilder.commit();
		}
		@Override public Seg createSeg(Mod mod) {
			return new ForAllSeg(mod);
		}
		
		private class ForAllSeg extends ForSeg {
			ForAllSeg(Mod mod) {super(mod);}
			
			@Override public void restore() throws TransformException {
				bindVariable(mod.globalScope().all("$_1", mod.references()));
			}
			
			@Override public void verify() throws TransformException {
				List<Node> queryResult = Arrays.asList(nodesToCanonicalArray(query.runOn(mod.scope(null))));
				if (!queryResult.equals(mod.references()))
					throw new TransformException("query selected a different set of nodes");
			}			
		}
	}
	
	private static abstract class ForBlock implements Block {
		private final QName variableName;
		final Query.Items query;
		final boolean target;
		Collection<QName> requiredVariables;
		
		ForBlock(Node def, String varAttrName) throws RuleBaseException {
			String varName = def.query().single(varAttrName).value();
			target = varName.equals("target");
			if (target) {
				varName = null;
			} else {
				if (!varName.startsWith("$"))  // TODO: verify variable name syntax
					throw new RuleBaseException("unrecognized for block variable keyword '" + varName + "'");
				varName = varName.substring(1);
			}
			query = new Query.Items(def);	
			variableName = varName == null ? null : query.parseQName(varName);
		}
		
		class ForSeg extends Seg {
			ForSeg(Mod mod) {super(mod);}
			
			void bindVariable(Resource value) throws TransformException {
				if (variableName != null) mod.bindVariable(variableName, value);
			}
			
			@Override public void analyze() throws TransformException {
				requiredVariables = query.analyze(mod.globalScope()).requiredVariables();
				bindVariable(null);
			}
			
		}
		
		class ForEachSeg extends ForSeg implements InsertionTarget, NodeTarget {
			ForEachSeg(Mod mod) {super(mod);}
			
			@Override public void restore() throws TransformException {
				bindVariable(mod.references().get(0));
			}
			
			@Override public void verify() throws TransformException {
				Node node = mod.references().get(0);
				QueryService scope = mod.scope(node.document().query());
				if (!query.runOn(scope).query().single("@xml:id=$_1/@xml:id", node).booleanValue())
					throw new TransformException("query didn't select node with id " + node.query().single("@xml:id").value());
			}
				
			public Node insert(Node node) throws TransformException {
				if (target) return mod.references().get(0).append().node(node).commit();
				return mod.nearest(InsertionTarget.class).insert(node);
			}
			
			public boolean canInsertMultiple() throws TransformException {
				return target ? true : mod.nearest(InsertionTarget.class).canInsertMultiple();
			}
			
			public ItemList targets() throws TransformException {
				if (target) return mod.references().get(0).toItemList();
				return mod.nearest(NodeTarget.class).targets();
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
			assertEquals(new QName(null, "x", null), block.variableName);
			assertFalse(block.target);
		}
		
		@Test
		public void parseForOneBlock() throws RuleBaseException {
			ForBlock block = define("<for one='$x'> //foo </for>");
			assertTrue(block instanceof For.ForOneBlock);
			assertEquals(new QName(null, "x", null), block.variableName);
			assertFalse(block.target);
		}
		
		@Test
		public void parseForAllBlock() throws RuleBaseException {
			ForBlock block = define("<for all='$x'> //foo </for>");
			assertTrue(block instanceof For.ForAllBlock);
			assertEquals(new QName(null, "x", null), block.variableName);
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
		public void parseForAllTargetKeywordFails() throws RuleBaseException {
			define("<for all='target'> //foo </for>");
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
		public void resolveAllDoesNothingOnNoResult() throws RuleBaseException, TransformException {
			ForAllBlock block = define("<for all='$x'> //java:foobar </for>");
			setModBuilderScope(content.query());
			block.resolve(modBuilder);
		}

		@Test
		public void analyzeWorksWithVariable() throws RuleBaseException, TransformException {
			ForOneBlock block = define("<for one='$x'> //java:method[@name=$other] </for>");
			setModGlobalScope(content.query());
			bindVariable(new QName(null, "x", null), null);
			Seg seg = block.createSeg(mod);
			seg.analyze();
			assertThat(block.requiredVariables, is(collection(new QName(null, "other", null))));
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
			bindVariable(new QName(null, "x", null), variableValue);
			block.createSeg(mod).restore();
		}

		@Test
		public void restoreWorksWithKeyword() throws RuleBaseException, TransformException {
			ForOneBlock block = define("<for one='target'> //java:method[@name='start'] </for>");
			setModReferences((Node) null);
			block.createSeg(mod).restore();
		}
		
		@Test
		public void restoreForAllWorks() throws RuleBaseException, TransformException {
			ForAllBlock block = define("<for all='$x'> //java:method </for>");
			ItemList items = content.query().all("for $m in //java:method order by $m/@xml:id return $m");
			setModReferences(items.nodes().toArray());
			setModGlobalScope(content.query());
			bindVariable(new QName(null, "x", null), content.query().all("//java:method"));
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
		public void verifyForAllWorks() throws RuleBaseException, TransformException {
			ForAllBlock block = define("<for all='$x'> //java:method </for>");
			ItemList items = content.query().all("for $m in //java:method order by $m/@xml:id return $m");
			setModReferences(items.nodes().toArray());
			setModScope(content.query());
			block.createSeg(mod).verify();
		}
		
		@Test(expected = TransformException.class)
		public void verifyForAllFails() throws RuleBaseException, TransformException {
			ForAllBlock block = define("<for all='$x'> //java:method[@name='start'] </for>");
			ItemList items = content.query().all("for $m in //java:method order by $m/@xml:id return $m");
			setModReferences(items.nodes().toArray());
			setModScope(content.query());
			block.createSeg(mod).verify();
		}
		
		@Test
		public void insertWithTarget() throws RuleBaseException, TransformException {
			ForOneBlock block = define("<for one='target'> //java:class </for>");
			final Node selectedNode = content.query().single("//java:class").node();
			setModReferences(selectedNode);
			ForBlock.ForEachSeg seg = (ForBlock.ForEachSeg) block.createSeg(mod);
			seg.insert(db.query().single("<java:method xml:id='m3' name='between'/>").node());
			assertTrue(seg.canInsertMultiple());
			assertTrue(selectedNode.query().exists("java:method[@xml:id='m3']"));
		}

		@Test
		public void insertNotTarget() throws RuleBaseException, TransformException {
			ForOneBlock block = define("<for one='$x'> //java:class </for>");
			final Node nodeToInsert = db.query().single("<bar/>").node(), insertedNode = db.query().single("<foo/>").node();
			InsertionTarget insertionTarget = new InsertionTarget() {
				public Node insert(Node node) throws TransformException {
					assertSame(nodeToInsert, node);
					return insertedNode;
				}
				public boolean canInsertMultiple() throws TransformException {
					return false;
				}
			};
			setModNearestAncestorImplementing(InsertionTarget.class, insertionTarget);
			ForBlock.ForEachSeg seg = (ForBlock.ForEachSeg) block.createSeg(mod);
			assertSame(insertedNode, seg.insert(nodeToInsert));
			assertFalse(seg.canInsertMultiple());
		}

		@Test
		public void targetNodesWithTarget() throws RuleBaseException, TransformException {
			ForOneBlock block = define("<for one='target'> //java:class </for>");
			final Node selectedNode = content.query().single("//java:class").node();
			setModReferences(selectedNode);
			assertEquals(Collections.singletonList(selectedNode), ((ForBlock.ForEachSeg) block.createSeg(mod)).targets().asList());
		}

		@Test
		public void targetNodesNotTarget() throws RuleBaseException, TransformException {
			ForOneBlock block = define("<for one='$x'> //java:class </for>");
			final Node selectedNode = content.query().single("//java:class").node();
			NodeTarget insertionTarget = new NodeTarget() {
				public ItemList targets() throws TransformException {
					return selectedNode.toItemList();
				}
			};
			setModNearestAncestorImplementing(NodeTarget.class, insertionTarget);
			assertEquals(Collections.singletonList(selectedNode), ((ForBlock.ForEachSeg) block.createSeg(mod)).targets().asList());
		}

	}
}
