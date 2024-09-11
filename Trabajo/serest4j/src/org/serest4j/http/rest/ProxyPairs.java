package org.serest4j.http.rest;

import java.util.TreeSet;

public class ProxyPairs {

	private final byte[] tokenClave;
	private final TreeSet<String> controllers; 

	public ProxyPairs(byte[] tokenClave) {
		this.tokenClave = tokenClave;
		this.controllers = new TreeSet<String>();
	}
	
	public byte[] getTokenClave() {
		return tokenClave;
	}
	public void addToControllers(String cl) {
		controllers.add(cl);
	}
	public boolean containsController(String cl) {
		return controllers.contains(cl);
	}
	public String listControllers() {
		return controllers.toString();
	}
}
