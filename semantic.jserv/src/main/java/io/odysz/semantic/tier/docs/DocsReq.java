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

	String docId;
	String docName;
	String mime;
	String content64;

	protected DocsReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}

}
