package io.oz.jserv.docsync;
//package io.oz.jserv.sync;
//
//import io.odysz.common.LangExt;
//import io.odysz.semantic.jprotocol.AnsonBody;
//import io.odysz.semantic.jprotocol.AnsonMsg;
//import io.odysz.semantic.tier.docs.DocsReq;
//import io.odysz.semantics.x.SemanticException;
//
//public class DocPageReq extends DocsReq {
//	public static class A {
//		public static final String query = "r/list"; 
//		public static final String close = "u";
//		public static final String download = "rec"; 
//	}
//
//	protected DocPageReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
//		super(parent, uri);
//	}
//
//	public DocPageReq() {
//		super(null, null);
//	}
//
//	public DocPageReq(String family) throws SemanticException {
//		super(null, null);
//		if (LangExt.isblank(family, "/"))
//			throw new SemanticException("DocsyncReq.family can not be empty.");
//		this.org = family;
//	}
//
////	public DocPageReq with(String device, String fullpath) {
////		this.clientpath = fullpath;
////		this.device = device;
////		return this;
////	}
//	
//	public DocPageReq docTabl(String tabl) {
//		this.docTabl = tabl;
//		return this;
//	}
//
////	@Override
////	public DocPageReq query(DocTableMeta meta) {
////		a = A.query;
////		docTabl = meta.tbl;
////		return this;
////	}
//
//}
