package io.odysz.semantic.jserv.R;

import io.odysz.module.rs.SResultset;
import io.odysz.semantic.jprotocol.JMessage;

public class QueryResp extends JMessage {

	private SResultset rs;

	QueryResp(SResultset rs) {
		super(Port.query);
		this.rs = rs;
	}

}
