package io.oz.jserv.dbsync;

import java.util.List;

import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.transact.sql.PageInf;

public class DBSyncResp extends AnsonResp {
	
	PageInf pageInf;

	long blockSeqReply;

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

}
