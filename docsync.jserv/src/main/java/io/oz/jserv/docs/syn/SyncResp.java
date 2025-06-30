package io.oz.jserv.docs.syn;

import static io.odysz.semantic.syn.ExessionAct.unexpect;

import java.util.ArrayList;

import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.meta.DocRef;
import io.odysz.semantic.syn.ExchangeBlock;

/**
 * @since 0.2.3
 */
public class SyncResp extends AnsonResp {
	String domain;

	ExchangeBlock exblock;

	/** @since 0.2.5 */
	public String[] docrefs_uids;

	public SyncResp() { }
	
	public SyncResp(String domain) {
		this.domain = domain;
	}

	public SyncResp exblock(ExchangeBlock b) {
		exblock = b;
		return this;
	}

	public int synact() {
		return exblock == null ? unexpect : exblock.synact();
	}

	public SyncResp syndomain(String domain) {
		this.domain = domain;
		return this;
	}

	public SyncResp docrefs(String[] uids) {
		this.docrefs_uids = uids;
		return this;
	}

	
}
