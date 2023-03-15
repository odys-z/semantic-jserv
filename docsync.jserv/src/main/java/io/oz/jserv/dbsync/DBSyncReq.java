package io.oz.jserv.dbsync;

import java.util.ArrayList;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

public class DBSyncReq extends AnsonBody {
	public static class A {
		/** open a clean session (negotiation task size?) */
		public static final String open = "r/open";

		/** query clean tasks */
		public static final String cleans = "r/cleans";

		/** push merged results: deletings, rejects, erasings */
		public static final String pushMerged = "u/mergeds";
	}

	/**
	 * Compose a request for a page for opening a ext-resource synchronizing session.
	 * @param entity
	 * @param myId
	 * @param wind
	 * @return
	 */
	public static AnsonMsg<DBSyncReq> extabl(String entity, String myId, TimeWindow wind) {
		return null;
	}

	public String tabl;
	ArrayList<String> deletings;
	ArrayList<String> rejects;
	ArrayList<String> erasings;

	protected DBSyncReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}
	
	public DBSyncReq(String uri, CleanTask cleanTask) {
		super(null, uri);
	}

	public DBSyncReq(String uri) {
		super(null, uri);
	}

	public DBSyncReq mergeResults(ArrayList<String> deletings,
			ArrayList<String> rejects, ArrayList<String> erasings) {
		
		a = A.pushMerged;
		this.deletings = deletings;
		this.rejects = rejects;
		this.erasings = erasings;

		return this;
	}

}
