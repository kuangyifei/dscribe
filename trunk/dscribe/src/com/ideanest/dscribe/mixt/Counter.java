package com.ideanest.dscribe.mixt;

import java.text.MessageFormat;

// TODO: make available to other modules, this is useful
class Counter {
	private long value;
	private final MessageFormat messageFormat;
	
	public Counter(String pattern) {
		messageFormat = new MessageFormat(pattern);
	}
	
	public long value() {return value;}
	
	public void increment() {value++;}
	public void increment(long increment) {value += increment;}
	
	@Override public String toString() {
		return messageFormat.format(new Object[]{value});
	}
	
	public String format(long count) {
		return messageFormat.format(new Object[]{count});
	}
	
	public static Counter english(String singular, String plural, String verb) {
		return new Counter(
				"{1,choice,0#no "+plural+"|1#1 "+singular+"|1<{1,number,integer} "+plural+"}"
				+ (verb == null ? "" : " " + verb));
	}

}
