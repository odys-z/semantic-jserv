package io.odysz.semantic.tier.docs;

import java.sql.SQLException;
import java.util.HashMap;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.jprotocol.AnsonResp;

/**
 * Docsyncer response.
 * 
 * This structure is recommend used as Parcel between Android activities.
 * @author ody
 *
 */
public class DocsResp extends AnsonResp {
	public SyncDoc doc;

//	long size;
//	long size() { return size; } 
//	
//	public DocsResp() {
//		super();
//		map = new HashMap<String, Object>();
//	}
//
	SyncingPage syncing;
	public SyncingPage syncing() { return syncing; }
	public DocsResp syncing(SyncingPage page) {
		syncing = page;
		return this;
	}
//
//	String recId;
//	public String recId() { return recId; }
//	public DocsResp recId(String recid) {
//		recId = recid;
//		return this;
//	}
//
//	String fullpath;
//	public String fullpath() { return fullpath; }
//	public DocsResp fullpath(String fullpath) {
//		this.fullpath = fullpath;
//		return this;
//	}
//
//	String filename;
//	public String clientname() { return filename; }
//	public DocsResp  clientname(String clientname) {
//		this.filename = clientname;
//		return this;
//	}

	public long blockSeqReply;
	public long blockSeq() { return blockSeqReply; }
	public DocsResp blockSeq(long seq) {
		blockSeqReply = seq;
		return this;
	}
	
//	String cdate;
//	public String cdate() { return cdate; }
//	public DocsResp cdate(String cdate) {
//		this.cdate = cdate;
//		return this;
//	}

	public DocsResp doc(SyncDoc d) {
		this.doc = d;
		return this;
	}

	public DocsResp doc(AnResultset rs, DocTableMeta meta) throws SQLException {
		this.doc = new SyncDoc(rs, meta);
		return this;
	}

}
