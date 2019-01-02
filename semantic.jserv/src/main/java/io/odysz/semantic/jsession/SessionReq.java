package io.odysz.semantic.jsession;

import java.io.IOException;
import java.io.InputStream;

import com.google.gson.stream.JsonWriter;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;

public class SessionReq extends JBody {
	public SessionReq(JMessage<SessionReq> parent) {
		super(parent);
	}

	String uid;
	String token;
	String token() { return token; }
	String iv;
	String iv() { return iv; }

	public String uid() { return uid; }

	public static JMessage<SessionReq> formatLogin(String uid, String tk64, String iv64) {
		JMessage<SessionReq> jmsg = new JMessage<SessionReq>(Port.session);

		SessionReq itm = new SessionReq(jmsg);
		itm.uid = uid;
		itm.a("login");

		itm.setup(uid, tk64, iv64);

		jmsg.body(itm);
		return jmsg;
	}

	private void setup(String uid, String tk64, String iv64) {
		this.uid = uid;
		this.token = tk64;
		this.iv = iv64;
	}

	public static IUser parseLogin(InputStream in, String uid, String tk64, String iv64) throws SemanticException, IOException {
		SemanticObject jmsg = JHelper.readResp(in);
		return new SUser(jmsg);
	}

	@Override
	public void toJson(JsonWriter writer) throws IOException {
		writer.beginObject();
		writer.name("a").value(a);
		writer.name("uid").value(uid);
		writer.name("iv").value(iv);
		writer.name("token").value(token());
		writer.endObject();
	}
}

