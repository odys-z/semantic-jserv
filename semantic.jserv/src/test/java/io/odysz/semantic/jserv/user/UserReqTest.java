package io.odysz.semantic.jserv.user;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import org.junit.Test;

import io.odysz.common.Utils;
import io.odysz.semantic.jprotocol.JHeader;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;

public class UserReqTest {

	@SuppressWarnings("unchecked")
	@Test
	public void test() throws SemanticException, IOException, ReflectiveOperationException {
		Utils.printCaller(false);

		JMessage<UserReq> req = new JMessage<UserReq>(Port.user);

		JHeader header = new JHeader("ss-id", "uid");
		JHeader.usrAct("func-id", "user", "cate", "R");
		req.header(header);

		String a = "aaa";
		String tabl = "table111";
		String id0 = "abc ddd";

		ArrayList<String[]> items = new ArrayList<String[]>(1);
		items.add(new String[] {"0", "1"});

		SemanticObject obj = new SemanticObject().code("test");

		UserReq body0 = (UserReq) new UserReq(req, "conn-id")
				.a(a);

		body0.data("id", id0)
			.data("items", items)
			.data("obj", obj);

		body0.tabl = tabl;
		
		OutputStream os = new ByteArrayOutputStream();
		JHelper.writeJsonReq(os, req.body(body0));
		String json = os.toString();
		os.close();

		JHelper<UserReq> jreq = new JHelper<UserReq>();
		InputStream in = new ByteArrayInputStream(json.getBytes());
		JMessage<UserReq> resp = jreq.readJson(in, UserReq.class);
		
		UserReq body = resp.body(0);
		assertEquals(a, body.a());
		assertEquals(tabl, body.tabl());
		assertEquals(id0, body.data("id"));
		items = (ArrayList<String[]>) body.data("items");
		assertEquals("0", items.get(0)[0]);
		assertEquals("1", items.get(0)[1]);
		assertEquals("test", ((SemanticObject)body.get("obj")).code());
	}

}
