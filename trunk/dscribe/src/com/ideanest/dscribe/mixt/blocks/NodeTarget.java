package com.ideanest.dscribe.mixt.blocks;

import org.exist.fluent.ItemList;

import com.ideanest.dscribe.mixt.TransformException;

public interface NodeTarget {
	
	ItemList targets() throws TransformException;

}
