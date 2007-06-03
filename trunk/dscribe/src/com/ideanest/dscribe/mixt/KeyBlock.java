package com.ideanest.dscribe.mixt;

public interface KeyBlock extends Block {
	
	void resolve(KeyMod.Builder modBuilder) throws TransformException;

}
