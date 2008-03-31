package com.ideanest.dscribe.mixt.blocks;

import org.exist.fluent.Node;

import com.ideanest.dscribe.mixt.TransformException;

public interface InsertionTarget {

	Node insert(Node node) throws TransformException;
	boolean canInsertMultiple() throws TransformException;
	
}
