package com.ideanest.dscribe.mixt;

import java.util.HashMap;
import java.util.Map;

import org.exist.fluent.*;


public class KeyMod extends Mod {
	
	private final Map<String,Object> variableBindings;
	private String key;

	KeyMod(Rule rule) {
		super(rule);
		this.key = "_" + rule.id + ".";
		this.variableBindings = new HashMap<String,Object>();
	}
	
	KeyMod(Mod parent, String key) {
		super(parent);
		this.key = key;
		variableBindings = new HashMap<String,Object>(parent.variableBindings());
	}
	
	@Override Map<String,Object> variableBindings() {
		return variableBindings;
	}
	
	@Override public String key() {
		return key;
	}
	
	@Override void writeAncestors(ElementBuilder<?> builder) {
		builder.elem("ancestor").attr("refid", key()).end("ancestor");
		super.writeAncestors(builder);
	}
	
	@Override public String toString() {
		return super.toString().replace(".mod[", ".keymod[");
	}
	
	public static class Builder extends Mod.Builder {
		String keySuffix;
		
		Builder(Mod parent, Block block, boolean lastBlock, QueryService scope) {
			super(parent, block, lastBlock, scope);
		}
		
		public void referenceKey(Node node) {
			super.reference(node);
			if (keySuffix != null) throw new IllegalStateException("block cannot reference more than one key");
			keySuffix = node.query().single("@xml:id").value() + ".";
		}
		
		@Override void checkChildrenSize() {
			// relax super limitations, don't check children size
		}
		
		@Override KeyMod createChild() {
			if (keySuffix == null) keySuffix = "-.";
			String key = parent.key() + keySuffix;
			return new KeyMod(parent, key);
		}
		
		@Override void reset() {
			keySuffix = null;
			super.reset();
		}
		
	}
}
