package io.odysz.semantic.tier.docs;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

public class DocsReq extends AnsonBody {
	public static class A {
		public static final String records = "r/list";
		public static final String rec = "r/rec";
		public static final String upload = "c";
		public static final String del = "d";
	}

	public static class State {
		public static final String confirmed = "conf";
		public static final String published = "publ";
		public static final String closed = "clos";
		public static final String deprecated = "depr";
	};

	String docId;
	String docName;
	String mime;
	String content64;
	
	public DocsReq() {
		super(null, null);
	}

	protected DocsReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}

}
