package io.odysz.semantic.jprotocol;

import java.util.Arrays;
import java.util.stream.Collectors;

import io.odysz.anson.Anson;

public class AnsonHeader extends Anson {

	String uid;
	String ssid;
	String iv64;
	String[] usrAct;
	
	/**
	 * Session token, encrypt(key=session-token, text=ssid+uid, iv) : iv
	 * in base64, where session-token is reply of login.
	 * 
	 * @since 1.4.36
	 */
	String ssToken;

	/** @since 1.4.36 */
	public String token() { return ssToken; }

	public AnsonHeader(String ssid, String uid, String ssToken) {
		this.uid = uid;
		this.ssid = ssid;
		this.ssToken = ssToken;
	}
	
	public AnsonHeader() { }

	public String logid() { return uid; }

	public String ssid() { return ssid; }
	
	/**
	 * @since 1.4.36
	 * @deprecated according to <a href='https://stackoverflow.com/a/36972483/7362888'>
	 * this discussion</a>, replay attack prevention is not planned to be implemented.
	 * @param sq sequence number, not used
	 * @return this
	 */
	public AnsonHeader seq(int sq) { return this; }

	/**
	 * @return js equivalent {md: ssinf.md, ssid: ssinf.ssid, uid: ssinf.uid, iv: ssinf.iv};
	public static AnsonHeader format(String uid, String ssid, String token) {
		// formatLogin: {a: "login", logid: logId, pswd: tokenB64, iv: ivB64};
		return new AnsonHeader(ssid, uid, token);
	}
	 */

	public AnsonHeader act(String[] act) {
		usrAct = act;
		return this;
	}

	public AnsonHeader act(String funcId, String cmd, String cate, String remarks) {
		return act(new String[] {funcId, cate, cmd, remarks});
	}

	public AnsonHeader act(LogAct act) {
		usrAct = new String[] {act.func, act.cate, act.cmd, act.remarks};
		return this;
	}

	public static String[] usrAct(String funcId, String cmd, String cate, String remarks) {
		return new String[] {funcId, cate, cmd, remarks};
	}

	
	/**
	 * For test. The string can not been used for json data.
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return String.format("{ssid: %s, uid: %s, iv64: %s,\n\t\tuserAct: %s}",
				ssid, uid, iv64, usrAct == null ? null :
					Arrays.stream(usrAct).collect(Collectors.joining(", ", "[", "]")));
	}
}
