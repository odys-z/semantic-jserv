package io.oz.jserv.sync;
//package io.odysz.semantic.tier.docs.sync;
//
//import io.odysz.semantic.jprotocol.AnsonBody;
//import io.odysz.semantic.jprotocol.AnsonMsg;
//import io.odysz.semantic.tier.docs.DocsReq;
//
//public class DocsyncReq extends DocsReq {
//	public static class A {
//		public static final String query = "r/list"; 
//		// public static final String syncf = "sync-f"; 
//		public static final String close = "u";
//		public static final String download = "rec"; 
//	}
//
//	protected DocsyncReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
//		super(parent, uri);
//	}
//
//	public DocsyncReq() {
//		super(null, null);
//	}
//
//	public AnsonBody with(String fullpath, String device) {
//		this.clientpath = fullpath;
//		this.device = device;
//		return this;
//	}
//
//}
