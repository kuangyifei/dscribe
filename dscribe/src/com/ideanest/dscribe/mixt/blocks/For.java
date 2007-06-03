package com.ideanest.dscribe.mixt.blocks;

import java.util.Collection;

import org.exist.fluent.*;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.mixt.*;

public class For implements BlockType {
	
	public QName xmlName() {
		return new QName(Namespace.RULES, "for", null);
	}

	public Block define(Node def, Rule rule) throws RuleBaseException {
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
			if (!varName.startsWith("$")) throw new RuleBaseException("unrecognized for-block variable keyword '" + varName + "'");
			if (target) varName = null;
			variableName = varName;
			query = new Query.Items(def);	
		}
		
		public Helper createHelper(Mod mod) {
			return new ForBlockHelper(mod);
		}
		
		private class ForBlockHelper extends Helper implements InsertionTarget {
			ForBlockHelper(Mod mod) {super(mod);}
			
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
		
	}

}
