package io.oz.jserv.dbsync;

import io.odysz.semantics.meta.TableMeta;

public class CleantablMeta extends TableMeta {

	public final String tabl;

	/** resource provider, to be name to resourcer? */
	public final String synoder;
	/** full path (one DB field) that will be used as the other part of PK, where PK = synoder + clientpath */
	public final String clientpath;

	public final String synodee;

	public final String flag;

	public final String stamp;

	public CleantablMeta(String tbl, String... conn) {
		super(tbl, conn);
		
		this.tabl = "syn_clean";
		this.synoder = "synoder";
		this.clientpath = "clientpath";
		this.synodee = "synodee";
		this.flag = "sync";
		this.stamp = "stamp";
	}
	
}
