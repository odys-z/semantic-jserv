package io.oz.jserv.sync;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.tier.docs.DocsReq;

public class DocsyncReq extends DocsReq {
	public static class A {
		public static final String query = "r/list"; 
		public static final String close = "u";
		public static final String download = "rec"; 
	}

	public static final class SyncFlag {
		/**
		 * created at cloud hub
		 */
		public static String hubInit;
		/**
		 * kept as private file
		 */
		public static String priv;
		/**
		 * to be pushed (shared) to hub
		 */
		public static String sharing;
		
	}

	protected DocsyncReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}

	public DocsyncReq() {
		super(null, null);
	}

	public DocsyncReq with(String fullpath, String device) {
		this.clientpath = fullpath;
		this.device = device;
		return this;
	}

	public DocsyncReq query() {
		a = A.query;
		return this;
	}

}
