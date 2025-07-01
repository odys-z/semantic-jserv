package io.oz.jserv.docs.syn;

import static io.odysz.semantic.syn.ExessionAct.*;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.user.UserReq;
import io.odysz.semantic.meta.DocRef;
import io.odysz.semantic.syn.ExchangeBlock;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.ExpSyncDoc;

/**
 * @since 0.2.3
 */
public class SyncReq extends UserReq {
	/**
	 * @since 0.2.3
	 */
	public static class A {
		/** on joining */
		public static final String exchange= "ex/exchange";
		public static final String exclose = "ex/close";
		public static final String exrest  = "ex/rest";
		public static final String exinit  = "ex/init";

		public static final String initjoin = "join/init";
		public static final String closejoin= "join/close";
		
		/**
		 * @since 0.2.4
		 */
		public static final String resolveRef = "ref/reslove"; 
		
		/**
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
	}

	ExchangeBlock exblock;

	/** Only used as a query condition, for resolving doc-refs. */
	public DocRef docref;
	
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
	/**
	 * Convert to DocsReq when {@link #doc} is available.
	 * @return a doc block to be managed
	 * @since 0.2.5
	 */
	public DocsReq toDocReq() {
		return new DocsReq().doc(this.doc);
	}
}
