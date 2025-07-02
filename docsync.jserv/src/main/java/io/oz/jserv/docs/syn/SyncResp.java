package io.oz.jserv.docs.syn;

import static io.odysz.semantic.syn.ExessionAct.unexpect;

import java.util.ArrayList;
import java.util.HashMap;

import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.meta.DocRef;
import io.odysz.semantic.syn.ExchangeBlock;
import io.odysz.semantic.tier.docs.ExpSyncDoc;

/**
 * @since 0.2.3
 */
public class SyncResp extends AnsonResp {
	String domain;

	ExchangeBlock exblock;

	/**
	 * [uids: {@link DocRef}]
	 * @since 0.2.5 */
	public HashMap<String,DocRef> docrefs;
	
	/** @since 0.2.5 */
	public String docrefsTabl;

	/**
	 * Used, started by / in a doc-chain.
	 * @since 0.2.5
	 */
	DocRef docref_i;
	public SyncResp docref(DocRef dr) {
		this.docref_i = dr;
		return this;
	}

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

	public SyncResp docrefs(HashMap<String,DocRef> refs) {
		this.docrefs = refs;
		return this;
	}

	ExpSyncDoc docByRef;
	public SyncResp doc(ExpSyncDoc doc) {
		this.docByRef = doc;
		return this;
	}

	int blockSeq;
	public SyncResp blockSeq(int seq) {
		blockSeq = seq;
		return this;
	}

	
}
