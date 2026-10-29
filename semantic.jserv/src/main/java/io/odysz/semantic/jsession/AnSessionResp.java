package io.odysz.semantic.jsession;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonCtor;
import io.odysz.common.Utils;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantics.SessionInf;

public class AnSessionResp extends AnsonResp {

	SessionInf ssInf;

	@AnsonCtor(base = {""}, semntics = {"ssInf.ssid=ssid", "ssInf.uid=uid", "ssInf.roleId=roleId"})
	public AnSessionResp(AnsonMsg<AnsonResp> parent, String ssid, String uid, String ... roleId) {
		super(parent);
		ssInf = new SessionInf(ssid, uid, roleId == null || roleId.length == 0 ? null : roleId[0]);
		ssInf.ssid(ssid);
		ssInf.uid(uid);
	}

	@AnsonCtor(base = {}, initialist = "SessionInf ssInf ssInf")
	public AnSessionResp(AnsonMsg<? extends AnsonResp> parent, SessionInf ssInf) {
		super(parent);
		this.ssInf = ssInf;
	}

	@AnsonCtor(base = {""})
	public AnSessionResp() {
		super("");
	}

	public SessionInf ssInf() {
		return ssInf;
	}

	/**
	 * A data package for extra user info for application's extension,
	 * e.g. default page, login count, etc.
	 * 
	 * <p>Since 1.5.19, typed {@link JProfile}, as clients (C++, Java, py3) don't support
	 * polymorphism yet; application specific data goes in {@link JProfile#customExt}.</p>
	 * 
	 * @since 1.4.25
	 */
	JProfile profile;
	public JProfile profile() { return profile; }

	/**
	 * Set {@link #profile}.
	 * 
	 * {@link io.odysz.semantics.IUser#profile()} still returns Anson (semantic.transact),
	 * but only a {@link JProfile} goes on the wire; any other type is serialized into
	 * {@link JProfile#customExt}.
	 * 
	 * @since 1.4.25, 1.5.19 typed JProfile
	 * 
	 * @param profile
	 * @return this
	 */
	public AnSessionResp profile(Anson profile) {
		if (profile == null || profile instanceof JProfile)
			this.profile = (JProfile) profile;
		else {
			// clients can't deserialize other types, without polymorphism
			Utils.warn("AnSessionResp.profile(): %s is not a JProfile, replied as an empty JProfile.",
					profile.getClass().getName());
			this.profile = new JProfile().customExt(
					String.format("Can't set %s into JProfile", profile.getClass().getName()));
		}
		return this;
	}
}
