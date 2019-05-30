package io.odysz.jsample.cheap;

import io.odysz.sworkflow.CheapException;

public class CheapCode {
	public static final String ok = "ok";
	public static final String err = CheapException.ERR_WF;
	public static final String err_internal = CheapException.ERR_WF_INTERNAL;
	public static final String err_competate = CheapException.ERR_WF_COMPETATION;
	public static final String errRight = CheapException.ERR_WF_Rights;
	
	public static boolean eq(String l, String r) {
		return l.equals(r);
	}
	
	public static boolean isOk(String r) {
		return ok.equals(r);
	}
}
