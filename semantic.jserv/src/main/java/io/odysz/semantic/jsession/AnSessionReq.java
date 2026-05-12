package io.odysz.semantic.jsession;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.HashMap;

import io.odysz.anson.AnsonCtor;
import io.odysz.common.AESHelper2;
import io.odysz.common.LangExt;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;

/**
 * <p>Session Request<br>
 * a: see {@link AnSession} </p>
 * session's DB access is controlled by server
 * 
 * @author odys-z@github.com
 */
public class AnSessionReq extends AnsonBody {
	public static class A {
		public static final String login = "login";
		public static final String logout = "logout";
		public static final String pswd = "pswd";
		public static final String init = "init";
		public static final String touch = "touch";
		public static final String ping = "ping";
	}
	
	@AnsonCtor(base= {""} )
	public AnSessionReq() {
		super(null, null);
	}

	/**
	 * Session connection is ignored and controlled by server.
	 * 
	 * @param parent
	 */
	public AnSessionReq(AnsonMsg<AnSessionReq> parent) {
		super(parent, null); // session's DB access is controlled by server
	}

	String uid;
	public String uid() { return uid; }
	String token;
	public String token() { return token; }
	String iv;
	public String iv() { return iv; }

	/**
	 * @deprecated No equivalent of AST & C++.
	 */
	HashMap<String, Object> mds;
	public String md(String k) { return mds == null ? null : (String) mds.get(k); }
	public AnSessionReq md(String k, String md) {
		if (k == null || LangExt.isblank(md))
			return this;
		if (mds == null)
			mds = new HashMap<String, Object>();
		mds.put(k, md);
		return this;
	}

	String deviceId;
	public String deviceId() { return deviceId; }

	/**
	 * Format login request message.
	 * 
	 * @param uid
	 * @param tk64
	 * @param iv64
	 * @param deviceId
	 * @return login request message
	 */
	public static AnsonMsg<AnSessionReq> formatLogin(String uid, String tk64, String iv64, String ... deviceId) {
		AnsonMsg<AnSessionReq> jmsg = new AnsonMsg<AnSessionReq>(Port.session);

		AnSessionReq itm = new AnSessionReq(jmsg);
		itm.uid = uid;
		itm.a("login");

		itm.setup(uid, tk64, iv64);

		if (deviceId != null && deviceId.length > 0)
			itm.deviceId = deviceId[0];

		jmsg.body((AnsonBody)itm);
		return jmsg;
	}

	public static AnsonMsg<AnSessionReq> formatLogin2(String uid, String pswdPlain,
			String ... deviceId) throws GeneralSecurityException, IOException {

		AnsonMsg<AnSessionReq> jmsg = new AnsonMsg<AnSessionReq>(Port.session);

		AnSessionReq itm = new AnSessionReq(jmsg);
		itm.uid = uid;
		itm.a("login");

		byte[] iv =   AESHelper2.getRandom();
		String iv64 = AESHelper2.encode64(iv);
		String tk64 = AESHelper2.encrypt(uid, pswdPlain, iv);
		itm.setup(uid, tk64, iv64);

		if (deviceId != null && deviceId.length > 0)
			itm.deviceId = deviceId[0];

		jmsg.body((AnsonBody)itm);
		
		return jmsg;
	}

	private void setup(String uid, String tk64, String iv64) {
		this.uid = uid;
		this.token = tk64;
		this.iv = iv64;
	}
}

