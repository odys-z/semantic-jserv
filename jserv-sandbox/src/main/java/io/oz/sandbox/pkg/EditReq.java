package io.oz.sandbox.pkg;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

public class EditReq extends AnsonBody {
	static class A {
		/** upload source and compile */
		public static final String compile = "u/compile";
	}

	protected EditReq() {
		super(null, null);
	}

	protected EditReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}

}
