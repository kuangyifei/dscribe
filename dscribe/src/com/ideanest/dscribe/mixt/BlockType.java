package com.ideanest.dscribe.mixt;

import org.exist.fluent.*;

public interface BlockType {

	QName xmlName();
	
	String version();
	
	Block define(Node def) throws RuleBaseException;

}
