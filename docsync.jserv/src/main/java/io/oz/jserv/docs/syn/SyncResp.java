package io.oz.jserv.docs.syn;

import static io.odysz.semantic.syn.ExessionAct.*;

import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.syn.ExchangeBlock;

public class SyncResp extends AnsonResp {

	ExchangeBlock exblock;
	public SyncResp exblock(ExchangeBlock b) {
		exblock = b;
		return this;
	}

	public int synact() {
		return exblock == null ? unexpected : exblock.synact();
	}

	
}
