package com.ideanest.dscribe.mixt;

import java.lang.annotation.*;

import org.exist.fluent.*;

public interface BlockType {

	QName xmlName();
	
	String version();
	
	Block define(Node def) throws RuleBaseException;
	
	@Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME)
	public @interface AllowAttributes {
		String[] value();
	}

}
