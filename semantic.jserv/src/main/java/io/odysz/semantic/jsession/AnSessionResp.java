package io.odysz.semantic.jsession;

import io.odysz.anson.Anson;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantics.SessionInf;

public class AnSessionResp extends AnsonResp {

	SessionInf ssInf;
	
	/**
	 * A data package for extra user info for application's extension,
	 * e.g. default page, login count, etc.
	 * 
	 * @since 1.5.0
	 */
	Anson profile;

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

	/**
	 * @since 1.5.0
	 * 
	 * @param profile
	 * @return this
	 */
	public AnSessionResp profile(Anson profile) {
		this.profile = profile;
		return this;
	}
}
