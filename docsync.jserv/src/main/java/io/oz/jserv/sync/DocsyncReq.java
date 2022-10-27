package io.oz.jserv.sync;

import io.odysz.common.LangExt;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantics.x.SemanticException;

public class DocsyncReq extends DocsReq {
	public static class A {
		public static final String query = "r/list"; 
		public static final String close = "u";
		public static final String download = "rec"; 
	}

	protected DocsyncReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}

	public DocsyncReq() {
		super(null, null);
	}

	public DocsyncReq(String family) throws SemanticException {
		super(null, null);
		if (LangExt.isblank(family, "/"))
			throw new SemanticException("DocsyncReq.family can not be empty.");
		this.org = family;
	}

	public DocsyncReq with(String device, String fullpath) {
		this.clientpath = fullpath;
		this.device = device;
		return this;
	}
	
	public DocsyncReq docTabl(String tabl) {
		this.docTabl = tabl;
		return this;
	}

	public DocsyncReq query(DocTableMeta meta) {
		a = A.query;
		docTabl = meta.tbl;
		return this;
	}

}
