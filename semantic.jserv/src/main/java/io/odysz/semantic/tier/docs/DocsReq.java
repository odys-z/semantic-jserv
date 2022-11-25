package io.odysz.semantic.tier.docs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import io.odysz.anson.AnsonField;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jsession.SessionInf;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.PageInf;

import static io.odysz.common.LangExt.isblank;

public class DocsReq extends AnsonBody {
	public static class A {
		/**
		 * Action: read records for synodes synchronizing.
		 * For client querying matching (syncing) docs, use {@link #records} instead. 
		 * @see DocsTier#list(DocsReq req, IUser usr)
		 * @see Docsyncer#query(DocsReq jreq, IUser usr) 
		 * */
		public static final String syncdocs = "r/syncs";
		/**
		 * Action: read records for client path matching.
		 * For synodes synchronizing, use {@link #syncdocs} instead. 
		 */
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
		 */
		public static final String synclose = "u/close";

		/** Query synchronizing tasks - for pure device client
		public static final String selectDocs = "sync/tasks"; */
	}

	public PageInf page;

	public String docTabl;
	public DocsReq docTabl(String tbl) {
		docTabl = tbl;
		return this;
	}

	public String docId;
	public String docName;
	public String createDate;
	public String clientpath;
	public String mime;
	public String subFolder;

	@AnsonField(shortenString = true)
	public String uri64;

	String[] deletings;

	/**
	 * Either {@link io.odysz.semantic.ext.DocTableMeta.Share#pub pub}
	 * or {@link io.odysz.semantic.ext.DocTableMeta.Share#pub priv}.
	 */
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
	protected PathsPage syncing;
	public PathsPage syncing() { return syncing; }

	protected String device; 
	public String device() { return device; }
	public DocsReq device(String d) {
		device = d;
		return this;
	}

	/** @deprecated */
	protected ArrayList<SyncDoc> syncQueries;
	/**@deprecated replaced by DocsPage.paths */
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

	public boolean reset;

	private long limit = -1;
	public long limit() { return limit; }
	public DocsReq limit(long l) {
		limit = l;
		return this;
	}

	/**
	 * @deprecated
	 * Add a doc record for matching path at synode. Should be called by device client.
	 * <p>Note: if the file path is empty, the query is ignored.</p>
	 * @param p
	 * @return this
	 * @throws IOException see {@link SyncDoc} constructor
	 * @throws SemanticException fule doesn't exists. see {@link SyncDoc} constructor 
	 */
	public DocsReq querySync(IFileDescriptor p) throws IOException, SemanticException {
		if (p == null || isblank(p.fullpath()))
			return this;

		File f = new File(p.fullpath());
		if (!f.exists())
			throw new SemanticException("File for querying doesn't exist: %s", p.fullpath());
		/*
		if (syncQueries == null)
			syncQueries = new ArrayList<SyncDoc>();

		File f = new File(p.fullpath());
		if (!f.exists())
			throw new SemanticException("File for querying doesn't exist: %s", p.fullpath());

		syncQueries.add(new SyncDoc(p, p.fullpath(), null));
		*/
		if (page == null) {
			page = new PageInf();
		}

		return this;
	}

	public DocsReq syncing(PathsPage page) {
		this.syncing = page;
		return this;
	}

	public DocsReq blockStart(IFileDescriptor file, SessionInf usr) throws SemanticException {
		this.device = usr.device;
		if (isblank(this.device, ".", "/"))
			throw new SemanticException("User object used for uploading file must have a device id - for distinguish files. %s", file.fullpath());

		this.clientpath = file.fullpath(); 
		this.docName = file.clientname();
		this.createDate = file.cdate();
		this.blockSeq = 0;
		
		this.a = A.blockStart;
		return this;
	}

	public DocsReq blockUp(int seq, DocsResp resp, String b64, SessionInf ssinf) throws SemanticException {
		return blockUp(seq, resp.doc, b64, ssinf);
	}

	public DocsReq blockUp(long sequence, IFileDescriptor doc, StringBuilder b64, SessionInf usr) throws SemanticException {
		String uri64 = b64.toString();
		return blockUp(sequence, doc, uri64, usr);
	}
	
	/**
	 * Compose blocks for updating, which will be handled at server side by {@link BlockChain}.
	 * 
	 * <p><b>issue</b>
	 * Is this means there should be a Dochain client peer?</p>
	 * 
	 * @param sequence
	 * @param doc
	 * @param s64
	 * @param usr
	 * @return this
	 * @throws SemanticException
	 */
	public DocsReq blockUp(long sequence, IFileDescriptor doc, String s64, SessionInf usr) throws SemanticException {
		this.device = usr.device;
		if (isblank(this.device, ".", "/"))
			throw new SemanticException("File to be uploaded must come with user's device id - for distinguish files");

		this.blockSeq = sequence;

		this.docId = doc.recId();
		this.clientpath = doc.fullpath();
		this.uri64 = s64;

		this.a = A.blockUp;
		return this;
	}

	public DocsReq blockAbort(DocsResp startAck, SessionInf usr) throws SemanticException {
		this.device = usr.device;

		this.blockSeq = startAck.blockSeqReply;

		this.docId = startAck.doc.recId();
		this.clientpath = startAck.doc.fullpath();

		this.a = A.blockAbort;
		return this;
	}

	public DocsReq blockEnd(DocsResp resp, SessionInf usr) throws SemanticException {
		this.device = usr.device;

		this.blockSeq = resp.blockSeqReply;

		this.docId = resp.doc.recId();
		this.clientpath = resp.doc.fullpath();

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
		shareDate = p.sharedate;
		return this;
	}

	public DocsReq clientpath(String path) {
		clientpath = path;
		return this;
	}

	public DocsReq resetChain(boolean set) {
		this.reset = set;
		return this;
	}
	
	public DocsReq queryPath(String device, String fullpath) {
		this.clientpath = fullpath;
		this.device = device;
		return this;
	}
}
