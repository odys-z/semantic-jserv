package io.odysz.semantic.jserv.R;

import io.odysz.module.rs.SResultset;
import io.odysz.semantic.jprotocol.JMessage;

public class QueryResp extends JMessage {

	private SResultset rs;

	public QueryResp() {
		super(Port.query);
	}

	public QueryResp rs(SResultset rs) {
		this.rs = rs;
		return this;
	}

}
