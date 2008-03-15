package com.ideanest.dscribe.mixt.blocks;

import static org.junit.Assert.*;

import java.util.*;

import org.exist.fluent.*;
import org.junit.Test;

import com.ideanest.dscribe.mixt.*;
import com.ideanest.dscribe.testutil.BlockTestCase;

public class With implements BlockType {

	public QName xmlName() {
		return new QName(Engine.RULES_NS, "with", null);
	}

	public String version() {
		return "1";
	}

	@AllowAttributes({"some", "any"})
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
			if (!variableName.startsWith("$")) throw new RuleBaseException("illegal with block variable name '" + variableName + "'");
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
	
	@Deprecated public static class _Test extends BlockTestCase {
		@Test(expected = RuleBaseException.class)
		public void parseNoVariableAttribute() throws RuleBaseException {
			define("<with> //foo </with>");
		}

		@Test(expected = RuleBaseException.class)
		public void parseMultipleVariableAttributes() throws RuleBaseException {
			define("<with any='$x' some='$x'> //foo </with>");
		}
		
		@Test
		public void parseAny() throws RuleBaseException {
			WithBlock block = define("<with any='$x'> //foo </with>");
			assertTrue(block.optional);
			assertEquals("$x", block.variableName);
		}
		
		@Test
		public void parseSome() throws RuleBaseException {
			WithBlock block = define("<with some='$x'> //foo </with>");
			assertFalse(block.optional);
			assertEquals("$x", block.variableName);
		}

		@Test(expected = RuleBaseException.class)
		public void parseBadVariableName() throws RuleBaseException {
			define("<with any='target'> //foo </with>");
		}
		
		@Test
		public void resolveAny() throws RuleBaseException, TransformException {
			WithBlock block = define("<with any='$x'> $y/foo </with>");
			block.requiredVariables = Collections.singletonList("$y");
			dependOnUnverifiedVariables("$y");
			thenCommit();
			block.resolve(modBuilder);
		}

		@Test
		public void resolveSome() throws RuleBaseException, TransformException {
			WithBlock block = define("<with some='$x'> //java:method </with>");
			block.requiredVariables = Collections.emptyList();
			setModBuilderScope(content.query());
			dependOnUnverifiedVariables();
			thenCommit();
			block.resolve(modBuilder);
		}
		
		@Test
		public void analyze() throws RuleBaseException, TransformException {
			WithBlock block = define("<with any='$x'> $y/foo </with>");
			setModGlobalScope(content.query());
			bindVariable("$x", null);
			block.createSeg(mod).analyze();
		}
		
		@Test
		public void restoreAny() throws RuleBaseException, TransformException {
			WithBlock block = define("<with any='$x'> //java:method </with>");
			setModScope(content.query());
			bindVariable("$x", content.query().all("//java:method"));
			block.createSeg(mod).restore();
		}

		@Test(expected = TransformException.class)
		public void restoreSomeMissing() throws RuleBaseException, TransformException {
			WithBlock block = define("<with some='$x'> //java:method[@name='foobar'] </with>");
			setModScope(content.query());
			block.createSeg(mod).restore();
		}
	}
}
