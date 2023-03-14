package io.oz.jserv.dbsync;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.transact.sql.PageInf;

public class DBSyncResp extends AnsonResp {
	
	PageInf pageInf;

	public AnResultset tasks() {
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

}
