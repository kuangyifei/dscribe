package com.ideanest.dscribe.mixt.blocks;

import static org.junit.Assert.*;

import java.util.*;

import org.exist.fluent.*;
import org.junit.Test;

import com.ideanest.dscribe.mixt.*;
import com.ideanest.dscribe.mixt.test.BlockTestCase;

public class With implements BlockType {

	public QName xmlName() {
		return new QName(Engine.RULES_NS, "with", null);
	}

	public String version() {
		return "1";
	}

	@AllowAttributes({"some", "any", "distinct"})
	public Block define(Node def) throws RuleBaseException {
		int numDirectives = def.query().single("count(@some | @any | @distinct)").intValue();
		if (numDirectives > 1) throw new RuleBaseException("with block specified with more than one of @some, @any and @distinct");
		if (numDirectives < 1) throw new RuleBaseException("with block specified without any @some, @any or @distinct");
		if (def.query().exists("@distinct")) return new WithDistinctBlock(def);
		else return new WithAnySomeBlock(def);
	}
	
	private static abstract class WithBlock implements Block {
		final QName variableName;
		final Query.Items query;
		Collection<QName> requiredVariables;

		WithBlock(Node def) throws RuleBaseException {
			String varName = def.query().single("@some | @any | @distinct").value();
			if (!varName.startsWith("$"))  // TODO: verify variable name syntax
				throw new RuleBaseException("illegal with block variable name '" + varName + "'");
			varName = varName.substring(1);
			query = new Query.Items(def);
			variableName = query.parseQName(varName);
		}
		
		private class WithSeg extends Seg {
			WithSeg(Mod mod) {super(mod);}
			
			@Override public void analyze() throws TransformException {
				requiredVariables = query.analyze(mod.globalScope()).requiredVariables();
				mod.bindVariable(variableName, null);
			}
		}
	}

	private static class WithAnySomeBlock extends WithBlock implements LinearBlock {
		private final boolean optional;
		
		private WithAnySomeBlock(Node def) throws RuleBaseException {
			super(def);
			optional = def.query().exists("@any");
		}

		public void resolve(Mod.Builder modBuilder) throws TransformException {
			if (optional || query.runExists(modBuilder.scope())) {
				modBuilder.dependOn(requiredVariables).unverified();
				modBuilder.commit();
			}
		}
		
		public Seg createSeg(Mod mod) {return new WithAnySomeSeg(mod);}

		private class WithAnySomeSeg extends WithBlock.WithSeg {
			WithAnySomeSeg(Mod mod) {super(mod);}
			
			@Override public void restore() throws TransformException {
				ItemList items = query.runOn(mod.scope(null));
				if (!optional && items.size() == 0) throw new TransformException("restoring non-optional with block got empty result");
				mod.bindVariable(variableName, items);
			}
			
		}
	}
	
	private static class WithDistinctBlock extends WithBlock implements KeyBlock {
		private WithDistinctBlock(Node def) throws RuleBaseException {
			super(def);
		}
		
		public void resolve(KeyMod.Builder modBuilder) throws TransformException {
			ItemList values = modBuilder.scope().unordered("distinct-values($_1)", query.runOn(modBuilder.scope()));
			for (Item value : values) {
				String atomizedValue = value.value();
				modBuilder.dependOn(requiredVariables);
				modBuilder.supplement()
						.elem("distinct-value").attr("type", value.type()).text(atomizedValue).end("distinct-value");
				modBuilder.setKey(atomizedValue);
				modBuilder.commit();
			}
		}
		
		public Seg createSeg(Mod mod) {return new WithDistinctSeg(mod);}
		
		private class WithDistinctSeg extends WithBlock.WithSeg {
			private Item value;
			
			WithDistinctSeg(Mod mod) {super(mod);}

			@Override public void restore() throws TransformException {
				String type = mod.supplementQuery().single("distinct-value/@type").value();
				if (type.equals("xs:anyAtomicType") || type.equals("xdt:anyAtomicType") || type.equals("xs:NOTATION")) {
					value = mod.supplementQuery().single("distinct-value/text()");
				} else {
					value = mod.supplementQuery().presub().single("$1(distinct-value/text())", type);
				}
				mod.bindVariable(variableName, value);
			}
			
			@Override public void verify() throws TransformException {
				// A direct eq comparison would error out on type mismatch, and an = comparison
				// has different type coercion semantics, so we use distinct-values here to ensure
				// the same comparison semantics as in resolve().
				if (!mod.globalScope().single(
						"count(distinct-values(($_1, $_2))) eq count($_1)",
						query.runOn(mod.scope(null)), value).booleanValue()) {
					throw new TransformException("query didn't select value " + value);
				}
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
			define("<with any='$x' some='$x' distinct='$x'> //foo </with>");
		}
		
		@Test
		public void parseAny() throws RuleBaseException {
			WithAnySomeBlock block = define("<with any='$x'> //foo </with>");
			assertTrue(block.optional);
			assertEquals(new QName(null, "x", null), block.variableName);
		}
		
		@Test
		public void parseSome() throws RuleBaseException {
			WithAnySomeBlock block = define("<with some='$x'> //foo </with>");
			assertFalse(block.optional);
			assertEquals(new QName(null, "x", null), block.variableName);
		}

		@Test
		public void parseDistinct() throws RuleBaseException {
			WithDistinctBlock block = define("<with distinct='$x'> //foo </with>");
			assertEquals(new QName(null, "x", null), block.variableName);
		}
		
		@Test(expected = RuleBaseException.class)
		public void parseBadVariableName() throws RuleBaseException {
			define("<with any='target'> //foo </with>");
		}
		
		@Test
		public void resolveAny() throws RuleBaseException, TransformException {
			WithAnySomeBlock block = define("<with any='$x'> $y/foo </with>");
			block.requiredVariables = Collections.singletonList(new QName(null, "y", null));
			dependOnUnverifiedVariables(new QName(null, "y", null));
			thenCommit();
			block.resolve(modBuilder);
		}

		@Test
		public void resolveSome() throws RuleBaseException, TransformException {
			WithAnySomeBlock block = define("<with some='$x'> //java:method </with>");
			block.requiredVariables = Collections.emptyList();
			setModBuilderScope(content.query());
			dependOnUnverifiedVariables();
			thenCommit();
			block.resolve(modBuilder);
		}
		
		@Test
		public void resolveDistinct() throws RuleBaseException, TransformException {
			WithDistinctBlock block = define("<with distinct='$x'>$y/java:method/@name/string()</with>");
			block.requiredVariables = Collections.singletonList(new QName(null, "y", null));
			dependOnVariables(new QName(null, "y", null));
			supplement();
			setKey("start");
			thenCommit();
			dependOnVariables(new QName(null, "y", null));
			supplement();
			setKey("other");
			thenCommit();
			dependOnVariables(new QName(null, "y", null));
			supplement();
			setKey("end");
			thenCommit();
			setModBuilderScope(content.query().let("$y", content.query().single("//java:class")));
			block.resolve(keyModBuilder);
			checkSupplement("(<distinct-value type='xs:string'>start</distinct-value>, <distinct-value type='xs:string'>other</distinct-value>, <distinct-value type='xs:string'>end</distinct-value>)");
		}

		@Test
		public void analyze() throws RuleBaseException, TransformException {
			WithBlock block = define("<with any='$x'> $y/foo </with>");
			setModGlobalScope(content.query());
			bindVariable(new QName(null, "x", null), null);
			block.createSeg(mod).analyze();
		}
		
		@Test
		public void restoreAny() throws RuleBaseException, TransformException {
			WithBlock block = define("<with any='$x'> //java:method </with>");
			setModScope(content.query());
			bindVariable(new QName(null, "x", null), content.query().all("//java:method"));
			block.createSeg(mod).restore();
		}

		@Test(expected = TransformException.class)
		public void restoreSomeMissing() throws RuleBaseException, TransformException {
			WithBlock block = define("<with some='$x'> //java:method[@name='foobar'] </with>");
			setModScope(content.query());
			block.createSeg(mod).restore();
		}
		
		@Test
		public void restoreDistinct() throws TransformException, RuleBaseException {
			WithBlock block = define("<with distinct='$x'>//java:method/@name/string()</with>");
			setModData("<distinct-value type='xs:string'>start</distinct-value>");
			bindVariable(new QName(null, "x", null), content.query().single("'start'"));
			block.createSeg(mod).restore();
		}
		
		@Test
		public void verifyDistinctSuccess() throws TransformException, RuleBaseException {
			WithBlock block = define("<with distinct='$x'>//java:method/@name/string()</with>");
			WithDistinctBlock.WithDistinctSeg seg = (WithDistinctBlock.WithDistinctSeg) block.createSeg(mod);
			seg.value = content.query().single("'start'");
			setModScope(content.query());
			setModGlobalScope(content.query());
			seg.verify();
		}
		
		@Test(expected = TransformException.class)
		public void verifyDistinctFailure() throws TransformException, RuleBaseException {
			WithBlock block = define("<with distinct='$x'>//java:method/@name/string()</with>");
			WithDistinctBlock.WithDistinctSeg seg = (WithDistinctBlock.WithDistinctSeg) block.createSeg(mod);
			seg.value = content.query().single("10");
			setModScope(content.query());
			setModGlobalScope(content.query());
			seg.verify();
		}

	}
}
