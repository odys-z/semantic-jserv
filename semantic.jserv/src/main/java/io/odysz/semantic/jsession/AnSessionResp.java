package io.odysz.semantic.jsession;

import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantics.SessionInf;

public class AnSessionResp extends AnsonResp {

	private SessionInf ssInf;

	public AnSessionResp(AnsonMsg<AnsonResp> parent, String ssid, String uid, String ... roleId) {
		super(parent);
		ssInf = new SessionInf(ssid, uid, roleId == null || roleId.length == 0 ? null : roleId[0]);
		ssInf.ssid(ssid);
		ssInf.uid(uid);
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
