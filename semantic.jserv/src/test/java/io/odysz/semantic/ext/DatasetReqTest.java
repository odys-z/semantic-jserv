package io.odysz.semantic.ext;


import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.Test;

import io.odysz.common.Utils;
import io.odysz.semantic.DA.DatasetCfg.TreeSemantics;
import io.odysz.semantic.jprotocol.JHeader;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantics.x.SemanticException;

class DatasetReqTest {

	protected static JHelper<DatasetReq> jreqDs = new JHelper<DatasetReq>();

	@Test
	void testToFromJson() throws SemanticException, IOException, ReflectiveOperationException {
		Utils.printCaller(false);

		JMessage<DatasetReq> req = new JMessage<DatasetReq>(Port.dataset);
		req.t = "";

		JHeader header = new JHeader("ss-id", "uid");
		JHeader.usrAct("func-id", "query", "a_user", "R");
		req.header(header);

		TreeSemantics tree = new TreeSemantics(",a_functions,funcId id,parentId,funcName text,,,fals");
		DatasetReq itm = (DatasetReq) DatasetReq
				.formatReq("inet-sample", req, "sk-key")
				.treeSemtcs(tree)
				.page(0, 20);
		req.body(itm);
		itm.page(0, 27);

		OutputStream os = new ByteArrayOutputStream();
		JHelper.writeJsonReq(os, req);
		String json = os.toString();
		os.close();
		Utils.logi(json);
		assertTrue(json.startsWith("{\n"));

		InputStream in = new ByteArrayInputStream(json.getBytes());
		JMessage<DatasetReq> resp = jreqDs.readJson(in, DatasetReq.class);
		json = resp.toString();
		Utils.logi(json);
		assertTrue(json.startsWith("{\n"));

		assertEquals(Port.dataset, resp.port());
		assertEquals(req.t, resp.t);
		assertEquals("ss-id", resp.header().ssid());
		DatasetReq bd = resp.body(0);
		assertEquals("inet-sample", bd.conn()); // not printed by JBbody.toString()
		assertEquals("ds", bd.a()); // not printed by JBbody.toString()
		assertEquals(bd.sk, bd.sk);
		assertEquals("funcId", bd.stcs.dbRecId());
		assertEquals("parentId", bd.stcs.dbParent());
		assertEquals("parentId", bd.stcs.aliasParent());
	}

}
