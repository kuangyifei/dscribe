package com.ideanest.dscribe.mixt;


public abstract class Seg {
	
	public final Mod mod;
	
	public Seg(Mod mod) {
		this.mod = mod;
	}

	public void analyze() throws TransformException {}
	public void restore() throws TransformException {}
	public void verify() throws TransformException {}

}
