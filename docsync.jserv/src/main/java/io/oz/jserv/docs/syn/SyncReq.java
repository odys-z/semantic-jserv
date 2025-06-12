package io.oz.jserv.docs.syn;

import static io.odysz.semantic.syn.ExessionAct.*;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.user.UserReq;
import io.odysz.semantic.syn.ExchangeBlock;
import io.odysz.semantic.tier.docs.ExpSyncDoc;

public class SyncReq extends UserReq {
	public static class A {
		/** on joining */
		public static final String exchange= "ex/exchange";
		public static final String exclose = "ex/close";
		public static final String exrest  = "ex/rest";
		public static final String exinit  = "ex/init";

		public static final String initjoin = "join/init";
		public static final String closejoin= "join/close";
		
		public static final String resolveRef = "ref/reslove"; 
	}

	ExchangeBlock exblock;
	public ExpSyncDoc doc;
	
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
}
