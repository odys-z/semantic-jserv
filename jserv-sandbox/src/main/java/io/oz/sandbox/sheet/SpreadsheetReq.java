//package io.oz.sandbox.sheet;
//
//import io.odysz.semantic.jprotocol.AnsonBody;
//import io.odysz.semantic.jprotocol.AnsonMsg;
//import io.odysz.semantic.jserv.R.PageInf;
//import io.odysz.semantic.jserv.user.UserReq;
//import io.odysz.transact.sql.Insert;
//import io.odysz.transact.x.TransException;
//
///**
// * TODO This envolope will be changed to generic type once Antson support it.
// * 
// * @author odys-z@github.com
// *
// */
//public class SpreadsheetReq extends UserReq {
//	static class A {
//		public static final String records = "r";
//		public static final String insert = "c";
//		public static final String update = "u";
//		public static final String delete = "d";
//	}
//
//	MyCurriculum rec;
//	PageInf page;
//
//	public SpreadsheetReq() {
//		super(null, null);
//	}
//
//	protected SpreadsheetReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
//		super(parent, uri);
//	}
//
//	public SpreadsheetReq insertRec(Insert inst) throws TransException {
//		
//		if (rec == null) {
//			rec = new MyCurriculum();
//			rec.currName = "курс";
//			rec.cate = "";
//			rec.clevel = "";
//			rec.subject = "";
//			rec.module = "";
//			rec.sort = "999";
//		}
//		// else - default value by client
//
//		inst.nv("currName", rec.currName)
//			.nv("cate", rec.cate)
//			.nv("clevel", rec.clevel)
//			.nv("subject", rec.subject)
//			.nv("module", rec.module)
//			.nv("sort", rec.sort);
//		
//		return this;
//	}
//
//}