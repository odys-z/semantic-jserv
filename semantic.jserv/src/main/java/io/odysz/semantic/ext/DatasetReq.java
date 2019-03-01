package io.odysz.semantic.ext;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jserv.R.QueryReq;

public class DatasetReq extends QueryReq {

	String sk;
	public String[] sqlArgs;

	public DatasetReq(JMessage<? extends JBody> parent) {
		super(parent);
	}

	public int size() { return pgsize; }
	public int page() { return page; }

}
