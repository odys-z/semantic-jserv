//package io.odysz.semantic.ext;
//
//import io.odysz.semantics.meta.TableMeta;
//
///**
// * @deprecated
// * <pre>
// drop table if exists a_synodes;
// CREATE TABLE a_synodes (
//	synid   varchar2(64) NOT NULL,-- user input
//	org     varchar2(12) NOT NULL,
//	mac     varchar2(256),        -- if possible
//	remarks varchar2(256),        -- empty?
//	startup DATETIME,             -- last bring up time
//	os      varchar2(256),        -- android, browser, etc.
//	version varchar2(64),         -- docsync.jserv version
//	extra   varchar2(512),
//	PRIMARY KEY (synid)
// );
//
// drop table if exists a_sharelog;
// create table a_sharelog (
//	org    varchar2(12) NOT NULL, -- family, fk-on-del
//	tabl   varchar2(64) NOT NULL, -- doc's business name, e.g. 'h_photos'
//	docId  varchar2(12) NOT NULL, -- fk-on-del
//	synid  varchar2(64) NOT NULL, -- fk-on-del, synode &amp; device
//	clientpath text     NOT NULL,
//	expire date
// );</pre>
// *
// * @author odys-z@github.com
// *
// */
//public class SharelogMeta extends TableMeta {
//
//	public final String parentbl;
//	public final String parentpk;
//	public final String familyTbl;
//	public final String synodeTbl;
//
//	public final String org;
//	public final String docTbl;
//	public final String synid;
//	public final String clientpath;
//	public final String dstpath;
//	public final String expire;
//	public final String docFk;
//
//
//	public SharelogMeta(String parentbl, String parentpk, String... conn) {
//		super("a_sharelog", conn);
//		
//		this.parentbl = parentbl;
//		this.parentpk = parentpk;
//		this.synodeTbl = "a_synodes";
//		this.familyTbl = "a_orgs";
//
//		this.synid = "synid";
//		this.docFk = "docId";
//		this.org = "org";
//		this.clientpath = "clientpath";
//		this.dstpath = "dstpath";
//		this.docTbl = "tabl";
//		this.expire = "expire";
//	}
//
//	/**
//	 * Specify select-element from synodes table, for inserting into share-log.
//	 * @return cols
//	 */
//	public String[] insertShorelogCols() {
//		return new String[] {
//			synid, org, docTbl, docFk
//		};
//	}
//}
