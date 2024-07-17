package io.odysz.semantic.jserv.syn;

import static io.odysz.semantic.syn.ExessionAct.*;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.syn.ExchangeBlock;

public class SyncReq extends AnsonBody {

	ExchangeBlock exblock;
	public SyncReq exblock(ExchangeBlock b) {
		exblock = b;
		return this;
	}

//	protected SyncReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
//		super(parent, uri);
//	}

	public SyncReq(AnsonMsg<? extends AnsonBody> parent, String domain, String synode) {
		super(parent, "/syn/" + domain + "/" + synode);
	}

	public int synact() {
		return exblock == null ? unexpected : exblock.synact();
	}

}
