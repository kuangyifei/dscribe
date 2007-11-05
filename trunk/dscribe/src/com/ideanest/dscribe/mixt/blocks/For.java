package com.ideanest.dscribe.mixt.blocks;

import java.util.Collection;

import org.exist.fluent.*;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.mixt.*;

public class For implements BlockType {
	
	public QName xmlName() {
		return new QName(Namespace.RULES, "for", null);
	}

	public Block define(Node def) throws RuleBaseException {
		boolean each = def.query().exists("@each"), one = def.query().exists("@one");
		if (each && one) throw new RuleBaseException("for block specified with both @each and @one");
		if (!(each || one)) throw new RuleBaseException("for block has neither @each nor @one");
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
					throw new RuleBaseException("unrecognized for-block variable keyword '" + varName + "'");
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
				if (!query.runOn(scope).query().exists("*[@xml:id=$_1/@xml:id]", node))
					throw new TransformException("query didn't select node with id " + node.query().single("@xml:id").value());
			}
				
			public ElementBuilder<?> contentBuilder() throws TransformException {
				if (target) return mod.references().get(0).append();
				return mod.nearestAncestorImplementing(InsertionTarget.class).contentBuilder();
			}
		}
		
		@Deprecated @DatabaseTestCase.ConfigFile("test/conf.xml")
		public static class ParseTest extends DatabaseTestCase {
			private Node rule;
			@Override protected void setUp() {
				Folder rules = db.createFolder("rules");
				rules.namespaceBindings().put("", Namespace.RULES);
				rule = rules.documents().build(Name.create("samplerule")).elem("rule").commit().root();
			}
			public void testEachBlock() throws RuleBaseException {
				Node def = rule.append().elem("for").attr("each", "$x").text("//for[@each]").end("for").commit();
				For.ForBlock block = (For.ForBlock) new For().define(def);
				assertTrue(block instanceof For.ForEachBlock);
				assertEquals("$x", block.variableName);
				assertFalse(block.target);
			}
		}

	}	
}
