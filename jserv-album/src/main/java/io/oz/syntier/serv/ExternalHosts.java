package io.oz.syntier.serv;

import java.util.HashMap;

import io.odysz.anson.Anson;

/**
 * If the web pages are not static and needs some data layer configurations,
 * use this to feed data to handler, {@link WebsrvLocalExposer}.
 * 
 * @since 0.7.1
 */
public class ExternalHosts extends Anson {

	public String host;
	public String localip;

	public HashMap<String, String> syndomx;

	public ExternalHosts() {
		syndomx = new HashMap<String, String>();
	}
}
