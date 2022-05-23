package io.oz.sandbox.sheet;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.R.PageInf;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.x.TransException;

public class SpreadsheetReq extends AnsonBody {
	static class A {
		public static final String records = "r";
		public static final String insert = "c";
		public static final String update = "u";
		public static final String delete = "d";
	}

	MyCurriculum rec;
	PageInf page;

	public SpreadsheetReq() {
		super(null, null);
	}

	protected SpreadsheetReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}

	public SpreadsheetReq insertRec(Insert inst) throws TransException {
		
		if (rec == null) {
			rec = new MyCurriculum();
			rec.currName = "curr name";
			rec.cate = "cate";
			rec.level = "level";
			rec.subject = "subject";
			rec.module = "module";
			rec.sort = "999";
		}
		// else - default value by client

		inst.nv("currName", rec.currName)
			.nv("cate", rec.cate)
			.nv("clevel", rec.level)
			.nv("subject", rec.subject)
			.nv("module", rec.module)
			.nv("sort", rec.sort);
		
		return this;
	}

}
