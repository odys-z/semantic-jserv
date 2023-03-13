package io.oz.jserv.dbsync;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

public class DBSyncReq extends AnsonBody {
	public static class A {
		/** open a clean session (negotiation task size?) */
		public static final String open = "r/stamps";
		/** query clean tasks */
		public static final String records = "r/cleans";

	}

	public String tabl;

	protected DBSyncReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}

}
