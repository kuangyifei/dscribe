package com.ideanest.dscribe.mixt.blocks;

import java.util.*;

import org.exist.fluent.*;

import com.ideanest.dscribe.Namespace;
import com.ideanest.dscribe.mixt.*;

public class With implements BlockType {

	public QName xmlName() {
		return new QName(Namespace.RULES, "with", null);
	}

	public Block define(Node def) throws RuleBaseException {
		boolean some = def.query().exists("@some"), any = def.query().exists("@any");
		if (some && any) throw new RuleBaseException("with block has both @some and @any");
		if (!(some || any)) throw new RuleBaseException("with block has neither @some nor @any");
		return new WithBlock(def, any);
	}

	private static class WithBlock implements LinearBlock {
		private final String variableName;
		private final boolean optional;
		private final Query.Items query;
		private Collection<String> requiredVariables;
		
		private WithBlock(Node def, boolean optional) throws RuleBaseException {
			variableName = def.query().single(optional ? "@any" : "@some").value();
			if (!variableName.startsWith("$"))  // TODO: verify variable name syntax
				throw new RuleBaseException("illegal with block variable name '" + variableName + "'");
			this.optional = optional;
			query = new Query.Items(def);
		}

		public void resolve(Mod.Builder modBuilder) throws TransformException {
			if (optional || query.runExists(modBuilder.scope())) {
				modBuilder.dependOn(requiredVariables).unverified();
				modBuilder.commit();
			}
		}
		
		public Seg createSeg(Mod mod) {return new WithSeg(mod);}

		private class WithSeg extends Seg {
			WithSeg(Mod mod) {super(mod);}
			
			@Override public void analyze() throws TransformException {
				requiredVariables = query.analyze(mod.globalScope()).requiredVariables();
				mod.bindVariable(variableName, null);
			}
			
			@Override public void restore() throws TransformException {
				ItemList items = query.runOn(mod.scope(null));
				if (!optional && items.size() == 0) throw new TransformException("restoring non-optional with block got empty result");
				mod.bindVariable(variableName, items);
			}
			
		}
	}
}
