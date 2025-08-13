package io.odysz.semantic.jprotocol;

import static io.odysz.common.LangExt.joinurl;
import static io.odysz.common.LangExt.concatArr;

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
	public JServUrl subpaths(String... subs) {
		subpaths = concatArr(subpaths, subs);
		return this;
	}
	
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
		return joinurl(https, ip, port, JProtocol.urlroot, subpaths);
	}
}
