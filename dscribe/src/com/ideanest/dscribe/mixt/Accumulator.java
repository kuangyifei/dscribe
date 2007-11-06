package com.ideanest.dscribe.mixt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.*;

import org.junit.Before;
import org.junit.Test;

class Accumulator<E> {
	
	private static class Cell<E> {
		private Set<E> contents = new HashSet<E>();
		private Cell<E> next;
	}
	
	static class Locator<E> {
		private Cell<E> ref;
		private Locator(Cell<E> ref) {this.ref=ref;}
		public Set<E> catchUp() {
			Set<E> result;
			if (ref.next == null) {
				if (ref.contents.isEmpty()) return Collections.emptySet();
				result = Collections.unmodifiableSet(ref.contents);
			} else {
				result = new HashSet<E>();
				while(true) {
					result.addAll(ref.contents);
					if (ref.next == null) break;
					ref = ref.next;
				}
			}
			ref.next = new Cell<E>();
			ref = ref.next;
			return result;
		}		
	}
	
	private Cell<E> head;
	
	public Accumulator() {
		this(new Cell<E>());
	}
	
	private Accumulator(Cell<E> head) {
		this.head = head;
	}
	
	private void update() {
		while (head.next != null) head = head.next;
	}
	
	public void add(E element) {
		update();
		head.contents.add(element);
	}
	
	public void addAll(Collection<E> elements) {
		update();
		head.contents.addAll(elements);
	}
	
	public Locator<E> anchor() {
		update();
		if (!head.contents.isEmpty()) {
			head.next = new Cell<E>();
			head = head.next;
		}
		return new Locator<E>(head);
	}

	@Deprecated public static class _Test {
		private Accumulator<String> acc;
		@Before public void setUp() {
			acc = new Accumulator<String>();
		}
		@Test public void test1() {
			Locator<String> loc = acc.anchor();
			acc.add("a");
			assertEquals(Collections.singleton("a"), loc.catchUp());
			assertTrue(loc.catchUp().isEmpty());
		}
		@Test public void test2() {
			acc.add("a");
			Locator<String> loc = acc.anchor();
			assertTrue(loc.catchUp().isEmpty());
		}
		@Test public void test3() {
			acc.add("a");
			Locator<String> loc = acc.anchor();
			acc.add("b");
			assertEquals(Collections.singleton("b"), loc.catchUp());
		}
		@Test public void test4() {
			Locator<String> loc1 = acc.anchor();
			acc.add("a");
			Locator<String> loc2 = acc.anchor();
			acc.add("b");
			assertEquals(new HashSet<String>(Arrays.asList(new String[]{"a", "b"})), loc1.catchUp());
			assertEquals(Collections.singleton("b"), loc2.catchUp());
		}
		@Test public void test5() {
			Locator<String> loc1 = acc.anchor();
			acc.add("a");
			Locator<String> loc2 = acc.anchor();
			acc.add("b");
			acc.add("a");
			assertEquals(new HashSet<String>(Arrays.asList(new String[]{"a", "b"})), loc1.catchUp());
			assertEquals(new HashSet<String>(Arrays.asList(new String[]{"a", "b"})), loc2.catchUp());
		}
		@Test public void test6() {
			Locator<String> loc1 = acc.anchor();
			acc.add("a");
			Locator<String> loc2 = acc.anchor();
			acc.add("b");
			loc1.catchUp();
			acc.add("c");
			assertEquals(new HashSet<String>(Arrays.asList(new String[]{"c", "b"})), loc2.catchUp());
			assertEquals(Collections.singleton("c"), loc1.catchUp());
		}
	}
}
