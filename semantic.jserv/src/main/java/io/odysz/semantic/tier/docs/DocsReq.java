package io.odysz.semantic.tier.docs;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.musteqs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.UserReq;
import io.odysz.semantic.meta.DocRef;
import io.odysz.semantic.tier.docs.BlockChain.IBlock;
import io.odysz.semantics.SessionInf;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.PageInf;

public class DocsReq extends UserReq implements IBlock {
	public static class A {
		/**
		 * Action: read records for synodes synchronizing.
		 * @see DocsTier#list(DocsReq, IUser)
		 * @deprecated
		 * */
		public static final String syncdocs = "r/syncs";

		/** List all nodes, includeing devices &amp; synodes of the family */
		public static final String orgNodes = "r/synodes";

		/**
		 * Action: read records for client path matching.
		public static final String records = "r/list";
		 */
		
		/** @deprecated function not used */
		public static final String mydocs = "r/my-docs";

		/** query doc / entity with entity fields, id, etc. */
		public static final String rec = "r/rec";

		public static final String download = "r/download";

		/**
		 * Download a doc using ranges property in request headers.
		 * 
		 * In semantic.jserv 1.5.16, docsync.jserv 0.2.4, this is actually used at server
		 * side, as the download with http 206 response is intercepted at ServPort.doGet(),
		 * which is actually a hack into the protocol for understandable by browsers. 
		 */
		public static final String download206 = "r/doc206";
		/** @deprecated */
		public static final String upload = "c";

		/** request for deleting docs */
		public static final String del = "d";

		public static final String blockStart = "c/b/start";
		public static final String blockUp = "c/b/block";
		public static final String blockEnd = "c/b/end";
		public static final String blockAbort = "c/b/abort";

		/** Query client paths, the sync-page */
		public static final String selectSyncs = "r/syncflags";

		/** select devices, requires user org-id as parameter from client */
		public static final String devices = "r/devices";

		public static final String registDev = "c/device";

		/** check is a new device name valid */
		public static final String checkDev = "r/check-dev";

		/** Requests works start synodes' synchronization
		 * @since 1.5.17, anclient.cmake 0.1.0,
		 * this is used for IPC to get ready to push ({@link #blockStart}).
		 */
		public static String requestSyn = "u/syn";

		/** Query synchronizing tasks - for pure device client
		public static final String selectDocs = "sync/tasks"; */
	}

	public String synuri;

	public String docTabl;
	public DocsReq docTabl(String tbl) {
		docTabl = tbl;
		return this;
	}

	public ExpSyncDoc doc;

	public PageInf pageInf;

	/**
	 * @param whereqs (n0, v0), (n1, v1), ..., must be even number of elements.
	 * @return this
	 */
	public DocsReq pageInf(int page, int size, String... whereqs) {
		pageInf = new PageInf(page, size, whereqs);
		return this;
	}

	String[] deletings;

	/**
	 * <b>Note: use {@link #DocsReq(String)}</b><br>
	 * Don't use this constructor - should only be used by json deserializer. 
	 */
	public DocsReq() {
		super(null, null);
		blockSeq = -1;
	}

	/**
	 * @param syncTask i.e. docTable name, could be a design problem?
	 */
	public DocsReq(String syncTask, String uri) {
		super(null, uri);
		blockSeq = -1;
		docTabl = syncTask;
	}

	public DocsReq(String uri) {
		super(null, uri);
		blockSeq = -1;
	}

	public DocsReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
		blockSeq = -1;
	}

	public DocsReq(AnsonMsg<? extends AnsonBody> parent, String uri, IFileDescriptor p) {
		super(parent, uri);
		device = new Device(null, null, p.device());
		doc = new ExpSyncDoc(p)
				.clientpath(p.fullpath());
	}


	public DocsReq(String docTabl, ExpSyncDoc doc, String uri) {
		super(null, uri);
		this.device = new Device(null, null, doc.device());
		this.doc = doc.escapeClientpath();
		this.docTabl = docTabl;
	}

	public DocsReq(DocRef doc, String uri) {
		super(null, uri);
		this.doc = (ExpSyncDoc) new ExpSyncDoc(doc.docm)
				.recId(doc.docId)
				.clientname(doc.pname)
				.uri64(doc.uri64)
				.uids(doc.uids);
		musteqs(doc.syntabl, this.doc.tabl());
		this.docTabl = doc.syntabl;
	}

	protected String stamp;
	public String stamp() { return stamp; }

	/**
	 * The page of quirying client files status - not for used between jservs. 
	 */
	protected PathsPage syncingPage;
	public PathsPage syncingPage() { return syncingPage; }

	protected Device device; 
	public Device device() { return device; }
	public DocsReq device(String devid) {
		device = new Device(devid, devid);
		return this;
	}
	public DocsReq device(Device d) {
		device = d;
		return this;
	}

	protected ArrayList<ExpSyncDoc> syncQueries;
	public Set<String> syncQueries() {
		return syncingPage.clientPaths == null
				? null
				: syncingPage.clientPaths.keySet();
	}

	int blockSeq;
	public int blockSeq() { return blockSeq; } 

	public DocsReq nextBlock;

	/**
	 * <p>Document sharing domain.</p>
	 * for album synchronizing, this is h_photos.family (not null).
	 * */
	public String org;
	public DocsReq org(String org) { this.org = org; return this; }

	/** If the chain already exists when starting, reset it. */
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
	 * @param d
	 * @return this
	 * @throws IOException
	 * @throws SemanticException file doesn't exist.
	 */
	public DocsReq querySync(IFileDescriptor d) throws IOException, SemanticException {
		if (d == null || isblank(d.fullpath()))
			return this;

		File f = new File(d.fullpath());
		if (!f.exists())
			throw new SemanticException("File for querying doesn't exist: %s", d.fullpath());
		/*
		if (syncQueries == null)
			syncQueries = new ArrayList<SyncDoc>();

		File f = new File(p.fullpath());
		if (!f.exists())
			throw new SemanticException("File for querying doesn't exist: %s", p.fullpath());

		syncQueries.add(new SyncDoc(p, p.fullpath(), null));
		*/
		if (pageInf == null) {
			pageInf = new PageInf();
		}

		return this;
	}

	public DocsReq syncing(PathsPage page) {
		this.syncingPage = page;
		return this;
	}

	public DocsReq blockStart(IFileDescriptor file, SessionInf usr) throws SemanticException {
		this.device = new Device(usr.device, null);
		if (isblank(this.device, "\\.", "/"))
			throw new SemanticException("User object used for uploading file must have a device id - for distinguish files. %s", file.fullpath());

		doc = doc == null
			? new ExpSyncDoc(file)
				.clientpath(file.fullpath())
				.folder(usr.device + "-" + usr.uid())
			: doc; 

		// this.docName = file.clientname();
		// this.createDate = file.cdate();
		this.blockSeq = 0;
		
		this.a = A.blockStart;
		return this;
	}

	public DocsReq blockUp(int seq, DocsResp resp, String b64, SessionInf ssinf) throws SemanticException {
		return blockUp(seq, resp.xdoc, b64, ssinf);
	}

	public DocsReq blockUp(int sequence, IFileDescriptor doc, StringBuilder b64, SessionInf usr) throws SemanticException {
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
	 * @param b64 for multi-thread style, this must be copied as it is used as a reference
	 * @param usr
	 * @return this
	 * @throws SemanticException
	 */
	public DocsReq blockUp(int sequence, IFileDescriptor doc, String b64, SessionInf usr) throws SemanticException {
		this.device = new Device(usr.device, null);
		if (isblank(this.device, ".", "/"))
			throw new SemanticException("File to be uploaded must come with user's device id - for distinguish files");

		this.blockSeq = sequence;

		this.doc = new ExpSyncDoc(doc);

		this.doc.uri64 = b64;

		this.a = A.blockUp;
		return this;
	}

	public DocsReq blockAbort(DocsResp startAck, SessionInf usr) throws SemanticException {
		this.device = new Device(usr.device, null);

		this.blockSeq = startAck.blockSeqReply;
		this.doc = startAck.xdoc;
		// this.doc.recId = startAck.xdoc.recId();
		// this.doc.clientpath(startAck.xdoc.fullpath());

		this.a = A.blockAbort;
		return this;
	}

	public DocsReq blockEnd(DocsResp resp, SessionInf usr) throws SemanticException {
		this.device = new Device(usr.device, null);

		this.blockSeq = resp.blockSeqReply;
		this.a = A.blockEnd;

		this.doc = resp.xdoc;
		// this.doc.recId = resp.xdoc.recId();
		// this.doc.clientpath(resp.xdoc.fullpath());
		return this;
	}

	public DocsReq blockSeq(int i) {
		blockSeq = i;
		return this;
	}

	public DocsReq resetChain(boolean set) {
		this.reset = set;
		return this;
	}
	
	public DocsReq queryPath(String device, String fullpath) {
		this.doc.clientpath = fullpath;
		this.device = new Device(device, null);
		return this;
	}

	public DocsReq doc(String device, String fullpath) {
		this.device = new Device(device, null);
		this.doc = new ExpSyncDoc().device(device).clientpath(fullpath);
		return this;
	}

	public DocsReq doc(ExpSyncDoc doc) {
		this.doc = doc;
		return this;
	}

	@Override
	public ExpSyncDoc doc() { return doc; }

	@Override
	public IBlock nextBlock(IBlock block) {
		this.nextBlock = (DocsReq) block;
		return this;
	}

	@Override
	public IBlock nextBlock() { return nextBlock; }
}
