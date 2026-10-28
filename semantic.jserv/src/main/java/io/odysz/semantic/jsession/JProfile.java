package io.odysz.semantic.jsession;

import io.odysz.anson.Anson;

/**
 * The common profile of a session user, replied on login in {@link AnSessionResp#profile()}
 * ({@link io.odysz.semantics.IUser#profile()}), which all clients understand.
 *
 * <p>Peers: semantics.py3, semanticshare.io.odysz.semantic.jsession.JProfile;
 * @anclient/semantier / registry.ts.</p>
 *
 * @since 1.5.19
 */
public class JProfile extends Anson {

	/**
	 * The page to land on, decided by the server: the client router's path, e.g. "/home".
	 */
	public String home;

	/** The user's org, e.g. a_users.orgId. */
	public String orgId;

	/**
	 * A serialized Anson string, only a particular system / business understands or cares about.
	 */
	public String customExt;

	public JProfile() {}

	public JProfile(String home, String orgId) {
		this.home = home;
		this.orgId = orgId;
	}

	public JProfile customExt(String ext) {
		this.customExt = ext;
		return this;
	}
}
