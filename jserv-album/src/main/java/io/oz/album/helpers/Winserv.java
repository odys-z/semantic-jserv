package io.oz.album.helpers;

import java.util.HashMap;

import io.odysz.common.Configs;

public class Winserv {
	/** * */
	public static class keys {
		/** Default xtable id, configs.xml/t[id="default"] */
		public static final String deftXTableId = "winserv";
		public static final String bind = "bind";
		public static final String port = "port";
		public static final String home = "home";
		public static final String volume = "volume";
	}

	protected static HashMap<String, HashMap<String, String>> cfgs;

	private static String cfgFile = "winserv.xml";

	static {
		cfgs = new HashMap<String, HashMap<String, String>>(1);
	}

	/**For redirect path of config.xml
	 * @param xmlDir
	 */
	public static void init(String xml) {
		cfgFile = xml;
		Configs.load(cfgs, xml, keys.deftXTableId);
	}

	public static String v(String key) {
		return cfgs.get(keys.deftXTableId).get(key);
	}

	public static String v(String tid, String k) {
		if (!cfgs.containsKey(tid))
			Configs.load(cfgs, cfgFile, tid);
		return cfgs.get(tid).get(k);
	}

	public static boolean vbool(String key) {
		String isTrue = cfgs.get(keys.deftXTableId).get(key);
		if (isTrue == null) return false;
		isTrue = isTrue.trim().toLowerCase();
		return "true".equals(isTrue) || "1".equals(isTrue) || "y".equals(isTrue) || "yes".equals(isTrue);
	}

	public static int vint(String key, int deflt) {
		String str = cfgs.get(keys.deftXTableId).get(key);
		if (str == null) return deflt;
		str = str.trim().toLowerCase();

		try {
			return Integer.valueOf(str);
		} catch (Exception ex) {
			System.err.println(String.format("Config %s = %s is not an integer (%s)", key, str, ex.getMessage()));
			return deflt;
		}
	}

	public static boolean has(String key) {
		return has(keys.deftXTableId, key);
	}

	public static boolean has(String tid, String key) {
		return v(tid, key) != null;
	}
}
