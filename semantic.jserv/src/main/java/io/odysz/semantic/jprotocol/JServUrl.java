package io.odysz.semantic.jprotocol;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.joinurl;
import static io.odysz.common.LangExt.shouldeqs;
import static io.odysz.common.LangExt.mustnonull;
import static io.odysz.common.Regex.asJserv;
import static io.odysz.common.Regex.getHttpParts;
import static io.odysz.common.Regex.validUrlPort;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

import io.odysz.common.LangExt;
import io.odysz.common.UrlValidator;
import io.odysz.common.Utils;

import static io.odysz.common.LangExt._0;
import static io.odysz.common.LangExt.concatArr;

/**
 * <p>The jserv parser and composer. </p>
 * 
 * TODO Refactor Typescript and py3 clients.
 * @since 0.2.5
 */
public class JServUrl {
	static UrlValidator urlValidator;

	boolean https;
	String ip;
	int port;
	String[] subpaths;
	public JServUrl subpaths(String... subs) {
		subpaths = concatArr(subpaths, subs);
		return this;
	}
	
	String jservtime;
	public String jservtime() { return jservtime; }
	public JServUrl jservtime(String utc) {
		jservtime = utc;
		return this;
	}
	
	public JServUrl() {
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
	
	/** @since 0.7.6 */
	public JServUrl jserv(String jurl, String timestamp) {
		Object[] jservparts = getHttpParts(jurl);

		https = (boolean) jservparts[1];
		ip = (String) jservparts[2];

		try { port = (int) jservparts[3]; }
		catch (Exception e) {
			port = Integer.valueOf((String) jservparts[3]); 
		}

		subpaths = (String[]) jservparts[4];
		mustnonull(subpaths);
		shouldeqs(new Object(){}, JProtocol.urlroot, subpaths[0]);
		if (eq(JProtocol.urlroot, subpaths[0]))
			subpaths = LangExt.<String>removele(subpaths, 0);  
		
		jservtime = timestamp;
		return this;
	}

	/**
	 * Validate jserv's format:
	 * - a valid url<br>
	 * - requirs a path root, e.g. jserv-alubm<br>
	 * - port greater then 1024<br>
	 * @param jserv
	 * @return valid or not
	 * @since 0.7.6
	 */
	public static boolean valid(String jserv) {
		if (urlValidator == null)
			urlValidator = new UrlValidator();

		try {
			if (!urlValidator.isValid(jserv))
				return false;

			Object[] jservparts = getHttpParts(jserv);
			return urlValidator.isValid(asJserv(jserv)) &&
				validUrlPort((int)jservparts[3], new int[] {1025, -1}) &&
				eq(JProtocol.urlroot, ((String[]) jservparts[4])[0]);
		}
		catch (Exception e) {
			Utils.warnT(new Object[] {}, "Found invalid jserv: %s,\nerror: %s",
					jserv, e.getMessage());
			return false;
		}
	}
	
	/**
	 * Thanks to https://stackoverflow.com/a/38342964/7362888
	 * @param retries default 11
	 * @return local ip, 127.0.0.1 if is offline (got 0:0:0:0:0:0:0:0:0).
	 */
	public static String getLocalIp(int ... retries) {
	    try(final DatagramSocket socket = new DatagramSocket()) {
	    	boolean succeed = false;
	    	int tried = 0;
	    	while (!succeed && tried++ < _0(retries, 11) + 1)
	    		try {
	    			socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
	    			succeed = true;
	    		} catch (IOException e) {
	    			// starting service at network interface not ready yet
	    			Utils.warn("Network interface is not ready yet? Try again ...");
	    			try {
						Thread.sleep(3000);
					} catch (InterruptedException e1) { }
	    		}

	    	if (socket.getLocalAddress() == null ||
	    		eq(socket.getLocalAddress().getHostAddress(), "0:0:0:0:0:0:0:0"))
	    		return "127.0.0.1";

	    	return socket.getLocalAddress().getHostAddress();
		} catch (SocketException e) {
			return "127.0.0.1";
		}
	}
}
