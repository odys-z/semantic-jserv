package io.oz.jserv.dbsync;

import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.syn.SynEntity;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.transact.sql.PageInf;

public class DBSyncResp extends AnsonResp {
	
	PageInf pageInf;
	long blockSeqReply;
	SynEntity entity;

	TimeWindow cleanWin;

	/**
	 * <p>A page of records to be merged.</p>
	 * 
	 * Page.condts: empty. For records, call {@link #rs(int)};
	 * 
	 * @return page info
	 */
	public PageInf syncPage() {
		// TODO Auto-generated method stub
		return pageInf;
	}

	public DBSyncResp blockSeq(long blockSeq) {
		// TODO Auto-generated method stub
		return this;
	}

	public DBSyncResp doc(ExpSyncDoc d) {
		// TODO Auto-generated method stub
		return this;
	}

	public DBSyncResp entity(SynEntity e) {
		this.entity = e;
		return this;
	}

	public DBSyncResp cleanWindow(String tbl) {
		cleanWin = new TimeWindow(tbl, 0); 
		return this;
	}

	public DBSyncResp cleanWindow(TimeWindow win) {
		cleanWin = win;
		return this;
	}
}
