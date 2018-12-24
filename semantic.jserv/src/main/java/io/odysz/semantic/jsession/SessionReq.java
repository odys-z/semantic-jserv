package io.odysz.semantic.jsession;

import com.google.gson.Gson;

import io.odysz.semantic.jprotocol.JHeader;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JMessage.MsgCode;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantics.SemanticObject;

public class SessionReq extends JMessage {
	static JHelper<SessionReq> jhelper = new JHelper<SessionReq>();
	private static Gson gson = new Gson();

	JHeader header;
	private String a;

	SessionReq() {
		super(Port.session);
	}

	public String a() {
		return a;
	}

	public String uid() {
		// TODO Auto-generated method stub
		return null;
	}



}

