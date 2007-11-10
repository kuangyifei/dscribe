package com.ideanest.dscribe.testutil;

import java.util.Collection;

import org.hamcrest.*;

public class Matchers {

	public static <T> Matcher<Collection<T>> collection(T... contents) {
		return new CollectionMatcher<T>(contents);
	}

	public static <T> Matcher<Collection<T>> emptyCollectionOf(Class<T> clazz) {
		return new EmptyCollectionOfMatcher<T>(clazz);
	}
	
	public static class CollectionMatcher<T> extends BaseMatcher<Collection<T>> {
		private final Object[] contents;
		public CollectionMatcher(Object[] contents) {
			this.contents = contents;
		}
		public boolean matches(Object o) {
			if (!(o instanceof Collection)) return false;
			Collection<?> collection = Collection.class.cast(o);
			if (collection.size() != contents.length) return false;
			for (Object item : contents) if (!collection.contains(item)) return false;
			return true;
		}
		public void describeTo(Description description) {
			description.appendText("hasContents").appendValueList("([", ",", "])", contents);
		}
	}
	
	public static class EmptyCollectionOfMatcher<T> extends BaseMatcher<Collection<T>> {
		private final Class<T> clazz;
		public EmptyCollectionOfMatcher(Class<T> clazz) {
			this.clazz = clazz;
		}
		public boolean matches(Object o) {
			if (o instanceof Collection) {
				return ((Collection<?>) o).isEmpty();
			} else {
				return false;
			}
		}
		public void describeTo(Description description) {
			description.appendText("emptyCollectionOf(" + clazz.getName() + ".class)");
		}
	}

}
