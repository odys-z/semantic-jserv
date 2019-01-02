package io.odysz.semantic.jprotocol;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import io.odysz.module.rs.SResultset;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;

/**<h4>The bridge of json string and JMessage/SemanticObject objects</h4>
 * @author ody
 *
 * @param <T> e.g. {@link io.odysz.semantic.jserv.R.QueryReq} (extends JBody), the message item type of JMessage.
 */
public class JHelper<T extends JBody> {

	private static Gson gson = new Gson();

	/*
	public static void writeJsonResp(OutputStream os, SemanticObject msg) throws IOException {
		JsonWriter writer = new JsonWriter(new OutputStreamWriter(os, "UTF-8"));
		writer.beginObject();
		writer.name("port").value(msg.getString("port"));
		writer.name("code").value(msg.getString("code"));
		if (msg.getType("error") != null)
			writer.name("error").value(msg.getString("error"));

		Class<?> t = msg.getType("msg");
		if (t == SemanticObject.class)
			// writer.name("msg").value(msg.get("msg"));
			writeJsonResp(os, (SemanticObject) msg.get("msg"));
		else if (t != null) // string, object, ...
			writer.name("msg").value(msg.get("msg").toString());
		// else t is null, no such property

		// TODO body ...
		writer.endObject();
		writer.close();
	}
	*/
	//
	public static void writeJsonResp(OutputStream os, SemanticObject msg) throws IOException {
		JsonWriter writer = new JsonWriter(new OutputStreamWriter(os, "UTF-8"));
//		writer.beginObject();
//		HashMap<String, Object> ps = msg.props();
//		for (String n : ps.keySet()) {
//			Class<?> t = msg.getType(n);
//			Object v = msg.get(n);
//			writer.name(n);
//			writeRespValue(writer, t, v);
//		}
//		writer.endObject();
		writeJsonValue(writer, msg);
		writer.close();
	}


	@SuppressWarnings("unchecked")
	private static void writeRespValue(JsonWriter writer, Class<?> t, Object v) throws IOException {
		if (IUser.class.isAssignableFrom(t)) {
			((IUser)v).writeJsonRespValue(writer);
		}
		if (SemanticObject.class.isAssignableFrom(t)) {
			 writeJsonValue(writer, (SemanticObject) v);
//			((SemanticObject)v).jsonValue(writer);
		}
		else if (Map.class.isAssignableFrom(t)) {
			writeMap(writer, (Map<?, ?>) v);
		}
		else if (List.class.isAssignableFrom(t)) {
			writeLst(writer, (List<Object>) v);
		}
		else
			writer.value(v.toString());
	}


	private static void writeJsonValue(JsonWriter writer, SemanticObject v) throws IOException {
		writer.beginObject();
		HashMap<String, Object> ps = v.props();
		if (ps != null)
			for (String n : ps.keySet()) {
				Class<?> t = v.getType(n);
				Object obj = v.get(n);
				writer.name(n);
				writeRespValue(writer, t, obj);
			}
		writer.endObject();
	}


	private static void writeLst(JsonWriter writer, List<Object> lst) throws IOException {
		writer.beginArray();
		for (Object v : lst) {
			writeRespValue(writer, v.getClass(), v);
		}
		
		writer.endArray();
	}


	private static void writeMap(JsonWriter writer, Map<?, ?> map) throws IOException {
		writer.beginArray();
		for (Object k : map.keySet()) {
			Object v = map.get(k);
			writer.name(k.toString());
			writeRespValue(writer, map.get(k).getClass(), map.get(k));
		}
		
		writer.endArray();
	}

	public void writeJsonReq(OutputStream os, JMessage<? extends JBody> jreq, Class<? extends JBody> itemClz) throws IOException {
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
					if (name != null && "rs".equals(name))
						obj.put("rs", readRs(reader));
					else if (name != null && "map".equals(name))
						obj.put("map", readMap(reader));
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

	private static HashMap<String, String> readMap(JsonReader reader) throws IOException {
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
	private static SResultset readRs(JsonReader reader) throws IOException {
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
		JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
		try {
			reader.setLenient(true);
			if (reader.peek() == JsonToken.NULL)
				// empty load
				return null;
			reader.beginObject();
			JsonToken token = reader.peek();
			while (token != null && token != JsonToken.END_DOCUMENT) {
				switch (token) {
				case NAME:
					String name = reader.nextName();
					if (name != null && "port".equals(name.trim().toLowerCase()))
						msg.port(reader.nextString());
					else if (name != null && "header".equals(name.trim().toLowerCase()))
						msg.header(readHeader(reader));
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
		} catch (Exception e) {
			throw new SemanticException("Parsing Json failed. Internal error: %s", e.getMessage());
		} finally { reader.close(); }
		
		return msg;
	}

	/**<p>Read message body (deserialization).</p>
	 * <p>In the current version (0.1.0), the method using Gson, and the debugging shows only
	 * fields with getter can be deserialized.</p>
	 * @param reader
	 * @param elemClass
	 * @param parent
	 * @return
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	protected List<T> readBody(JsonReader reader, Class<? extends JBody> elemClass, JMessage<?> parent)
			throws IOException {
		reader.beginArray();
		List<T> messages = new ArrayList<T>();
		while (reader.hasNext()) {
			T message;
			// debugging shows that only fields with getter can be deserialized
			try { message = gson.fromJson(reader, elemClass); }
			catch (Exception me) { message = (T) new JErrBody(parent, me.getMessage());}
			message.parent = parent;
			messages.add(message);
		}
		reader.endArray();
		return messages;
	}

	protected JHeader readHeader(JsonReader reader) throws IOException {
		JHeader header = gson.fromJson(reader, JHeader.class);
		return header;
	}

	public JHeader readHeader(String head) throws IOException {
		if (head == null || head.length() == 0)
			return null;
		InputStream in = new ByteArrayInputStream(head.getBytes());
		JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
		return readHeader(reader);
	}
}

