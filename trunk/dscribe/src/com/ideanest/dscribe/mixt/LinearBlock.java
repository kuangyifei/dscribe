package com.ideanest.dscribe.mixt;


public interface LinearBlock extends Block {
	
	void resolve(Mod.Builder modBuilder) throws TransformException;

}
