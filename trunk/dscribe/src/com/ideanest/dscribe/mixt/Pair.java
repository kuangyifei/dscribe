package com.ideanest.dscribe.mixt;

public class Pair<E1, E2> {
	public E1 first;
	public E2 second;
	
	public Pair(E1 first, E2 second) {
		this.first = first;
		this.second = second;
	}
	
	public static <E1, E2> Pair<E1, E2> of(E1 first, E2 second) {
		return new Pair<E1, E2>(first, second);
	}
	
	@Override public boolean equals(Object o) {
		if (o.getClass() != Pair.class) return false;
		Pair<?,?> that = (Pair<?,?>) o;
		return (this.first == null ? that.first == null : this.first.equals(that.first)) &&
				(this.second == null ? that.second == null : this.second.equals(that.second));
	}
	
	@Override public int hashCode() {
		return (first == null ? 0 : first.hashCode()) ^ (second == null ? 0 : second.hashCode());
	}
}
