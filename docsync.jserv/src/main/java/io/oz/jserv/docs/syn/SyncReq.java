package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.musteqs;
import static io.oz.syn.ExessionAct.*;
import static io.odysz.common.LangExt.isblank;

import java.util.ArrayList;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.user.UserReq;
import io.odysz.semantic.meta.DocRef;
import io.odysz.semantic.tier.docs.BlockChain.IBlock;
import io.odysz.semantics.x.SemanticException;
import io.oz.syn.ExchangeBlock;
import io.oz.syn.ExessionAct;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.IFileDescriptor;

/**
 * @since 0.2.3
 */
public class SyncReq extends UserReq implements IBlock {
	/**
	 * @since 0.2.3
	 */
	public static class A {
		/** on joining */
		public static final String exchange = "ex/exchange";
		public static final String exclose  = "ex/close";
		public static final String exrestore= "ex/restore";
		public static final String exinit   = "ex/init";

		public static final String initjoin = "join/init";
		public static final String closejoin= "join/close";
		
		/**
		 * @since 0.2.4
		 */
		public static final String resolveRef = "ref/reslove"; 
		
		/**
		 * Query my doc-refs from a peer synode.
		 * @since 0.2.5
		 */
		public static final String queryRef2me = "r/docref";

		/**
		 * @since 0.2.5
		 */
		public static final String startDocrefPush = "u/ref-b0";

		public static final String docRefBlockUp   = "u/ref-bi";
		public static final String docRefBlockEnd  = "u/ref-b9";
		public static final String docRefBlockAbort= "u/ref-bx";

		/**
		 * Jservs management: tell me all
		 * @since 0.2.6
		 */
		public static final String queryJservs = "r/jservs";
		
		/**
		 * Jservs management: accept mine, s'il vous plaît.
		 * @since 0.2.6
		 */
		public static final String reportJserv = "u/jserv";
	}

	ExchangeBlock exblock;

	/** Only used as a query condition, for resolving doc-refs. */
	public DocRef docref;
	public SyncReq docref(DocRef ref) {
		this.docref = ref;
		if (this.doc != null && this.doc.uids != null)
			musteqs(this.doc.uids, ref.uids);
		else if (this.doc != null && isblank(this.doc.uids))
			this.doc.uids = ref.uids;
		return this;
	}
	
	public SyncReq() {
		super(null, null);
	}

	public SyncReq exblock(ExchangeBlock b) {
		exblock = b;
		return this;
	}

	public SyncReq(AnsonMsg<? extends AnsonBody> parent, String domain) {
		super(parent, "/syn/" + domain);
	}

	public int synact() {
		return exblock == null ? unexpect : exblock.synact();
	}

	/** 
	 * data to be used for resolve doc-ref.
	 * @since 0.2.5
	 */
	ExpSyncDoc doc;

	/** doc data [start-inclucive, end-exclucive] */
	long[] range;
	public SyncReq range(long start, long len) {
		range = new long[] {start, len};
		return this;
	}

	SyncReq nextblock;

	/**
	 * @since 0.2.5
	 */
	@Override
	public IBlock nextBlock(IBlock block) {
		return this.nextblock = (SyncReq) block;
	}

	/**
	 * @since 0.2.5
	 */
	@Override
	public IBlock nextBlock() { return nextblock; }

	/**
	 * @since 0.2.5
	 */
	@Override
	public IBlock blockSeq(int seq) {
		this.blockSeq = seq;
		return this;
	}

	int blockSeq;
	/**
	 * @since 0.2.5
	 */
	@Override
	public int blockSeq() {
		return blockSeq;
	}

	/**
	 * @since 0.2.5
	 */
	@Override
	public ExpSyncDoc doc() {
		return doc;
	}
	
	public SyncReq blockStart(String domain, String mysnid, String peer, int totalBlocks, IFileDescriptor f) {
		doc = doc == null ? new ExpSyncDoc(f) : doc; 
		this.exblock = new ExchangeBlock(domain, mysnid, peer, ExessionAct.mode_client);
		this.blockSeq = 0;
		this.a = A.startDocrefPush;
		return this;
	}

	/**
	 * Compose blocks for updating, with breakpoint untouched
	 * - which will be handled at server side by {@link ExpSynodetier}.
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
	public SyncReq blockUp(String domain, String me, String peer, int sequence, IFileDescriptor doc, String b64)
			throws SemanticException {
		this.blockSeq = sequence;
		this.doc = new ExpSyncDoc(doc);
		this.doc.uri64 = b64;
		this.exblock = new ExchangeBlock(domain, me, peer, ExessionAct.mode_client);
		this.a = A.docRefBlockUp;
		return this;
	}

	public SyncReq blockAbort(String domain, String me, String peer, SyncResp startAck) throws SemanticException {
		this.blockSeq = startAck == null ? -1 : startAck.blockSeq;
		this.exblock = new ExchangeBlock(domain, me, peer, ExessionAct.mode_client);
		this.a = A.docRefBlockAbort;
		return this;
	}

	public SyncReq blockEnd(String domain, String me, String peer, SyncResp resp) throws SemanticException {
		this.blockSeq = resp.blockSeq;
		this.a = A.docRefBlockEnd;
		this.docref = resp.docref_i;
		this.exblock = new ExchangeBlock(domain, me, peer, ExessionAct.mode_client);
		return this;
	}

	String avoidTabl;
	ArrayList<String> avoidUids;
	
	public SyncReq avoid(String avoidtbl, ArrayList<String> avoidUids) {
		this.avoidTabl = avoidtbl;
		this.avoidUids = avoidUids;
		return this;
	}
}
