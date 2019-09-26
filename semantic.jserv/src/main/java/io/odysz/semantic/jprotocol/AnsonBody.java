package io.odysz.semantic.jprotocol;

import java.lang.reflect.Field;

import io.odysz.anson.Anson;

public abstract class AnsonBody extends Anson {
	public static String[] jcondt(String logic, String field, String v, String tabl) {
		return new String[] {logic, field, v, tabl};
	}

	protected AnsonMsg<? extends AnsonBody> parent;

	protected String conn;
	public String conn() { return conn; }

	/** Action: login | C | R | U | D | any serv extension */
	protected String a;
	/** @return Action: login | C | R | U | D | any serv extension */
	public String a() { return a; }

	public AnsonBody a(String act) {
		this.a = act;
		return this;
	}

	protected AnsonBody(AnsonMsg<? extends AnsonBody> parent, String conn) {
		this.parent = parent;
		this.conn = conn;
	}

	/**For debug, print, etc. The string can not been used for json data.
	 * @see java.lang.Object#toString()
	public String toString() {
		StringBuffer b = new StringBuffer(String.format(
				"{<%s>\n\t\ta: %s,", getClass().getName(), a));
		Field[] fdList = this.getClass().getDeclaredFields();
		for (Field f : fdList) {
			String v = null;
			try{f.setAccessible(true);
				v = f.get(this).toString();
			}catch (Throwable e) { }
			b.append(String.format("\n\t\t%s: %s,",
					f.getName(), v));
		}
		return b.append("\n\t}").toString();
	}
	 */

}
