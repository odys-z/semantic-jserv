package io.oz.jserv.dbsync;

import java.util.List;

import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.transact.sql.PageInf;

public class DBSyncResp extends AnsonResp {
	
	PageInf pageInf;
	long blockSeqReply;
	// Clobs clobchain;
	SynEntity entity;

	List<CleanTask> tasks;
	
	TimeWindow cleanWin;

	public List<CleanTask> cleanTasks() {
		return tasks;
	}

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

	public DBSyncResp doc(SyncDoc fullpath) {
		// TODO Auto-generated method stub
		return this;
	}

	/*
	public DBSyncResp start(Clobs chain) {
		this.clobchain = chain;
		return this;
	}
	*/

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
