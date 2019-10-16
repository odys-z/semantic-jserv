package io.odysz.semantic.jserv.user;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantics.SemanticObject;

/**A stub for user's message body extension - subclassing {@link JBody}.
 * @author ody
 *
 */
public class UserReq extends AnsonBody {
	private String code;

	private SemanticObject data;
	public UserReq data(String k, Object v) {
		if (k == null) return this;

		if (data == null)
			data = new SemanticObject();
		data.put(k, v);
		return this;
	}

	public Object data(String k) {
		return data == null ? null : data.get(k);
	}

	String tabl;
	public String tabl() { return tabl; }

	public UserReq(AnsonMsg<? extends AnsonBody> parent, String conn) {
		super(parent, conn);
	}
	
	public Object get(String prop) {
		return data == null ? null : data.get(prop);
	}

//	@Override
//	public void toJson(JsonWriter writer, JOpts opts) throws IOException, SemanticException {
//		writer.beginObject()
//			.name("conn").value(conn)
//			.name("a").value(a)
//			.name("code").value(code)
//			.name("port").value(Port.user.name())
//			.name("tabl").value(tabl);
//		
//		if (data != null) {
//			writer.name("data");
//			// JHelper.writeMap(writer, data, opts);
//			JHelper.writeMap(writer, data.props(), opts);
//		}
//		
//		writer.endObject();
//	}
//
//	@Override
//	public void fromJson(JsonReader reader) throws IOException, SemanticException {
//		JsonToken token = reader.peek();
//		if (token == JsonToken.BEGIN_OBJECT) {
//			reader.beginObject();
//			token = reader.peek();
//			while (token != JsonToken.END_OBJECT) {
//				String name = reader.nextName();
//				if ("a".equals(name))
//					a = JHelper.nextString(reader);
//				else if ("conn".equals(name))
//					conn = JHelper.nextString(reader);
//				else if ("code".equals(name))
//					code = JHelper.nextString(reader);
//				else if ("port".equals(name))
//					// must be Port.user, drop it
//					JHelper.nextString(reader);
//				else if ("tabl".equals(name))
//					tabl = JHelper.nextString(reader);
//				else if ("data".equals(name)) {
//					// HashMap<String, Object> v = JHelper.readMap(reader);
//					data = JHelper.readSemanticObj(reader);
//				}
//
//				token = reader.peek();
//			}
//			reader.endObject();
//		}
//		else throw new SemanticException("Parse QueryReq failed. %s : %s", reader.getPath(), token.name());
//	}
}
