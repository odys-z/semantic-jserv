package io.odysz.semantic.tier.docs;

import java.util.ArrayList;

import io.odysz.anson.AnsonField;
import io.odysz.common.LangExt;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantics.x.SemanticException;

public class DocsReq extends AnsonBody {
	public static class A {
		public static final String records = "r/list";
		public static final String mydocs = "r/my-docs";
		public static final String rec = "r/rec";
		public static final String upload = "c";
		public static final String del = "d";

		public static final String blockStart = "c/b/start";
		public static final String blockUp = "c/b/block";
		public static final String blockEnd = "c/b/end";
		public static final String blockAbort = "c/b/abort";
	}

	public static class State {
		public static final String confirmed = "conf";
		public static final String published = "publ";
		public static final String closed = "clos";
		public static final String deprecated = "depr";
	}


	public String docId;
	public String docName;
	public String clientpath;
	public String mime;

	@AnsonField(shortoString = true)
	public String uri64;

	String[] deletings;

	String docState;
	
	/**
	 * Output stream when this object is used as block chain node.
	@AnsonField(ignoreTo = true, ignoreFrom = true)
	FileOutputStream ofs;
	 */
	
	public DocsReq() {
		super(null, null);
		blockSeq = -1;
	}

	protected DocsReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}

	protected SyncingPage syncing;
	public SyncingPage syncing() { return syncing; }

	protected String device; 
	public String device() { return device; }

	protected ArrayList<SyncRec> syncQueries;
	public ArrayList<SyncRec> syncQueries() { return syncQueries; }

	protected long blockSeq;
	public long blockSeq() { return blockSeq; } 

	/** created by jserv, copied from {@link DocsResp} */
	String chainId;
	public String chainId() { return chainId; }

	public DocsReq nextBlock;

	public DocsReq querySync(IFileDescriptor p) {
		if (syncQueries == null)
			syncQueries = new ArrayList<SyncRec>();
		syncQueries.add(new SyncRec(p));
		return this;
	}

	public DocsReq syncing(SyncingPage page) {
		this.syncing = page;
		return this;
	}

//	public String blockChainId(IUser user) {
//		return chainId; // user.uid() + "/" + docId;
//	}
	
	public DocsReq blockStart(IFileDescriptor file, ClientDocUser usr) throws SemanticException {
		this.device = usr.device();
		if (LangExt.isblank(this.device, ".", "/"))
			throw new SemanticException("File to be uploaded must come with user's device id - for distinguish files. %s", file.fullpath());
		this.clientpath = file.fullpath(); 
		this.docName = file.clientname();
		this.blockSeq = 0;
		
		this.a = A.blockStart;
		return this;
	}

	public DocsReq blockUp(String chainId, long sequence, DocsResp resp, StringBuilder b64, ClientDocUser usr) throws SemanticException {
		String uri64 = b64.toString();
		return blockUp(chainId, sequence, resp, uri64, usr);
	}
	
	public DocsReq blockUp(String chainId, long sequence, DocsResp resp, String s64, ClientDocUser usr) throws SemanticException {
		this.device = usr.device();
		if (LangExt.isblank(this.device, ".", "/"))
			throw new SemanticException("File to be uploaded must come with user's device id - for distinguish files");

		this.chainId = chainId;
		this.blockSeq = sequence;

		this.docId = resp.recId();
		this.clientpath = resp.fullpath();
		this.uri64 = s64;

		this.a = A.blockUp;
		return this;
	}

	public DocsReq blockAbort(DocsResp startAck, ClientDocUser usr) throws SemanticException {
		this.device = usr.device();

		this.blockSeq = startAck.blockSeqReply;

		this.docId = startAck.recId();
		this.clientpath = startAck.fullpath();

		this.a = A.blockAbort;
		return this;
	}

	public DocsReq blockEnd(String chainId, DocsResp resp, ClientDocUser usr) throws SemanticException {
		this.chainId = chainId;
		this.device = usr.device();

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


}
