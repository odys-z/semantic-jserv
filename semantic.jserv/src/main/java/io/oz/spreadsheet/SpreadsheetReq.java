package io.oz.spreadsheet;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.R.PageInf;
import io.odysz.semantic.jserv.user.UserReq;
import io.odysz.transact.x.TransException;

/**
 * Base class or Spreadsheetier's request.
 *  
 * @author odys-z@github.com
 *
 */
public abstract class SpreadsheetReq extends UserReq {
	public static class A {
		public static final String records = "r";
		public static final String rec = "rec";
		public static final String insert = "c";
		public static final String update = "u";
		public static final String delete = "d";
	}

	public ISheetRec rec() throws TransException {
		throw new TransException("This must be overrid by subclass - FIXME using generic type?");
	}

	public PageInf page;

	public SpreadsheetReq() {
		super(null, null);
	}

	protected SpreadsheetReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}
}
