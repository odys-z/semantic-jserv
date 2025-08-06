//package io.oz.album.tier;
//
//import io.odysz.semantics.meta.TableMeta;
//import io.odysz.transact.x.TransException;
//
///**
// * @deprecated replaced by {@code io.oz.jserv.docs.syn.DocOrgMeta}.
// */
//public class Photo_OrgMeta extends TableMeta {
//
//	public final String oid;
//	public final String pid;
//	public final String market;
//
//	public Photo_OrgMeta(String conn) throws TransException {
//		super("h_photo_org", null, conn);
//		oid = "oid";
//		pid = "pid";
//		market = "market";
//	}
//}