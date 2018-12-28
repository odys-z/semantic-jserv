package io.odysz.semantic.jprotocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;

public class JHelper<T extends JBody> {

	private Gson gson = new Gson();

//	public void writeJson(OutputStream out, T msg) throws IOException {
//		JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, "UTF-8"));
//		writer.setIndent("  ");
//		// code, seq, port
//		writer.beginObject();
//		writer.name("code");
//		writer.value(msg.code.name());
//		writer.name("seq");
//		writer.value(msg.seq);
//		writer.name("port");
//		writer.value(msg.port.name());
//
//		writer.name("body");
//		writer.beginArray();
//		Type t = new TypeToken<T>() {}.getType();
//		for (JMessage message : msg.body()) {
//			gson.toJson(message, t, writer);
//		}
//		writer.endArray();
//		writer.endObject();
//		writer.close();
//	}

	public static void writeJson(OutputStream os, SemanticObject msg) throws IOException {
		JsonWriter writer = new JsonWriter(new OutputStreamWriter(os, "UTF-8"));
		writer.beginObject();
		writer.name("port").value(msg.getString("port"));
		writer.name("code").value(msg.getString("code"));
		if (msg.getType("error") != null)
			writer.name("error").value(msg.getString("error"));
		if (msg.getType("msg") != null)
			writer.name("msg").value(msg.getString("msg"));
		// TODO body ...
		writer.endObject();
		writer.close();
	}

	/**read json stream int list of elemClass: ArrayList&lt;elemClass&gt;.<br>
	 * A tried version working but returned T is not JMessage:<pre>
	public List&lt;T&gt; readJsonStream(InputStream in) throws IOException {
		JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
		Type t = new TypeToken&lt;ArrayList&lt;T&gt;&gt;() {}.getType();
		List&lt;T&gt; messages = gson.fromJson(reader, t);
 		reader.close();
 		return messages;
 	}</pre>
	 * @param in
	 * @param elemClass
	 * @return {header, query: [query-obj]}
	 * @throws IOException
	public List<T> readJsonStream(InputStream in, Class<? extends T> elemClass) throws IOException {
		JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
		reader.beginArray();
//		Type t = new TypeToken<T>() {}.getType();
		List<T> messages = new ArrayList<T>();
		while (reader.hasNext()) {
			T message = gson.fromJson(reader, elemClass);
			messages.add(message);
		}

		reader.endArray();
		reader.close();
		return messages;
 
	}
	 */
	
	public void println(JMessage<T> msg) {
		
	}

	/**Deserialize json message into subclass of {@link JMessage}.
	 * @param in
	 * @param bodyItemclzz
	 * @return message
	 * @throws IOException
	 * @throws ReflectiveOperationException
	 * @throws SemanticException
	 */
	public JMessage<T> readJson(InputStream in, Class<? extends JBody> bodyItemclzz)
			throws IOException, ReflectiveOperationException, SemanticException {
		// new UpdateReq, ...
//		 Class<? extends JMessage> bodyItemclzz = (Class<? extends JMessage>) Class.forName(bodyItemtype.getTypeName());
//		Class<? extends JMessage> bodyItemclzz = (Class<? extends JMessage>) bodyItemtype.getClass();

//		Constructor<JMessage<T>> ctor = bodyItemclzz.getDeclaredConstructor();
//		JMessage<T> msg = ctor.newInstance();
		JMessage<T> msg = new JMessage<T>();

		// {header: {header-obj}, req: [msg]}
		try {
			JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
			reader.setLenient(true);
			reader.beginObject();
			JsonToken token = reader.peek();
			while (token != null && token != JsonToken.END_DOCUMENT) {
				switch (token) {
//				case BEGIN_ARRAY:
//					msg.body(readArr(reader, bodyItemclzz));
//					break;
				case NAME:
					String name = reader.nextName();
					if (name != null && "header".equals(name.trim().toLowerCase()))
						msg.header(readObj(reader, JHeader.class));
					else if (name != null && "body".equals(name.trim().toLowerCase())) {
						List<T> m = readBody(reader, bodyItemclzz);
						msg.body(m);
					}
					else {
						reader.close();
						throw new SemanticException("Can't parse json message: %s, %s", bodyItemclzz.toString(), msg.toString());
					}
					break;
				case END_OBJECT:
					reader.endObject();
					break;
				default:
					reader.close();
					throw new SemanticException("Can't parse json message: %s, %s", bodyItemclzz.toString(), msg.toString());
				}
				token = reader.peek();
			}
//			reader.endObject();
			reader.close();
		} catch (Exception e) {
			throw new SemanticException("Parsing Json failed. Internal error: %s", e.getMessage());
		}
		
		return msg;
	}
	
	@SuppressWarnings("unchecked")
	protected List<T> readBody(JsonReader reader, Class<? extends JBody> elemClass)
			throws IOException {
		reader.beginArray();
		List<T> messages = new ArrayList<T>();
		while (reader.hasNext()) {
			T message;
			try { message = gson.fromJson(reader, elemClass); }
			catch (Exception me) { message = (T) new JErroBody(me.getMessage());}
			messages.add(message);
		}
		reader.endArray();
		return messages;
	}

	protected JHeader readObj(JsonReader reader, Class<JHeader> elemClass) throws IOException {
		JHeader header = gson.fromJson(reader, elemClass);
		return header;
	}
	
}

