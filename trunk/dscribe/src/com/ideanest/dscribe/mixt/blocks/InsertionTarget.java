package com.ideanest.dscribe.mixt.blocks;

import org.exist.fluent.Node;

import com.ideanest.dscribe.mixt.*;

public interface InsertionTarget {

	Node insert(Node node, Mod.Builder builder) throws TransformException;
	boolean canInsertMultiple() throws TransformException;
	
}
