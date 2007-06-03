package com.ideanest.dscribe.mixt;


public abstract class Helper {
	
	public final Mod mod;
	
	public Helper(Mod mod) {
		this.mod = mod;
	}

	public void analyze() throws TransformException {}
	public void restore() throws TransformException {}
	// TODO: replace verify with deep-equals on a re-resolve
	public void verify() throws TransformException {}

}
