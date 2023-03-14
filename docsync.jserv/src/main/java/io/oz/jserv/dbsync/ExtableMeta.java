package io.oz.jserv.dbsync;

import io.odysz.semantics.meta.TableMeta;

public class ExtableMeta extends TableMeta {
	/** resource provider, to be name to resourcer? */
	public final String synoder;
	/** full path (one DB field) that will be used as the other part of PK, where PK = synoder + clientpath */
	public final String clientpath;

	public final String syncFlag;

	public final String stamp;

	public ExtableMeta(String tbl, String[] conn) {
		super(tbl, conn);
		
		this.synoder = "synode";
		this.clientpath = "clientpath";
		this.syncFlag = "sync";
		this.stamp = "synctamp";
	}
	

}
