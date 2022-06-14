package io.oz.album.tier;

import io.odysz.anson.Anson;

public class Profiles extends Anson {

	String home;
	public String home() { return home; }

	int maxUsers;
	public int maxusers() { return maxUsers; }
	
	int servtype;
	public int servtype() { return servtype; }

	public Profiles() {}
	
	public Profiles(String home) {
		this.home = home;
	}
}
