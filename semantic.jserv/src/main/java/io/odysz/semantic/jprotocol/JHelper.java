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

/**<h4>The bridge of json string and JMessage/SemanticObject objects</h4>
 * @author ody
 *
 * @param <T> e.g. {@link io.odysz.semantic.jserv.R.QueryReq} (extends JBody), the message item type of JMessage.
 */
public class JHelper<T extends JBody> {

	private Gson gson = new Gson();

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

		JMessage<T> msg = new JMessage<T>();

		// {header: {header-obj}, body: [msgs]}
		try {
			JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
			reader.setLenient(true);
			reader.beginObject();
			JsonToken token = reader.peek();
			while (token != null && token != JsonToken.END_DOCUMENT) {
				switch (token) {
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

