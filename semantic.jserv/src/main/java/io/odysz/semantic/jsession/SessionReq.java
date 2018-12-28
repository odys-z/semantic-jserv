package io.odysz.semantic.jsession;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JHeader;
import io.odysz.semantic.jprotocol.JHelper;

public class SessionReq extends JBody {
	static JHelper<SessionReq> jhelper = new JHelper<SessionReq>();
//	private static Gson gson = new Gson();

	JHeader header;
	private String a;

//	SessionReq() {
//		super(Port.session);
//	}

	public String a() {
		return a;
	}

	public String uid() {
		// TODO Auto-generated method stub
		return null;
	}



}

