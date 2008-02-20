package com.ideanest.dscribe.mixt;

import static org.junit.Assert.*;

import java.util.*;

import org.exist.fluent.*;
import org.jmock.Expectations;
import org.junit.*;



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
		if (key == null) throw new IllegalArgumentException("null key");
		this.key = key;
		variableBindings = new HashMap<String,Object>(parent.variableBindings());
	}
	
	@Override Map<String,Object> variableBindings() {
		return variableBindings;
	}
	
	@Override public String key() {
		return key;
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
	
	@Deprecated public static class _BuilderTest extends Mod._Test {
		private Builder builder;
		
		@Before public void setupBuilder() throws IllegalArgumentException, IllegalAccessException {
			builder = new Builder(parentMod, block, false, resolutionScope);
		}
		
		@Test public void resetsParameterFields() {
			builder.reset();
			assertNull(builder.keySuffix);
		}
		
		@Test public void createChildNoKeys() {
			Mod child = builder.createChild();
			assertSame(parentMod, child.parent);
			assertEquals("_r1.e13.-.", child.key());
		}
		
		@Test public void createChildRefKey() {
			builder.referenceKey(doc1.query().single("//e1").node());
			Mod child = builder.createChild();
			assertSame(parentMod, child.parent);
			assertEquals("_r1.e13.e1.", child.key());
		}
		
		@Test public void referenceKey() {
			builder.referenceKey(doc1.query().single("//e1").node());
			assertEquals("e1.", builder.keySuffix);
		}

		@Test(expected = IllegalStateException.class)
		public void referenceKeyTwice() {
			builder.referenceKey(doc1.query().single("//e1").node());
			builder.referenceKey(doc1.query().single("//e1").node());
		}

		@Test public void commitTwice() throws TransformException {
			final Seg seg1 = mockery.mock(Seg.class, "seg1");
			final Seg seg2 = mockery.mock(Seg.class, "seg2");
			mockery.checking(new Expectations() {{
				exactly(2).of(parentMod).node();  will(returnValue(modStore));
				exactly(2).of(block).createSeg(with(any(Mod.class)));
					will(onConsecutiveCalls(returnValue(seg1), returnValue(seg2)));
				one(seg1).restore();
				one(seg2).restore();
			}});
			builder.commit();
			builder.referenceKey(doc1.query().single("//e1").node());
			builder.commit();
			assertNull(builder.keySuffix);
		}

	}
	
	@Deprecated public static class _ModTest extends Mod._Test {
		@Test public void constructorFromRule() {
			KeyMod mod = new KeyMod(rule);
			assertSame(rule, mod.rule);
			assertEquals(-1, mod.stage);
			assertNull(mod.parent);
			assertEquals("_r1.", mod.key);
			assertTrue(mod.variableBindings.isEmpty());
		}
		
		@Test public void constructorFromParentMod() {
			parentModBindings.put("a", "foo");
			KeyMod mod = new KeyMod(parentMod, "_r1.e13.g23.");
			assertSame(rule, mod.rule);
			assertEquals(4, mod.stage);
			assertSame(parentMod, mod.parent);
			assertEquals("_r1.e13.g23.", mod.key);
			assertEquals(parentMod.variableBindings(), mod.variableBindings);
			assertNotSame(parentMod.variableBindings(), mod.variableBindings);
		}
		
		@Test(expected = IllegalArgumentException.class)
		public void constructorFromParentNullKey() {
			new KeyMod(parentMod, null);
		}
}

}
