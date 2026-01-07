package io.oz.jservapp;

import io.odysz.anson.Anson;

/**
 * @since 1.5.7
 */
public class JservSettings extends Anson {
	
	public static final String json0 = "jserv.json";

	/**
	 * E.g. jserv-album, jserv-sample or regist-central
	 */
	public String protocolRoot;

	/**
	 * Relative path/filename to jserv.json.
	 */
	protected String jserv_json;
	
	/**
	 * JservMenu configurations
	 */
	protected JMenuSettings jmenu;

	public String app_name;
	public String conn;
	public String logconn;
	
	public JservSettings() { super(); }
}
