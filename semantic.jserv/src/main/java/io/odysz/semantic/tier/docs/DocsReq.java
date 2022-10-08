package io.odysz.semantic.tier.docs;

import java.util.ArrayList;

import io.odysz.anson.AnsonField;
import io.odysz.common.LangExt;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jsession.SessionInf;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.PageInf;

public class DocsReq extends AnsonBody {
	public static class A {
		/**
		 * Action: read records
		 * @see DocsTier#list(DocsReq req, IUser usr)
		 * @see Docsyncer#query(DocsReq jreq, IUser usr) 
		 * */
		public static final String records = "r/list";
		public static final String mydocs = "r/my-docs";
		public static final String rec = "r/rec";
		public static final String download = "r/download";
		public static final String upload = "c";
		/** request for deleting docs */
		public static final String del = "d";

		public static final String blockStart = "c/b/start";
		public static final String blockUp = "c/b/block";
		public static final String blockEnd = "c/b/end";
		public static final String blockAbort = "c/b/abort";

		/**
		 * Action: close synchronizing task
		 * @see io.odysz.semantic.tier.docs.sync.SyncWorker#syncDoc(ISyncFile p, SessionInf worker, AnsonHeader header)
		 */
		public static final String synclose = "u/close";
	}

//	public static class State {
//		public static final String confirmed = "conf";
//		public static final String published = "publ";
//		public static final String closed = "clos";
//		public static final String deprecated = "depr";
//	}

//	/** 
//	 * <p>Doc state: shared as public file.</p>
//	 * <p>docs created at hub:</p>
//	 *          hub state --&gt; both<br>
//	 * public : {@link #shareCloudHub} - (pulled) -&gt; {@link #sharePublic}<br/>
//	 * private: {@link #shareCloudPrv} - (pulled) -&gt; {@link #sharePrivate}<br/>
//	 * <p>docs created at private:</p>
//	 *          private state --&gt; both<br>
//	 * public : {@link #sharePrvHub}  - (pushed) -&gt; {@link #sharePublic}<br/>
//	 * private: {@link #sharePrvTmp}  - (synced) -&gt; {@link #sharePrivate}<br/>
//	 */
//	public static final String sharePublic = "pub";

//	/**
//	 * <p>Doc state: uploaded to hub for sharing.</p>
//	 * @see #sharePublic
//	 */
//	public static final String shareCloudHub = "hub";
//	/**
//	 * <p>temporary buffered at cloud.</p>
//	 * @see #sharePublic
//	 */
//	public static final String shareCloudPrv = "h-p";
//	/**
//	 * privately shared
//	 * @see #sharePublic
//	 * */
//	public static final String sharePrivate = "prv";
//	/** 
//	 * Doc state: doc is created as public and buffered at private node 
//	 * @see #sharePublic
//	 */
//	public static final String sharePrvHub = "p-h";
//	/**
//	 * Doc state: doc is created as private at private node
//	 * (state will turn to {@link #sharePrivate} by synchronizer). 
//	 * @see #sharePublic
//	 */
//	public static final String sharePrvTmp = "p.t";

	public PageInf page;

	public String docTabl;
	public String docId;
	public String docName;
	public String createDate;
	public String clientpath;
	public String mime;
	public String subFolder;

	@AnsonField(shortenString = true)
	public String uri64;

	String[] deletings;

	public String shareflag;
	public String shareby;
	public String shareDate;
	
	/**
	 * <b>Note: use {@link #DocsReq(String)}</b><br>
	 * Don't use this constructor - should only be used by json deserializer. 
	 */
	public DocsReq() {
		super(null, null);
		blockSeq = -1;
		
		subFolder = "";
	}

	/**
	 * @param syncTask i.e. docTable name, could be a design problem?
	 */
	public DocsReq(String syncTask) {
		super(null, null);
		blockSeq = -1;
		docTabl = syncTask;
		subFolder = "";
	}

	protected DocsReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
		blockSeq = -1;
		subFolder = "";
	}

	public DocsReq(AnsonMsg<? extends AnsonBody> parent, String uri, IFileDescriptor p) {
		super(parent, uri);
		device = p.device();
		clientpath = p.fullpath();
		docId = p.recId();
	}

	/**
	 * The page of quirying client files status - not for used between jservs. 
	 */
	protected SyncingPage syncing;
	public SyncingPage syncing() { return syncing; }

	protected String device; 
	public String device() { return device; }
	public DocsReq device(String d) {
		device = d;
		return this;
	}

	protected ArrayList<SyncDoc> syncQueries;
	public ArrayList<SyncDoc> syncQueries() { return syncQueries; }

	protected long blockSeq;
	public long blockSeq() { return blockSeq; } 

	public DocsReq nextBlock;

	/**
	 * <p>Document sharing domain.</p>
	 * for album synchronizing, this is h_photos.family (not null).
	 * */
	public String org;
	public DocsReq org(String org) { this.org = org; return this; }

//	public DocsReq querySync(IFileDescriptor p) {
//		if (syncQueries == null)
//			syncQueries = new ArrayList<SyncDoc>();
//		syncQueries.add(new SyncDoc(p));
//		return this;
//	}

	public DocsReq syncing(SyncingPage page) {
		this.syncing = page;
		return this;
	}

	public DocsReq blockStart(IFileDescriptor file, SessionInf usr) throws SemanticException {
		this.device = usr.device;
		if (LangExt.isblank(this.device, ".", "/"))
			throw new SemanticException("User object used for uploading file must have a device id - for distinguish files. %s", file.fullpath());

		this.clientpath = file.fullpath(); 
		this.docName = file.clientname();
		this.createDate = file.cdate();
		this.blockSeq = 0;
		
		this.a = A.blockStart;
		return this;
	}

	public DocsReq blockUp(long sequence, DocsResp resp, StringBuilder b64, SessionInf usr) throws SemanticException {
		String uri64 = b64.toString();
		return blockUp(sequence, resp, uri64, usr);
	}
	
	public DocsReq blockUp(long sequence, DocsResp resp, String s64, SessionInf usr) throws SemanticException {
		this.device = usr.device;
		if (LangExt.isblank(this.device, ".", "/"))
			throw new SemanticException("File to be uploaded must come with user's device id - for distinguish files");

		this.blockSeq = sequence;

		this.docId = resp.recId();
		this.clientpath = resp.fullpath();
		this.uri64 = s64;

		this.a = A.blockUp;
		return this;
	}

	public DocsReq blockAbort(DocsResp startAck, SessionInf usr) throws SemanticException {
		this.device = usr.device;

		this.blockSeq = startAck.blockSeqReply;

		this.docId = startAck.recId();
		this.clientpath = startAck.fullpath();

		this.a = A.blockAbort;
		return this;
	}

	public DocsReq blockEnd(DocsResp resp, SessionInf usr) throws SemanticException {
		this.device = usr.device;

		this.blockSeq = resp.blockSeqReply;

		this.docId = resp.recId();
		this.clientpath = resp.fullpath();

		this.a = A.blockEnd;
		return this;
	}

	public DocsReq blockSeq(int i) {
		blockSeq = i;
		return this;
	}

	public DocsReq folder(String name) {
		subFolder = name;
		return this;
	}

	public DocsReq share(SyncDoc p) {
		shareflag = p.shareflag;
		shareby = p.shareby;
		shareby = p.sharedate;
		return this;
	}

	public DocsReq clientpath(String path) {
		clientpath = path;
		return this;
	}
}
