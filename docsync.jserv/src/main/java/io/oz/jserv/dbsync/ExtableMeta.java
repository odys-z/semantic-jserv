//package io.oz.jserv.dbsync;
//
//import io.odysz.semantics.meta.TableMeta;
//
///**
// * Ext-file table meta
// * 
// * @deprecated redundant to DocTableMeta
// * 
// * @author odys-z@github.com
// *
// */
//public class ExtableMeta extends TableMeta {
//
//	public final String entabl;
//
//	/** resource provider, to be name to resourcer? */
//	public final String synoder;
//	/** full path (one DB field) that will be used as the other part of PK, where PK = synoder + clientpath */
//	public final String clientpath;
//
//	public final String pname;
//
//	public final String syncFlag;
//
//	public final String stamp;
//
//	public final String uri;
//
//	public ExtableMeta(String tbl, String[] conn) {
//		super(tbl, conn);
//		
//		this.entabl = "tabl";
//		this.synoder = "synode";
//		this.clientpath = "clientpath";
//		this.pname = "pname";
//		this.uri = "uri";
//		
//		this.syncFlag = "sync";
//		this.stamp = "synctamp";
//	}
//	
//
//}
