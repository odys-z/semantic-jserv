package io.odysz.semantic.jsession;

import java.io.InputStream;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;

public class SessionReq extends JBody {
	public SessionReq(JMessage<SessionReq> parent) {
		super(parent);
	}

//	private static JHelper<SessionReq> repHelper;

//	JHeader header;
	
	String uid;

	public String uid() {
		return uid;
	}

	public static JMessage<SessionReq> formatLogin(String uid, String tk64, String iv64) {
		JMessage<SessionReq> jmsg = new JMessage<SessionReq>(Port.session);
		SessionReq itm = new SessionReq(jmsg);
		itm.a("login");
		itm.uid = uid;
		jmsg.body(itm);
		return jmsg;
	}

	public static IUser parseLogin(InputStream in, String uid, String tk64, String iv64) {
		SemanticObject jmsg = JHelper.readJson(in);
		return new SUser(jmsg);
	}



}

