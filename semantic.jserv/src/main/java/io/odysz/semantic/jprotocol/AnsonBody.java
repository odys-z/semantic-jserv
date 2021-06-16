package io.odysz.semantic.jprotocol;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonField;

public abstract class AnsonBody extends Anson {
//	public static String[] jcondt(String logic, String field, String v, String tabl) {
//		return new String[] {logic, field, v, tabl};
//	}

	@AnsonField(ref=AnsonField.enclosing)
	protected AnsonMsg<? extends AnsonBody> parent;

	protected String conn;
	public String conn() { return conn; }

	/** Action: login | pswd, and any serv port extension */
	protected String a;
	/** @return Action: login | pswd and any serv port extension */
	public String a() { return a; }

	public AnsonBody a(String act) {
		this.a = act;
		return this;
	}

	protected AnsonBody(AnsonMsg<? extends AnsonBody> parent, String conn) {
		this.parent = parent;
		this.conn = conn;
	}

}
