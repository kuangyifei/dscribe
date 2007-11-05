package com.ideanest.dscribe.mixt;

import org.exist.fluent.Node;
import org.exist.fluent.QName;

public interface BlockType {

	QName xmlName();
	
	Block define(Node def) throws RuleBaseException;

}
