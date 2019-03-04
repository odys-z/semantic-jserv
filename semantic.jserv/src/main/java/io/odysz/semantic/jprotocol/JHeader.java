package io.odysz.semantic.jprotocol;

public class JHeader {

	String uid;
	String ssid;
	String iv64;
	String[] usrAct;

	public JHeader(String ssid, String uid) {
		this.uid = uid;
		this.ssid = ssid;
	}

	public String logid() {
		return uid;
	}

	public String ssid() {
		return ssid;
	}

	/**
	 * @return js equivalent {md: ssinf.md, ssid: ssinf.ssid, uid: ssinf.uid, iv: ssinf.iv};
	 */
	public static JHeader format(String uid, String ssid) {
		// formatLogin: {a: "login", logid: logId, pswd: tokenB64, iv: ivB64};
		return new JHeader(ssid, uid);
	}

	public void act(String[] act) {
		usrAct = act;
	}
	
	public static String[] usrAct(String funcId, String remarks, String cate, String cmd) {
		return new String[] {funcId, cate, cmd, remarks};
	}

}
