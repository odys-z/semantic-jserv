package io.odysz.semantic.jsession;

import java.io.IOException;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import io.odysz.common.Utils;
import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JMessage.Port;

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

		jmsg.body((JBody)itm);
		return jmsg;
	}

	private void setup(String uid, String tk64, String iv64) {
		this.uid = uid;
		this.token = tk64;
		this.iv = iv64;
	}

//	public static IUser parseLogin(InputStream in, String uid, String tk64, String iv64) throws SemanticException, IOException {
//		SemanticObject jmsg = JHelper.readResp(in);
//		return new SUser(jmsg);
//	}

	@Override
	public void toJson(JsonWriter writer) throws IOException {
		writer.beginObject();
		writer.name("a").value(a);
		writer.name("uid").value(uid);
		writer.name("iv").value(iv);
		writer.name("token").value(token());
		writer.endObject();
	}

	@Override
	public void fromJson(JsonReader reader) throws IOException {
		JsonToken token = reader.peek();
		if (token == JsonToken.BEGIN_OBJECT) {
			reader.beginObject();
			while (token != JsonToken.END_OBJECT) {
				String name = reader.nextName();
				if ("a".equals(name))
					a = reader.nextString();
				else if ("uid".equals(name))
					uid = reader.nextString();
				else if ("iv".equals(name))
					iv = reader.nextString();
				else if ("token".equals(name))
					this.token = reader.nextString();
				else
					Utils.warn("Session request's property ignored: %s : %s", name, reader.nextString());
				token = reader.peek();
			}
			reader.endObject();
		}
	}

}

