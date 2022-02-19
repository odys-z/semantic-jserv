package io.odysz.semantic.tier.docs;

/**
 * @deprecated should be replaced with {@link io.odysz.semantic.jsession.SessionInf}.
 * @author ody
 *
 */
public class ClientDocUser {

    public ClientDocUser(String usrid, String device) {
		this.uid  = usrid;
		this.device = device;
	}

	String device;
	public String device() { return device; }
	public ClientDocUser device(String d) {
		device = d;
		return this;
	}

	String uid;
	public String uid() { return uid; }
	public ClientDocUser uid(String userId) {
		uid = userId;
		return this;
	}

//	String pswd;
//	public String pswd() {return pswd; }
//	public ClientDocUser pswd(String psword) {
//		pswd = psword;
//		return this;
//	}
}
