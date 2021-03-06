//package io.odysz.semantic.jprotocol;
//
//import java.io.ByteArrayInputStream;
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.InputStreamReader;
//import java.io.OutputStream;
//import java.io.OutputStreamWriter;
//import java.lang.reflect.Constructor;
//import java.sql.SQLException;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//import com.google.gson.Gson;
//import com.google.gson.stream.JsonReader;
//import com.google.gson.stream.JsonToken;
//import com.google.gson.stream.JsonWriter;
//
//import io.odysz.module.rs.SResultset;
//import io.odysz.semantic.jserv.U.UpdateReq;
//import io.odysz.semantics.IUser;
//import io.odysz.semantics.SemanticObject;
//import io.odysz.semantics.x.SemanticException;
//
///**<h4>The bridge of json string and JMessage/SemanticObject objects</h4>
// * @author odys-z@github.com
// *
// * @param <T> e.g. {@link io.odysz.semantic.jserv.R.QueryReq} (extends JBody), the message item type of JMessage.
// */
//public class JHelper<T extends JBody> {
//
//	private static boolean printCaller = true;
//	public static void printCaller (boolean print) { printCaller = print; }
//
//	private static Gson gson = new Gson();
//	private static JOpts _opts = new JOpts();
//
//	public static void writeJsonResp(OutputStream os, SemanticObject msg, JOpts opts)
//			throws IOException, SemanticException {
//		JsonWriter writer = new JsonWriter(new OutputStreamWriter(os, "UTF-8"));
//		writeJsonValue(writer, msg, opts);
//		writer.close();
//	}
//
//	@SuppressWarnings("unchecked")
//	private static void writeRespValue(JsonWriter writer, Class<?> t, Object v,
//			JOpts opts) throws IOException, SemanticException {
//		if (IUser.class.isAssignableFrom(t)) {
//			((IUser)v).writeJsonRespValue(writer);
//		}
//		else if (SemanticObject.class.isAssignableFrom(t)) {
//			 writeJsonValue(writer, (SemanticObject) v, opts);
//		}
//		else if (t == Object[].class) {
//			writeStrings(writer, (Object[])v, opts);
//		}
//		else if (t == String[].class) {
//			writeStrings(writer, (String[])v, opts);
//		}
//		else if (v instanceof SResultset) {
//			SResultset rs = (SResultset)v;
//			writeRs(writer, rs, opts);
//		}
//		else if (Map.class.isAssignableFrom(t)) {
//			writeMap(writer, (Map<?, ?>) v, opts);
//		}
//		else if (List.class.isAssignableFrom(t)) {
//			writeLst(writer, (List<Object>) v, opts);
//		}
//		// Note 2019.5.13  set value while considering options
//		else if (Boolean.class.isAssignableFrom(t))
//			if (opts.noBoolean)
//				writer.value((Boolean) v ? "true" : "false");
//			else 
//				writer.value((Boolean)v);
//		else if (JBody.class.isAssignableFrom(t))
//			((JBody)v).toJson(writer, opts);
//		else
//			// Note 2019.5.13  set value while considering options
//			// writer.value(v == null ? JsonToken.NULL.toString() : v.toString());
//
//			// Note 2019.4.23, these two way are alternated more than twice, what's it?
//			// case 1: switch to null included, for autoVals of update is nullable.
//			
//			writer.value(v == null ? (opts.noNull ? "" : JsonToken.NULL.toString())
//								   : v.toString());
//	}
//
//	/**Write a string array, with "[" and "]".
//	 * @param writer
//	 * @param v
//	 * @param opts 
//	 * @throws IOException
//	 */
//	public static void writeStrings(JsonWriter writer, String[] v, JOpts opts) throws IOException {
//		writer.beginArray();
//		for (int i = 0; i < v.length; i++) {
//			if (v[i] == null) {
//				if (opts.noNull)
//					writer.value("");
//				else
//					writer.nullValue();
//			}
//			else
//				writer.value(v[i]);
//		}
//		writer.endArray();
//	}
//
//	private static void writeStrings(JsonWriter writer, Object[] v, JOpts opts) throws IOException {
//		writer.beginArray();
//		for (int i = 0; i < v.length; i++) {
//			if (v[i] == null) {
//				if (opts.noNull)
//					writer.value("");
//				else
//					writer.nullValue();
//			}
//			else
//				writer.value(v[i].toString());
//		}
//		writer.endArray();
//	}
//
//	public static void writeStrss(JsonWriter writer, String[][] vss, JOpts opts) throws IOException {
//		for (int i = 0; i < vss.length; i++) {
//			if (vss[i] == null) {
//				if (opts.noNull)
//					writer.value("");
//				else 
//					writer.nullValue();
//			}
//			else
//				writeStrings(writer, vss[i], opts);
//		}
//	}
//
//	private static void writeRs(JsonWriter writer, SResultset rs, JOpts opts) throws IOException, SemanticException {
//		// [ [col1, col2, ...],
//		//   [cel1, cel2, ...], ...
//		writer.beginArray();
//
//		// starting from 1
//		writer.beginArray();
//		for (int c = 1; c <= rs.getColCount(); c++) {
//			writer.value(rs.getColumnName(c));
//		}
//		writer.endArray();
//
//		try {
//			rs.beforeFirst();
//			while (rs.next()) {
//				writer.beginArray();
//				for (int ix = 1; ix <= rs.getColCount(); ix++) {
//					// writer.value(rs.getString(ix));
//					String v = rs.getString(ix);
//					if (v == null)
//						if (opts.noNull)
//							writer.value("");
//						else 
//							writer.nullValue();
//					else
//						writer.value(toJsonOpt(opts, rs.getString(ix)));
//				}
//				writer.endArray();
//			}
//		} catch (SQLException e) {
//			e.printStackTrace();
//			throw new SemanticException(e.getMessage());
//		}
//
//		writer.endArray();
//	}
//
//	/**Convert string considering j-options.
//	 * @param opts
//	 * @param v
//	 * @return json string
//	 */
//	private static String toJsonOpt(JOpts opts, String v) {
//		if (opts.noBoolean && "true".equals(v))
//			return "'true'";
//		else if (opts.noBoolean && "false".equals(v))
//			return "'false'";
//		else if (opts.noNull && v == null)
//			return "";
//		return v;
//	}
//
//	private static void writeJsonValue(JsonWriter writer, SemanticObject v, JOpts opts) throws IOException, SemanticException {
//		writer.beginObject();
//		HashMap<String, Object> ps = v.props();
//		if (ps != null)
//			for (String n : ps.keySet()) {
//				Class<?> t = v.getType(n);
//				Object obj = v.get(n);
//				writer.name(n);
//				writeRespValue(writer, t, obj, opts);
//			}
//		writer.endObject();
//	}
//
//	/**Write list into json. This method handle multi-dimensional array.
//	 * @param writer
//	 * @param lst
//	 * @param opts 
//	 * @throws IOException
//	 * @throws SemanticException
//	 */
//	public static void writeLst(JsonWriter writer, List<?> lst, JOpts opts) throws IOException, SemanticException {
//		writer.beginArray();
//		for (Object v : lst) {
//			writeRespValue(writer, v.getClass(), v, opts);
//		}
//		
//		writer.endArray();
//	}
//
//	public static void writeMap(JsonWriter writer, Map<?, ?> map, JOpts opts) throws IOException, SemanticException {
//		writer.beginObject();
//		for (Object k : map.keySet()) {
//			Object v = map.get(k);
//			writer.name(k.toString());
//			writeRespValue(writer, v.getClass(), v, opts);
//		}
//		
//		writer.endObject();
//	}
//
//	/**Write jreq into output stream.
//	 * If jreq.header is null, create an empty object {}.
//	 * @param os
//	 * @param jreq
//	 * @throws IOException
//	 * @throws SemanticException
//	 */
//	public static void writeJsonReq(OutputStream os, JMessage<? extends JBody> jreq)
//			throws IOException, SemanticException {
//		if (jreq.body == null)
//			throw new SemanticException("Request must have message body.");
//		
//		JsonWriter writer = new JsonWriter(new OutputStreamWriter(os, "UTF-8"));
//        writer.setIndent("  ");
//		writer.beginObject();
//		writer.name("port").value(jreq.port().name());
//		writer.name("t").value(jreq.t);
//
//		writer.name("header"); //.beginObject();
//		if (jreq.header != null)
//			gson.toJson(jreq.header, JHeader.class, writer);
//		else {
//			writer.beginObject();
//			writer.endObject();
//		}
//
//		writer.name("body").beginArray();
//        for (JBody bodyItem : jreq.body) 
//        	bodyItem.toJson(writer, _opts );
//            // gson.toJson(message, itemClz, writer);
//		writer.endArray();
//		writer.endObject();
//
//		writer.flush();
//        writer.close();
//		os.flush();
//	}
//
//	public void println(JMessage<T> msg) {
//	}
//	
//	public static SemanticObject readResp(InputStream in) throws IOException, SemanticException {
//		JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
//		SemanticObject obj = new SemanticObject();
//		
//		obj = readSemanticObj(reader);
//		return obj;
//	}
//
//	public static SemanticObject readSemanticObj(JsonReader reader) throws IOException, SemanticException {
//		SemanticObject obj = new SemanticObject();
//		reader.beginObject();
//
//		JsonToken tk = reader.peek();
//
//		while (tk != null && tk != JsonToken.END_DOCUMENT) {
//			if (tk == JsonToken.NAME) {
//				String name = reader.nextName();
//				if (name != null && "rs".equals(name))
//					// semantics: rs is list
//					obj.put("rs", readLstRs(reader));
//				else if (name != null && "map".equals(name))
//					// semantics: map is Map
//					obj.put("map", readMap(reader));
//				else {
//					tk = reader.peek();
//					if (tk == JsonToken.NULL) {
//						reader.nextNull();
//						obj.put(name, null);
//					}
//					else if (tk == JsonToken.BEGIN_OBJECT)
//						obj.put(name, readSemanticObj(reader));
//					else if (tk == JsonToken.BEGIN_ARRAY)
//						try {obj.put(name, readLstStrs(reader));
//						}catch (SemanticException se) {
//							obj.put(name, readSmtcsObjs(reader, true));
//						}
//					else
//						obj.put(name, reader.nextString());
//				}
//			}
//			else {
//				// what's here?
//			}
//			if (tk != JsonToken.END_DOCUMENT)
//				tk = reader.peek();
//			if (tk == JsonToken.END_OBJECT)
//				break;
//		}
//		reader.endObject();
//		return obj;
//	}
//
//	public static HashMap<String, Object> readMap(JsonReader reader) throws IOException, SemanticException {
//		// reader.beginArray();
//		reader.beginObject();
//
//		HashMap<String, Object> m = new HashMap<String, Object>();
//
//		JsonToken tk = reader.peek();
//		// error tolerating is necessary?
//		if (tk == JsonToken.END_DOCUMENT)
//			return m;
//
//		// while (tk != JsonToken.END_DOCUMENT && tk != JsonToken.END_ARRAY) {
//		while (tk != JsonToken.END_DOCUMENT && tk != JsonToken.END_OBJECT) {
//			// reader.beginObject();
//			tk = reader.peek();
//			if (tk == JsonToken.NAME) {
//				String n = reader.nextName();
//				tk = reader.peek();
//				Object v = null;
//				if (tk == JsonToken.BEGIN_ARRAY)
//					v = readLstStrs(reader);
//				else if (tk == JsonToken.BEGIN_OBJECT)
//					v = readSemanticObj(reader);
//				if (tk == JsonToken.STRING)
//					v = reader.nextString();
//				else if (tk == JsonToken.NULL) {
//					reader.nextNull();
//					v = null;
//				}
//				else
//					throw new SemanticException("Parsing Json failed. Trying reading data, but can't understand token here: %s : %s",
//							reader.getPath(), tk);
//				m.put(n, v);
//			}
////			reader.endObject();
////			tk = reader.peek();
//		}
//
////		if (tk == JsonToken.END_ARRAY)
////			reader.endArray();
//		if (tk == JsonToken.END_OBJECT)
//			reader.endObject();
//
//
//		return m;
//	}
//
//	/**@deprecated replaced by {@link #readLst_StrObj(JsonReader, Class)}<br>
//	 * - needing anson<br>
//	 * We restricted protocol complicity here. No object array! Only String array and indexed with constants.
//	 * @param reader
//	 * @return ArrayList[ Object [] ]
//	 * @throws IOException
//	 * @throws SemanticException 
//	 */
//	public static ArrayList<?> readLstStrs(JsonReader reader) throws IOException, SemanticException {
//		ArrayList<Object[]> lst = new ArrayList<Object[]>();
//		reader.beginArray();
//
//		JsonToken tk = reader.peek();
//		while (tk != JsonToken.END_ARRAY) {
//			tk = reader.peek();
//			if (tk == JsonToken.BEGIN_ARRAY) {
//				// not recursive, only support 2d string array
//				// reader.beginArray();
//				lst.add(readStrs(reader));
//				// reader.endArray();
//			}
//			else if (tk == JsonToken.BEGIN_OBJECT) {
//				// caller is trying as string array, but actually found here is an object array
//				throw new SemanticException("can't handle object array %s : %s", reader.getPath(), tk);
//			}
//			else if (tk == JsonToken.NULL) {
//				// list level 1 is [], but not inner list
//				reader.nextNull();
//				break;
//			}
//			else {
//				String[] rs = readStrs(reader);
//				lst.add(rs);
//				return lst; // 2019.05.15 - another proof of needing anson
//			}
//			tk = reader.peek();
//		}
//
//		reader.endArray();
//		return lst;
//	}
//
//	/**Read string list, but the element can be a JBody - another requirement of anson.
//	 * @param reader
//	 * @param elemClzz
//	 * @return
//	 * @throws IOException 
//	 * @throws SemanticException 
//	 */
//	public static ArrayList<Object[]> readLst_StrObj(JsonReader reader, Class<? extends JBody> elemClzz)
//			throws IOException, SemanticException {
//		ArrayList<Object[]> lst = new ArrayList<Object[]>();
//		reader.beginArray();
//
//		JsonToken tk = reader.peek();
//		while (tk != JsonToken.END_ARRAY) {
//			tk = reader.peek();
//			if (tk == JsonToken.BEGIN_ARRAY) {
//				// not recursive, only support 2d string array
//				// reader.beginArray();
//				lst.add(readStrObjs(reader, elemClzz));
//				// reader.endArray();
//			}
//			else if (tk == JsonToken.BEGIN_OBJECT) {
//				// caller is trying as string array, but actually found here is an object array
//				throw new SemanticException("can't handle object array %s : %s", reader.getPath(), tk);
//			}
//			else if (tk == JsonToken.NULL) {
//				// list level 1 is [], but not inner list
//				reader.nextNull();
//				break;
//			}
//			else {
//				String[] rs = readStrs(reader);
//				lst.add(rs);
//				return lst; // 2019.05.15 - another proof of needing anson
//			}
//			tk = reader.peek();
//		}
//
//		reader.endArray();
//		return lst;
//	}
//
//	@SuppressWarnings("unchecked")
//	public static ArrayList<ArrayList<Object[]>> readLstLstStrs(JsonReader reader)
//			throws IOException, SemanticException {
//		ArrayList<ArrayList<Object[]>> lstlst = new ArrayList<ArrayList<Object[]>>();
//		reader.beginArray();
//
//		JsonToken tk = reader.peek();
//		while (tk != JsonToken.END_ARRAY) {
//			tk = reader.peek();
//			if (tk == JsonToken.BEGIN_ARRAY) {
//				// not recursive, only support 2d string array
//				// reader.beginArray();
//				while (tk != JsonToken.END_ARRAY) {
//					lstlst.add((ArrayList<Object[]>) readLstStrs(reader));
//					tk = reader.peek();
//				}
//				// reader.endArray();
//			}
//			else
//				throw new SemanticException("can't handle object array");
//			tk = reader.peek();
//		}
//
//		reader.endArray();
//		return lstlst;
//	}
//	
//	public static ArrayList<SemanticObject> readSmtcsObjs(JsonReader reader, boolean ignoreStartingArr)
//			throws IOException, SemanticException {
//		ArrayList<SemanticObject> lst = new ArrayList<SemanticObject>();
//		if (!ignoreStartingArr)
//			reader.beginArray();
//		JsonToken tk = reader.peek();
//		while (tk != JsonToken.END_DOCUMENT && tk != JsonToken.END_ARRAY) {
//			if (tk == JsonToken.BEGIN_OBJECT) {
//				lst.add(readSemanticObj(reader));
//			}
//			tk = reader.peek();
//		}
//		reader.endArray();
//		return lst;
//	}
//
//	/**@deprecated replaced by {@link #readStrObjs(JsonReader, Class)}<br>
//	 * Convert a string ot string[], not handling begin "[" and ending "]".
//	 * Caller call this because it know the string is an array according to semantics.
//	 * @param reader
//	 * @return String[]
//	 * @throws IOException
//	 * @throws SemanticException 
//	 */
//	public static String[] readStrs(JsonReader reader) throws IOException, SemanticException {
//		ArrayList<String> strs = new ArrayList<String>();
//		
//		JsonToken tk = reader.peek();
//		// error tolerating is necessary?
//		if (tk == JsonToken.END_DOCUMENT)
//			return strs.toArray(new String[] {});
//		else if (tk == JsonToken.BEGIN_ARRAY) {
//			reader.beginArray();
//			tk = reader.peek();
//		}
//
//		while (tk != JsonToken.END_DOCUMENT && tk != JsonToken.END_ARRAY) {
//			strs.add(nextString(reader));
//			tk = reader.peek();
//		}
//		
//		if (tk == JsonToken.END_ARRAY)
//			reader.endArray();
//		
//		return strs.toArray(new String[] {});
//	}
//
//	public static Object[] readStrObjs(JsonReader reader, Class<? extends JBody> clz) throws IOException, SemanticException {
//		ArrayList<Object> strs = new ArrayList<Object>();
//		
//		JsonToken tk = reader.peek();
//		if (tk == JsonToken.END_DOCUMENT)
//			return strs.toArray(new String[] {});
//		else if (tk == JsonToken.BEGIN_ARRAY) {
//			reader.beginArray();
//			tk = reader.peek();
//		}
//
//		while (tk != JsonToken.END_DOCUMENT && tk != JsonToken.END_ARRAY) {
//			tk = reader.peek();
//			if (tk == JsonToken.BEGIN_OBJECT)
//				strs.add(readBody(reader, clz));
//			else
//				strs.add(nextString(reader));
//			tk = reader.peek();
//		}
//		
//		if (tk == JsonToken.END_ARRAY)
//			reader.endArray();
//		
//		return strs.toArray(new Object[] {});
//	}
//
//	/**Convert a string[] to string[][], not handling begin "[" and ending "]".
//	 * Caller call this because it know the string is an 2d array according to semantics.
//	 * @param reader
//	 * @return 2d String array
//	 * @throws IOException
//	 * @throws SemanticException 
//	 */
//	public static String[][] readStrss(JsonReader reader) throws IOException, SemanticException {
//		ArrayList<String[]> strs = new ArrayList<String[]>();
//		
//		JsonToken tk = reader.peek();
//		// error tolerating is necessary?
//		if (tk == JsonToken.END_DOCUMENT)
//			return strs.toArray(new String[][] {});
//
//		if (tk == JsonToken.BEGIN_ARRAY)
//			reader.beginArray();
//
//		while (tk != JsonToken.END_DOCUMENT && tk != JsonToken.END_ARRAY) {
//			if (tk == JsonToken.BEGIN_ARRAY) {
//				String[] a = readStrs(reader);
//				strs.add(a);
//			}
//			else if (tk == JsonToken.NULL) {
//				reader.nextNull();
//				strs.add(null);
//			}
//			else
//				throw new SemanticException("The semantics can not been understood.");
//			tk = reader.peek();
//		}
//
//		if (tk == JsonToken.END_ARRAY)
//			reader.endArray();
//
//		return strs.toArray(new String[][] {});
//	}
//	
//	public static List<?> readLstRs(JsonReader reader) throws IOException {
//		ArrayList<Object> lst = new ArrayList<Object>();
//		reader.beginArray();
//
//		JsonToken tk = reader.peek();
//		while (tk != JsonToken.END_ARRAY) {
//			SResultset rs = readRs(reader);
//			lst.add(rs);
//			tk = reader.peek();
//		}
//
//		reader.endArray();
//		return lst;
//	}
//
//	/**Read {@link SResultset}.
//	 * @param reader
//	 * @return result set
//	 * @throws IOException
//	 */
//	private static SResultset readRs(JsonReader reader) throws IOException {
//		reader.beginArray();
//
//		HashMap<String, Integer> colnames = new HashMap<String, Integer>();
//		reader.beginArray();
//
//		// cols
//		JsonToken tk = reader.peek();
//		int c = 1;
//		while (tk != JsonToken.END_DOCUMENT && tk != JsonToken.END_ARRAY) {
//			if (tk != JsonToken.NULL)
//				colnames.put(reader.nextString(), c);
//			else reader.nextNull();
//			c++;
//			tk = reader.peek();
//		}
//		reader.endArray();
//
//		SResultset rs = new SResultset(colnames);
//		
//		// error tolerating is necessary?
//		if (tk == JsonToken.END_DOCUMENT)
//			return rs;
//
//		// rows
//		tk = reader.peek();
//		while (tk != JsonToken.END_DOCUMENT && tk == JsonToken.BEGIN_ARRAY) {
//			reader.beginArray();
//			ArrayList<Object> row = new ArrayList<Object>(colnames.size());
//			c = 0;
//			while (tk != JsonToken.END_DOCUMENT && tk != JsonToken.END_ARRAY) {
//				String v = null;
//				if (tk != JsonToken.NULL)
//					v = reader.nextString();
//				else {
//					v = null;
//					reader.nextNull();
//				};
//				row.add(c, v);
//				c++;
//				tk = reader.peek();
//			}
//			rs.append(row);
//
//			if (tk == JsonToken.END_ARRAY) {
//				reader.endArray();
//				tk = reader.peek();
//			}
//		}
//
//		if (tk == JsonToken.END_ARRAY)
//			reader.endArray();
//
//		return rs;
//	}
//
//	/**Deserialize json message into subclass of {@link JMessage}.
//	 * @param in
//	 * @param bodyItemclzz
//	 * @return message
//	 * @throws IOException
//	 * @throws ReflectiveOperationException
//	 * @throws SemanticException
//	 */
//	public JMessage<T> readJson(InputStream in, Class<? extends JBody> bodyItemclzz)
//			throws IOException, ReflectiveOperationException, SemanticException {
//
//		JMessage<T> msg = new JMessage<T>();
//
//		// {header: {header-obj}, body: [msgs]}
//		JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
//		try {
//			reader.setLenient(true);
//			if (reader.peek() == JsonToken.NULL)
//				// empty load
//				return null;
//			reader.beginObject();
//			JsonToken token = reader.peek();
//			while (token != null && token != JsonToken.END_DOCUMENT) {
//				if (token == JsonToken.NAME) {
//					String name = reader.nextName();
//					name = name == null ? null : name.trim().toLowerCase();
//					if (name != null && "port".equals(name))
//						msg.port(reader.nextString());
//					else if (name != null && "t".equals(name))
//						msg.t = nextString(reader);
//					else if (name != null && "opts".equals(name))
//						msg.opts(readOpts(reader));
//					else if (name != null && "header".equals(name))
//						msg.header(readHeader(reader));
//					else if (name != null && "body".equals(name))
//						readBody(reader, bodyItemclzz, msg);
//					else if (name != null && ("seq".equals(name) || "version".equals(name)))
//						// skip
//						nextString(reader);
//					else {
//						reader.close();
//						throw new SemanticException("Can't parse json message. Expecting port | header | body, ..., but get %s (body type: %s, message: %s)",
//								name, bodyItemclzz.toString(), msg.toString());
//					}
//				}
//				else if (token == JsonToken.END_OBJECT)
//					reader.endObject();
//				else {
//					reader.close();
//					throw new SemanticException("Can't parse json message. Expecting token NAME | END_OBJECT, but get %s (body type: %s, message: %s)",
//								token.name(), bodyItemclzz.toString(), msg.toString());
//				}
//				token = reader.peek();
//			}
//		} catch (Exception e) {
//			e.printStackTrace();
//			throw new SemanticException("Parsing Json failed. Internal error: %s", e.getMessage());
//		} finally { reader.close(); }
//		
//		return msg;
//	}
//
//	public static String nextString(JsonReader reader) throws IOException, SemanticException {
//		JsonToken tk = reader.peek();
//		if (tk == JsonToken.STRING)
//			return reader.nextString();
//		else if (tk == JsonToken.NUMBER) {
//			Object v = null;
//			try {v = reader.nextBoolean();}
//			catch (Exception e) {
//				try {v = reader.nextDouble();}
//				catch (Exception e2) {
//					try {v = reader.nextInt();}
//					catch (Exception e3) {}
//			} }
//			return String.valueOf(v);
//		}
//		else if (tk == JsonToken.NULL) {
//			reader.nextNull();
//			return null;
//		}
//		else 
//			throw new SemanticException("Parsing Json failed. Trying reading string, but can't understand token here: %s : %s",
//					reader.getPath(),
//					reader.peek().name());
//	}
//
//	/**<p>Read message body into parent's body. (deserialization).</p>
//	 * <p>In the current version (0.1.0), the method using Gson, and the debugging shows only
//	 * fields with getter can be deserialized.</p>
//	 * @param reader
//	 * @param elemClass
//	 * @param parent
//	 * @throws SemanticException
//	 * @throws IOException
//	 */
//	protected static void readBody(JsonReader reader, Class<? extends JBody> elemClass,
//			JMessage<? extends JBody> parent) throws SemanticException, IOException {
//		reader.beginArray();
//
//		while (reader.hasNext() && reader.peek() != JsonToken.END_ARRAY) {
//			JBody bodyItem;
//			try {
//				Constructor<? extends JBody> ctor = elemClass.getConstructor(
//						parent.getClass(), String.class);
//
//				bodyItem = ctor.newInstance(parent, null);
//			} catch (Exception ie) {
//				throw new SemanticException("Can't find %1$s's constructor %1$s(%2$s): %3$s - %4$s",
//						elemClass.getName(), parent.getClass().getName(),
//						ie.getClass().getName(), ie.getMessage());
//			}
//
//			bodyItem.fromJson(reader);
//			parent.body(bodyItem);
//
//			// attacked?
//			if (parent.body.size() > 20)
//				throw new SemanticException("Max request body item is 20.");
//		}
//		reader.endArray();
//	}
//	
//	protected static JBody readBody(JsonReader reader, Class<? extends JBody> elemClass)
//			throws SemanticException, IOException {
//			JBody bodyItem;
//			try {
//
//				Constructor<? extends JBody> ctor = elemClass.getConstructor(
//						JMessage.class, String.class);
//
//				bodyItem = ctor.newInstance(null, null);
//			} catch (Exception ie) {
//				throw new SemanticException("Can't find %1$s's constructor: %2$s - %3$s",
//						elemClass.getName(), 
//						ie.getClass().getName(), ie.getMessage());
//			}
//
//			bodyItem.fromJson(reader);
//			return bodyItem;
//
//	}
//
//	protected static JHeader readHeader(JsonReader reader) throws IOException {
//		JHeader header = gson.fromJson(reader, JHeader.class);
//		return header;
//	}
//	
//	protected JOpts readOpts(JsonReader reader) throws IOException {
//		JOpts opts = gson.fromJson(reader, JOpts.class);
//		return opts;
//	}
//
//	public static JHeader readHeader(String head) throws IOException {
//		if (head == null || head.length() == 0)
//			return null;
//		InputStream in = new ByteArrayInputStream(head.getBytes());
//		JsonReader reader = new JsonReader(new InputStreamReader(in, "UTF-8"));
//		return readHeader(reader);
//	}
//	
//	public static void logi(SemanticObject obj) {
//		try {
//			if (printCaller) {
//				StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
//				System.out.println(String.format("log by        %s.%s(%s:%s)", 
//								stElements[2].getClassName(), stElements[2].getMethodName(),
//								stElements[2].getFileName(), stElements[2].getLineNumber()));
//				if (stElements.length > 3)
//				System.out.println(String.format("              %s.%s(%s:%s)", 
//								stElements[3].getClassName(), stElements[3].getMethodName(),
//								stElements[3].getFileName(), stElements[3].getLineNumber()));
//			}
//
//			if (obj != null) {
//    			OutputStream os = new ByteArrayOutputStream();
//    			writeJsonResp(os, obj, new JOpts());
//				System.out.println(os.toString());
//			}
//
//		} catch (Exception ex) {
//			StackTraceElement[] x = ex.getStackTrace();
//			System.err.println(String.format("logi(): Can't print. Error: %s. called by %s.%s()",
//					ex.getMessage(), x[0].getClassName(), x[0].getMethodName()));
//		}
//	}
//
//	/**<p>Deserializing a list of UpdateReqs.</p>
//	 * <b>Note:</b>
//	 * <p>All request element is deserialized a UpdateReq, so this can only work for Update/Insert request.</p>
//	 * @param reader
//	 * @return UpdateReq list
//	 * @throws IOException 
//	 * @throws SemanticException 
//	 */
//	public static ArrayList<UpdateReq> readLstUpdateReq(JsonReader reader)
//			throws SemanticException, IOException {
//		reader.beginArray();
//		JsonToken tk = reader.peek();
//		ArrayList<UpdateReq> upds = new ArrayList<UpdateReq>();
//		while (tk != JsonToken.END_ARRAY && tk != JsonToken.END_DOCUMENT) {
//			UpdateReq post = new UpdateReq(null, null, null, null);
//			post.fromJson(reader);
//			upds.add(post);
//			tk = reader.peek();
//		}
//		if (tk == JsonToken.END_ARRAY)
//			reader.endArray();
//		return upds;
//	}
//
//
//}
//
