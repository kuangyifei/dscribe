package com.ideanest.dscribe.mixt.blocks;

import org.exist.fluent.ElementBuilder;

import com.ideanest.dscribe.mixt.TransformException;

public interface InsertionTarget {

	ElementBuilder<?> contentBuilder() throws TransformException;
	
}
