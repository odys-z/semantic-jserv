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

		/** query a entity record */
		public static final String q_entity = "r/entity";

		/** push a entity record */
		public static final String pushEntity = "u/entity";

		/** push merged results: deletings, rejects, erasings */
		public static final String pushMerged = "u/mergeds";

		/** stream download */
		public static final String download = "r/bin";

		public static final String pushExtStart = "u/push-start";

		public static final String pushExtBlock = "u/push-block";

		public static final String pushExtEnd = "c/push-end";

		public static final String pushExtAbort = "c/push-abort";
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

	/// rec condt
	String synode;
	String clientpath;
	TimeWindow window;

	protected DBSyncReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}
	
	public DBSyncReq(String uri, CleanTask cleanTask) {
		super(null, uri);
	}

	public DBSyncReq(String uri, String tabl) {
		super(null, uri);
		this.tabl = tabl;
	}

	/**
	 * <p>Composing a request for pushing merged results.</p>
	 * @param deletings
	 * @param rejects
	 * @param erasings
	 * @return
	 */
	public DBSyncReq mergeResults(ArrayList<String> deletings,
			ArrayList<String> rejects, ArrayList<String> erasings) {
		
		a = A.pushMerged;
		this.deletings = deletings;
		this.rejects = rejects;
		this.erasings = erasings;

		return this;
	}

	public DBSyncReq askEntity(String synode, String clientpath, TimeWindow win) {
		a = A.q_entity;
		this.synode = synode;
		this.clientpath = clientpath;
		this.window = win;
		return this;
	}

	public DBSyncReq pushEntity(String synode, String clientpath, TimeWindow win) {
		a = A.pushEntity;
		this.synode = synode;
		this.clientpath = clientpath;
		this.window = win;
		return this;
	}

	public DBSyncReq download(String synode, String clientpath) {
		a = A.download;
		this.synode = synode;
		this.clientpath = clientpath;
		return this;
	}

	public long blockSeq() {
		// TODO Auto-generated method stub
		return 0;
	}

}
