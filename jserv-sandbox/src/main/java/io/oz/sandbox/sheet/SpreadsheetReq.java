package io.oz.sandbox.sheet;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

public class SpreadsheetReq extends AnsonBody {
	static class A {
		public static final String records = "r";
		public static final String insert = "c";
		public static final String update = "u";
		public static final String delete = "d";
	}

	public MyCurriculum rec;

	protected SpreadsheetReq() {
		super(null, null);
	}

	protected SpreadsheetReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}

}
