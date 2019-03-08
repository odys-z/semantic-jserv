package io.odysz.semantic.jserv.R;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.junit.jupiter.api.Test;

import io.odysz.common.Utils;
import io.odysz.semantic.jprotocol.JHeader;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Query.Ix;

class QueryReqTest {

	protected static JHelper<QueryReq> jreqQuery = new JHelper<QueryReq>();

	@Test
	void testToFrom() throws SemanticException, IOException, ReflectiveOperationException {
		Utils.printCaller(false);

		JMessage<QueryReq> req = new JMessage<QueryReq>(Port.query);
		req.t = "a_user";

		JHeader header = new JHeader("ss-id", "uid");
		JHeader.usrAct("func-id", "query", "a_user", "R");
		req.header(header);

		SemanticObject ssinf = new SemanticObject();
		QueryReq itm = QueryReq.formatReq("inet-sample", req, ssinf, "a_user", "u")
				.expr("userId", null)
				.j("a_roles", "r", "r.roleId = u.roleId")
				.where("=", "r.roleId", "'amdin'")
				.orderby("r.roleId")
				.orderby("u.userId", false);
		req.body(itm);
		itm.page(0, 27);

		OutputStream os = new ByteArrayOutputStream();
		JHelper.writeJsonReq(os, req);
		String json = os.toString();
		os.close();
		Utils.logi(json);
		assertTrue(json.startsWith("{\n"));

		InputStream in = new ByteArrayInputStream(json.getBytes());
		JMessage<QueryReq> resp = jreqQuery.readJson(in, QueryReq.class);
		json = resp.toString();
		Utils.logi(json);
		assertTrue(json.startsWith("{\n"));

		assertEquals(Port.query, resp.port());
		assertEquals(req.t, resp.t);
		assertEquals("ss-id", resp.header().ssid());
		QueryReq bd = resp.body(0);
		assertEquals("inet-sample", bd.conn()); // not printed by JBbody.toString()
		assertEquals("r", bd.a()); // not printed by JBbody.toString()
		assertEquals(req.body(0).pgsize, bd.pgsize);
		assertEquals("a_user", bd.mtabl);
		assertEquals("u", bd.mAlias);
		assertEquals("userId", bd.exprs.get(0)[Ix.exprExpr]);
		assertEquals("a_roles", bd.joins.get(0)[Ix.joinTabl]);
		assertEquals("r", bd.joins.get(0)[Ix.joinAlias]);
		assertEquals("=", bd.where.get(0)[Ix.predicateOper]);
		assertEquals("r.roleId", bd.orders.get(0)[Ix.orderExpr]);
		assertEquals("asc", bd.orders.get(0)[Ix.orderAsc]);
		assertEquals("desc", bd.orders.get(1)[Ix.orderAsc]);
	}

}
