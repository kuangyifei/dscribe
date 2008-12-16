package com.ideanest.dscribe.mixt;

import org.exist.fluent.QueryService;


public abstract class Seg {
	
	public final Mod mod;
	
	public Seg(Mod mod) {
		this.mod = mod;
	}

	public QueryService.QueryAnalysis analyze() throws TransformException {return null;}
	public void restore() throws TransformException {}
	public void verify() throws TransformException {}

}
