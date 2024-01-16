package io.odysz.semantic.jsession;

import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.R.AnQueryReq;
import io.odysz.semantics.SessionInf;
import io.odysz.semantics.x.SemanticException;

public class T_SessionClient {

	SessionInf ssInf;

	public T_SessionClient(AnSessionResp r) {
		ssInf = r.ssInf();
	}

	public AnsonMsg<AnQueryReq> query(String uri, String tbl, String alias,
			int page, int size, String... funcId) throws SemanticException {

		AnsonMsg<AnQueryReq> msg = new AnsonMsg<AnQueryReq>(Port.query);

		AnsonHeader header = new AnsonHeader(ssInf.ssid(), ssInf.uid(), ssInf.ssToken);
		if (funcId != null && funcId.length > 0)
			AnsonHeader.usrAct(funcId[0], "query", "R", "test");
		msg.header(header);

		AnQueryReq itm = AnQueryReq.formatReq(uri, msg, tbl, alias);
		msg.body(itm);
		itm.page(page, size);

		return msg;
	}
}
