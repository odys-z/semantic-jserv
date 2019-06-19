package io.odysz.semantic.jsession;

import java.io.IOException;
import java.util.HashMap;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantic.jprotocol.JOpts;
import io.odysz.semantics.x.SemanticException;

/**<p>Sessin Request<br>
 * a = "login" | "logout" | "heartbeat" ...</p>
 * @author odys-z@github.com
 */
public class SessionReq extends JBody {
	/**
	 * @param parent
	 * @param conn ignored for session connection are controlled by server 
	 */
	public SessionReq(JMessage<SessionReq> parent, String conn) {
		super(parent, null); // session's DB access is controlled by server
	}

	String uid;
	String token;
	String token() { return token; }
	String iv;
	String iv() { return iv; }

	HashMap<String,Object> mds;
	public String md(String k) { return mds == null ? null : (String) mds.get(k); }
	public SessionReq md(String k, String md) {
		if (k == null || LangExt.isblank(md))
			return this;
		if (mds == null)
			mds = new HashMap<String, Object>();
		mds.put(k, md);
		return this;
	}

	public String uid() { return uid; }

	/**Format login request message.
	 * @param uid
	 * @param tk64
	 * @param iv64
	 * @return login request message
	 */
	public static JMessage<SessionReq> formatLogin(String uid, String tk64, String iv64) {
		JMessage<SessionReq> jmsg = new JMessage<SessionReq>(Port.session);

		SessionReq itm = new SessionReq(jmsg, null);
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

	@Override
	public void toJson(JsonWriter writer, JOpts opts) throws IOException, SemanticException {
		writer.beginObject();
		writer.name("a").value(a);
		writer.name("uid").value(uid);
		writer.name("iv").value(iv);
		writer.name("token").value(token());
		if (mds != null) {
			writer.name("mds");
			JHelper.writeMap(writer, mds, new JOpts());
		}
		writer.endObject();
	}

	@Override
	public void fromJson(JsonReader reader) throws IOException, SemanticException {
		JsonToken token = reader.peek();
		
		// why?
		while (token == JsonToken.NULL) {
			reader.nextNull();
			token = reader.peek();
		}
		
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
				else if ("mds".equals(name))
					this.mds = JHelper.readMap(reader);
				else
					Utils.warn("Session request's property ignored: %s : %s", name, reader.nextString());
				token = reader.peek();
			}
			reader.endObject();
		}
	}

}

