package io.odysz.semantic.jprotocol;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.joinurl;
import static io.odysz.common.Regex.asJserv;
import static io.odysz.common.Regex.getJservParts;
import static io.odysz.common.Regex.validUrlPort;

import io.odysz.common.UrlValidator;
import io.odysz.common.Utils;

import static io.odysz.common.LangExt.concatArr;

/**
 * <p>The jserv parser and composer. </p>
 * 
 * TODO It is reasonable to move this to the protocol layer,
 * when time is allowed for refactoring Typescript and py3 client.
 * @since 0.2.5
 */
public class JServUrl {
	static UrlValidator urlValidator;

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
	
	/**
	 * Validate jserv's format:
	 * - a valid url<br>
	 * - requirs a path root, e.g. jserv-alubm<br>
	 * - port greater then 1024<br>
	 * @param jserv
	 * @return
	 */
	public static boolean valid(String jserv) {
		if (urlValidator == null)
			urlValidator = new UrlValidator();

		try {
			if (!urlValidator.isValid(jserv))
				return false;

			Object[] jservparts = getJservParts(jserv);
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
}
