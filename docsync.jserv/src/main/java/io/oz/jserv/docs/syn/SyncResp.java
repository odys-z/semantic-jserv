package io.oz.jserv.docs.syn;

import static io.odysz.semantic.syn.ExessionAct.unexpected;

import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.syn.ExchangeBlock;

public class SyncResp extends AnsonResp {
	String domain;

	ExchangeBlock exblock;

	public SyncResp() { }
	
	public SyncResp(String domain) {
		this.domain = domain;
	}

	public SyncResp exblock(ExchangeBlock b) {
		exblock = b;
		return this;
	}

	public int synact() {
		return exblock == null ? unexpected : exblock.synact();
	}

	public SyncResp syndomain(String domain) {
		this.domain = domain;
		return this;
	}

	
}
