package io.odysz.semantic.jprotocol;

public abstract class JBody {

	public JBody() { }

	public static String[] jcondt(String logic, String field, String v, String[] tabl) {
		return new String[] {logic, field, v, tabl};
	}

	public static String[] expr(String alais, String expr, Object object) {
		// TODO Auto-generated method stub
		return null;
	}

}
