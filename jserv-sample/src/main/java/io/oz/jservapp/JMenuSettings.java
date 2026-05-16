package io.oz.jservapp;

import io.odysz.anson.Anson;
import io.odysz.semantic.jprotocol.IPort;

public class JMenuSettings extends Anson {

	public static final String menusk0 = "jserv.sys.menu";

	/**
	 * Semantics Key of menu tree's data set (a semantic-tree),
	 * the reference to semantics.xml.
	 */
	String menusk;

	boolean verbose;

	IPort port;
	
	public JMenuSettings() {
	}
}
