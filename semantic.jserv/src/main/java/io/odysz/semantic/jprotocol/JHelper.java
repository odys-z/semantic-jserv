package io.odysz.semantic.jprotocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import io.odysz.module.rs.SResultset;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;

/**<h4>The bridge of json string and JMessage/SemanticObject objects</h4>
 * @author ody
 *
 * @param <T> e.g. {@link io.odysz.semantic.jserv.R.QueryReq} (extends JBody), the message item type of JMessage.
 */
public class JHelper<T extends JBody> {

	private static Gson gson = new Gson();

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

	public void writeJson(OutputStream os, JMessage<? extends JBody> jreq, Class<? extends JBody> itemClz) throws IOException {
		JsonWriter writer = new JsonWriter(new OutputStreamWriter(os, "UTF-8"));
        writer.setIndent("  ");
		writer.beginObject();
		writer.name("port").value(jreq.port().name());

		writer.name("header").beginObject();
		if (jreq.header != null)
			gson.toJson(jreq.header, JHeader.class, writer);
		writer.endObject();

		writer.name("body").beginArray();
        for (JBody message : jreq.body) 
        	message.toJson(writer);
            // gson.toJson(message, itemClz, writer);
		writer.endArray();
		writer.endObject();

		writer.flush();
        writer.close();
		os.flush();
	}

	public void println(JMessage<T> msg) {
		
	}
	
	public static SemanticObject readResp(InputStream in) throws IOException, SemanticException {
		JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
		SemanticObject obj = new SemanticObject();
		
		try {
			reader.beginObject();
			JsonToken tk = reader.peek();

			while (tk != null && tk != JsonToken.END_DOCUMENT) {
				switch (tk) {
				case NAME:
					String name = reader.nextName();
//					if (name != null && "port".equals(name))
//						obj.put("port", reader.nextString());
//					else if (name != null && "code".equals(name))
//						obj.put("code", reader.nextString());
//					else if (name != null && "msg".equals(name))
//						obj.put("msg", reader.nextString());
//					else
						if (name != null && "rs".equals(name))
						obj.put("rs", rs(reader));
					else if (name != null && "map".equals(name))
						obj.put("map", map(reader));
					else {
						tk = reader.peek();
						if (tk == JsonToken.NULL) {
							reader.nextNull();
							obj.put(name, null);
						}
						else
							obj.put(name, reader.nextString());
					}
					break;
				default:
					// reader.close();
					break;
				}
				if (tk != JsonToken.END_DOCUMENT)
					tk = reader.peek();
				if (tk == JsonToken.END_OBJECT)
					break;
			}
		} catch (Exception e) {
			throw new SemanticException("Parsing response failed. Internal error: %s", e.getMessage());
		}
		finally {
			reader.close();
		}
		
		return obj;
	}

	private static HashMap<String, String> map(JsonReader reader) throws IOException {
		reader.beginArray();

		HashMap<String, String> m = new HashMap<String, String>();

		JsonToken tk = reader.peek();
		// error tolerating is necessary?
		if (tk == JsonToken.END_DOCUMENT)
			return m;

		while (tk != JsonToken.END_DOCUMENT && tk != JsonToken.END_ARRAY) {
			reader.beginObject();
			String n = reader.nextName();
			String v = reader.nextString();
			m.put(n, v);
			reader.endObject();
			tk = reader.peek();
		}

		if (tk == JsonToken.END_ARRAY) {
			reader.endArray();
			tk = reader.peek();
		}

		if (tk == JsonToken.END_ARRAY)
			reader.endArray();

		return m;
	}

	/**Read {@link SResultset}.
	 * @param reader
	 * @return
	 * @throws IOException
	 */
	private static SResultset rs(JsonReader reader) throws IOException {
		reader.beginArray();

		HashMap<String, Integer> colnames = new HashMap<String, Integer>();
		reader.beginArray();

		// cols
		JsonToken tk = reader.peek();
		int c = 1;
		while (tk != JsonToken.END_DOCUMENT && tk != JsonToken.END_ARRAY) {
			colnames.put(reader.nextString(), c);
			c++;
			tk = reader.peek();
		}

		SResultset rs = new SResultset(colnames);
		
		// error tolerating is necessary?
		if (tk == JsonToken.END_DOCUMENT)
			return rs;

		// rows
		tk = reader.peek();
		while (tk != JsonToken.END_DOCUMENT && tk == JsonToken.BEGIN_ARRAY) {
			reader.beginArray();
			ArrayList<Object> row = new ArrayList<Object>(colnames.size());
			c = 0;
			while (tk != JsonToken.END_DOCUMENT && tk != JsonToken.END_ARRAY) {
				String v = reader.nextString();
				row.add(c, v);
				tk = reader.peek();
			}
			rs.append(row);

			if (tk == JsonToken.END_ARRAY) {
				reader.endArray();
				tk = reader.peek();
			}
		}

		if (tk == JsonToken.END_ARRAY)
			reader.endArray();

		return rs;
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
					if (name != null && "port".equals(name.trim().toLowerCase()))
						msg.port(reader.nextString());
					else if (name != null && "header".equals(name.trim().toLowerCase()))
						msg.header(readObj(reader, JHeader.class));
					else if (name != null && "body".equals(name.trim().toLowerCase())) {
						List<T> m = readBody(reader, bodyItemclzz, msg);
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
	protected List<T> readBody(JsonReader reader, Class<? extends JBody> elemClass, JMessage<?> parent)
			throws IOException {
		reader.beginArray();
		List<T> messages = new ArrayList<T>();
		while (reader.hasNext()) {
			T message;
			try { message = gson.fromJson(reader, elemClass); }
			catch (Exception me) { message = (T) new JErrBody(parent, me.getMessage());}
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

