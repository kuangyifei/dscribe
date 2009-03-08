package com.ideanest.dscribe.mixt;

import static org.junit.Assert.*;

import java.util.*;

import org.exist.fluent.*;
import org.jmock.Expectations;
import org.junit.*;



public class KeyMod extends Mod {
	
	private final Map<QName, Resource> variableBindings;
	private String key;

	KeyMod(Rule rule) {
		super(rule);
		this.key = "_" + rule.id + ".";
		this.variableBindings = new HashMap<QName, Resource>();
	}
	
	KeyMod(Mod parent, String key) {
		super(parent);
		if (key == null) throw new IllegalArgumentException("null key");
		this.key = key;
		variableBindings = new HashMap<QName, Resource>(parent.variableBindings());
	}
	
	@Override Map<QName, Resource> variableBindings() {
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
		
		@Override void checkChildCount() {
			// relax super limitations, don't check children size
		}
		
		@Override String key() {
			if (keySuffix == null) keySuffix = "-.";
			return parent.key() + keySuffix;
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
		
		@Test public void keyNoKeys() {
			String key = builder.key();
			assertEquals("_r1.e13.-.", key);
		}
		
		@Test public void keyRefKey() {
			builder.referenceKey(doc1.query().single("//e1").node());
			String key = builder.key();
			assertEquals("_r1.e13.e1.", key);
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
			final Mod[] children = new Mod[]{mockery.mock(Mod.class, "mod1"), mockery.mock(Mod.class, "mod2")};
			mockery.checking(new Expectations() {{
				allowing(parentMod).node();  will(returnValue(modStore));
				one(parentMod).restoreChild(with(equal(block)), with(any(Node.class)));
				will(returnValue(children[0]));
				one(parentMod).restoreChild(with(equal(block)), with(any(Node.class)));
				will(returnValue(children[1]));
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
			parentModBindings.put(new QName(null, "a", null), doc1);
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
