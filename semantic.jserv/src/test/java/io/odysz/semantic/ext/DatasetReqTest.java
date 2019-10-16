package io.odysz.semantic.ext;


import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.Test;

import io.odysz.anson.Anson;
import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.semantic.DA.DatasetCfgV11.TreeSemantics;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantics.x.SemanticException;

public class DatasetReqTest {

	// protected static JHelper<DatasetReq> jreqDs = new JHelper<DatasetReq>();

	@Test
	public void testToFromJson() throws SemanticException, IOException, ReflectiveOperationException, AnsonException {
		Utils.printCaller(false);

		AnsonMsg<AnDatasetReq> req = new AnsonMsg<AnDatasetReq>(Port.dataset);
		req.body(0).a("");

		AnsonHeader header = new AnsonHeader("ss-id", "uid");
		AnsonHeader.usrAct("func-id", "query", "a_user", "R");
		req.header(header);

		TreeSemantics tree = new TreeSemantics(",a_functions,funcId id,parentId,funcName text,,,fals");
		AnDatasetReq itm = (AnDatasetReq) AnDatasetReq
				.formatReq("inet-sample", req, "sk-key")
				.treeSemtcs(tree)
				.page(0, 20);
		req.body(itm);
		itm.page(0, 27);

		OutputStream os = new ByteArrayOutputStream();
		req.toBlock(os);
		String json = os.toString();
		os.close();
		assertTrue(json.startsWith("{\n  \"port\": \"dataset\","));

		InputStream in = new ByteArrayInputStream(json.getBytes());
		@SuppressWarnings("unchecked")
		AnsonMsg<AnDatasetReq> resp = (AnsonMsg<AnDatasetReq>) Anson.fromJson(in);
		json = resp.toString();
		assertTrue(json.startsWith("{gson, \n"));

		assertEquals(Port.dataset, resp.port());
		assertEquals(req.body(0).a(), resp.body(0).a());
		assertEquals("ss-id", resp.header().ssid());
		AnDatasetReq bd = resp.body(0);
		assertEquals("inet-sample", bd.conn()); // not printed by JBbody.toString()
		assertEquals("ds", bd.a()); // not printed by JBbody.toString()
		assertEquals(bd.sk, bd.sk);
		assertEquals("funcId", bd.stcs.dbRecId());
		assertEquals("parentId", bd.stcs.dbParent());
		assertEquals("parentId", bd.stcs.aliasParent());
	}

}
