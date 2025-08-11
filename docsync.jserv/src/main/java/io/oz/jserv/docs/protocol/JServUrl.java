package io.oz.jserv.docs.protocol;

import io.odysz.common.LangExt;

/**
 * <p>The jserv parser and composer. </p>
 * 
 * TODO It is reasonable to move this to the protocol layer,
 * when time is allowed for refactoring Typescript and py3 client.
 * @since 0.2.5
 */
public class JServUrl {

	public final boolean https;
	public String ip;
	public int port;
	public String[] subpaths;
	
	public JServUrl(boolean ishttps, String ip, int port) {
		https = ishttps;
		this.ip = ip;
		this.port = port;
	}

	public JServUrl ip(String ip) {
		this.ip = ip;
		return this;
	}

	public String jserv() {
		return LangExt.joinUrl(https, ip, port, subpaths);
	}
}
