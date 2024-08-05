package io.oz.jserv.docs.syn;

import static io.odysz.semantic.syn.ExessionAct.*;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.syn.ExchangeBlock;
import io.odysz.semantic.tier.docs.ExpSyncDoc;

public class SyncReq extends AnsonBody {

	ExchangeBlock exblock;
	public ExpSyncDoc doc;

	public SyncReq exblock(ExchangeBlock b) {
		exblock = b;
		return this;
	}

	public SyncReq(AnsonMsg<? extends AnsonBody> parent, String domain) {
		super(parent, "/syn/" + domain);
	}

	public int synact() {
		return exblock == null ? unexpected : exblock.synact();
	}

}
