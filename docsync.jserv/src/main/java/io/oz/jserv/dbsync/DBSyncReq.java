package io.oz.jserv.dbsync;

import java.util.ArrayList;
import java.util.List;

import io.odysz.jclient.tier.Tierec;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

public class DBSyncReq extends AnsonBody {
	public static class A {
		/** open a clean session (negotiation task size?)
		 * @deprecated */
		public static final String open = "r/open";

		/**Open a clean session,
		 * query clean tasks (negotiation task size?).*/
		public static final String cleans = "r/cleans";

		/** query a entity record */
		public static final String q_entity = "r/entity";

		/** push a entity record */
		public static final String pushEntity = "u/entity";

		/** push merged results: deletings, rejects, erasings */
		public static final String pushDRE = "u/mergeds";

		/** stream download */
		public static final String download = "r/bin";

		public static final String pushClobStart = "u/push-start";

		public static final String pushCloblock = "u/push-block";

		public static final String pushClobEnd = "c/push-end";

		public static final String pushClobAbort = "c/push-abort";
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
	String uri64;

	boolean resetChain;
	DBSyncReq nextBlock;
	int blockSeq;
	public DBSyncReq blockSeq(int seq) {
		blockSeq = seq;
		return this;
	}

	protected DBSyncReq(AnsonMsg<? extends AnsonBody> parent, String uri, String tabl) {
		super(parent, uri);
		this.tabl = tabl;
	}

	public DBSyncReq(String uri, CleanTask cleanTask) {
		super(null, uri);
	}

//	public DBSyncReq openClean() {
////		return new TimeWindow(taskName, blocksize)
////				.start(new Date())
////				.end(new Date());
//		return this;
//	}

	/**
	 * <p>Composing a request for pushing merged results.</p>
	 * @param deletings
	 * @param rejects
	 * @param erasings
	 * @return
	 */
	public DBSyncReq mergeResults(ArrayList<String> deletings,
			ArrayList<String> rejects, ArrayList<String> erasings) {

		a = A.pushDRE;
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
	
	public DBSyncReq cleanTasks() {
		// this.window = wind;
		a = A.cleans;
		return this;
	}

	public ArrayList<String[]> entSubscribes() {
		return null;
	}

}
