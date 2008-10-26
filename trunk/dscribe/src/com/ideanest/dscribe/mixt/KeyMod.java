package com.ideanest.dscribe.mixt;

import static org.junit.Assert.*;

import java.util.*;

import org.exist.fluent.*;
import org.jmock.Expectations;
import org.junit.*;



public class KeyMod extends Mod {
	
	private final Map<String, Resource> variableBindings;
	private String key;

	KeyMod(Rule rule) {
		super(rule);
		this.key = "_" + rule.id + ".";
		this.variableBindings = new HashMap<String, Resource>();
	}
	
	KeyMod(Mod parent, String key) {
		super(parent);
		if (key == null) throw new IllegalArgumentException("null key");
		this.key = key;
		variableBindings = new HashMap<String, Resource>(parent.variableBindings());
	}
	
	@Override Map<String, Resource> variableBindings() {
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
			if (keySuffix != null) throw new IllegalStateException("block cannot have more than one key");
			keySuffix = node.query().single("@xml:id").value() + ".";
		}
		
		public void setKey(String rawKey) {
			if (keySuffix != null) throw new IllegalStateException("block cannot have more than one key");
			keySuffix = sanitizeKey(rawKey) + ".";
		}
		
		private String sanitizeKey(String rawKey) {
			StringBuilder key = new StringBuilder();
			for (int offset = 0; offset < rawKey.length();) {
				int c = rawKey.codePointAt(offset);
				if (Character.isLetterOrDigit(c)) {
					key.appendCodePoint(c);
				} else if (c == '-') {
					key.append("--");
				} else {
					assert c >= 0;
					key.append('-').append(Integer.toString(c, 16)).append('-');  // stringify codepoint to its int value
				}
				offset += Character.charCount(c);
			}
			return key.toString();
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
		
		@Test public void sanitizeKeyWithoutSpecialChars() {
			assertEquals("foo1234BAR", builder.sanitizeKey("foo1234BAR"));
		}

		@Test public void sanitizeKeyWithSpecialChars() {
			assertEquals("foo--bar-5f-test-2e-1-23-2", builder.sanitizeKey("foo-bar_test.1#2"));
		}

		@Test public void sanitizeKeyWithAstralChars() {
			assertEquals("f-2780-\u041f-482-xxx\ud800\udf46", builder.sanitizeKey("f\u2780\u041f\u0482xxx\ud800\udf46"));
		}

		@Test public void commitTwice() throws TransformException {
			final Seg seg1 = mockery.mock(Seg.class, "seg1");
			final Seg seg2 = mockery.mock(Seg.class, "seg2");
			mockery.checking(new Expectations() {{
				allowing(parentMod).node();  will(returnValue(modStore));
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
			parentModBindings.put("a", doc1);
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
