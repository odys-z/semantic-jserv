package io.odysz.semantic.jprotocol;

public abstract class JBody {
	public static String[] jcondt(String logic, String field, String v, String tabl) {
		return new String[] {logic, field, v, tabl};
	}

	public static String[] expr(String alais, String expr, Object object) {
		return null;
	}

	private JMessage<? extends JBody> parent;

	public JBody(JMessage<? extends JBody> parent) {
		this.parent = parent;
	}

	/** Action: login | c | r | u | d */
	private String a;

	/**get action */
	public String a() {
		return a;
	}

	public JBody a(String act) {
		this.a = act;
		return this;
	}

}
