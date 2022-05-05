package io.odysz.semantic.jprotocol;

import java.util.Arrays;
import java.util.stream.Collectors;

import io.odysz.anson.Anson;

public class AnsonHeader extends Anson {

	String uid;
	String ssid;
	String iv64;
	String[] usrAct;

	public AnsonHeader(String ssid, String uid) {
		this.uid = uid;
		this.ssid = ssid;
	}
	
	public AnsonHeader() { }

	public String logid() {
		return uid;
	}

	public String ssid() {
		return ssid;
	}

	/**
	 * @return js equivalent {md: ssinf.md, ssid: ssinf.ssid, uid: ssinf.uid, iv: ssinf.iv};
	 */
	public static AnsonHeader format(String uid, String ssid) {
		// formatLogin: {a: "login", logid: logId, pswd: tokenB64, iv: ivB64};
		return new AnsonHeader(ssid, uid);
	}

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

	
	/**For test. The string can not been used for json data.
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return String.format("{ssid: %s, uid: %s, iv64: %s,\n\t\tuserAct: %s}",
				ssid, uid, iv64, usrAct == null ? null :
					Arrays.stream(usrAct).collect(Collectors.joining(", ", "[", "]")));
	}

//	public static String[] usrAct(String funcId, String cmd, String cate, String remarks) {
//	}
	
//	public static AnsonMsg<? extends AnsonBody> userReq(String t, IPort p, String[] act, DatasetReq req) {
//		return new String[] {funcId, cate, cmd, remarks};
//	}
	
}
