package io.odysz.semantic.jprotocol;

import java.io.IOException;

import com.google.gson.stream.JsonWriter;

public abstract class JBody {
	public static String[] jcondt(String logic, String field, String v, String tabl) {
		return new String[] {logic, field, v, tabl};
	}

//	public static String[] expr(String alais, String expr, Object object) {
//		return null;
//	}

	protected JMessage<? extends JBody> parent;

	public JBody(JMessage<? extends JBody> parent) {
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

	/** Sesrialize this object into json, with help of JsonWriter
	 * @param writer
	 * @throws IOException
	 */
	public abstract void toJson(JsonWriter writer) throws IOException;

}
