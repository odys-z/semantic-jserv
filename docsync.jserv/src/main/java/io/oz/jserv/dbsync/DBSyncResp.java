package io.oz.jserv.dbsync;

import java.util.List;

import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.tier.docs.SynEntity;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.transact.sql.PageInf;

public class DBSyncResp extends AnsonResp {
	
	PageInf pageInf;
	long blockSeqReply;
	Clobs clobchain;
	SynEntity entity;

	public List<CleanTask> cleanTasks() {
		// TODO Auto-generated method stub
		return null;
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
		return null;
	}

	public DBSyncResp doc(SyncDoc fullpath) {
		// TODO Auto-generated method stub
		return null;
	}

	public DBSyncResp start(Clobs chain) {
		this.clobchain = chain;
		return this;
	}

	public DBSyncResp entity(SynEntity e) {
		this.entity = e;
		return this;
	}

}
