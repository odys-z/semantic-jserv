package io.odysz.semantic.jprotocol;

import java.io.IOException;
import java.lang.reflect.Field;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public abstract class JBody {
	public static String[] jcondt(String logic, String field, String v, String tabl) {
		return new String[] {logic, field, v, tabl};
	}

//	public static String[] expr(String alais, String expr, Object object) {
//		return null;
//	}

	protected JMessage<? extends JBody> parent;

	protected JBody(JMessage<? extends JBody> parent) {
		this.parent = parent;
	}

	/** Action: login | c | r | u | d */
	protected String a;

	/**get action */
	public String a() {
		return a;
	}

	public JBody a(String act) {
		this.a = act;
		return this;
	}

	/** Serialize this object into json, with help of JsonWriter. { a: login, ... }
	 * @param writer
	 * @throws IOException
	 */
	public abstract void toJson(JsonWriter writer) throws IOException;

	/**For debug, print, etc. The string can not been used for json data.
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		StringBuffer b = new StringBuffer(String.format(
				"{<%s>\n\t\ta: %s,", getClass().getName(), a));
		Field[] fdList = this.getClass().getDeclaredFields();
		for (Field f : fdList) {
			String v = null;
			try { v = f.get(this).toString(); }
			catch (Throwable e) { }
			b.append(String.format("\n\t\t%s: %s,",
					f.getName(), v));
		}
		return b.append("\n\t}").toString();
	}

	/**Deserialize body item object from reader into fields.
	 * @param reader
	 * @throws IOException
	 */
	public abstract void fromJson(JsonReader reader) throws IOException;

}
