package io.odysz.semantic.jsession;

import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonResp;

public class AnSessionResp extends AnsonResp {

	private SessionInf ssInf;

	public AnSessionResp(AnsonMsg<AnsonResp> parent, String ssid, String uid, String ... roleId) {
		super(parent);
		ssInf = new SessionInf(ssid, uid, roleId == null || roleId.length == 0 ? null : roleId[0]);
		ssInf.ssid = ssid;
		ssInf.uid = uid;
		// TODO built-in role?
		// if (roleId != null && roleId.length > 0)
		// 	ssInf.roleId = roleId[0];
	}

	public AnSessionResp(AnsonMsg<? extends AnsonResp> parent, SessionInf ssInf) {
		super(parent);
		this.ssInf = ssInf;
	}

	public AnSessionResp() {
		super("");
	}

	public SessionInf ssInf() {
		return ssInf;
	}
}
