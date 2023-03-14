package io.oz.jserv.dbsync;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

public class DBSyncReq extends AnsonBody {
	public static class A {
		/** open a clean session (negotiation task size?) */
		public static final String open = "r/open";
		/** query clean tasks */
		public static final String cleans = "r/cleans";

	}

	public String tabl;

	protected DBSyncReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}

	/**
	 * Compose a request for a page for opening a ext-resource synchronizing session.
	 * @param entity
	 * @param myId
	 * @param wind
	 * @return
	 */
	public static AnsonMsg<DBSyncReq> extabl(String entity, String myId, TimeWindow wind) {
		// TODO Auto-generated method stub
		return null;
	}

}
