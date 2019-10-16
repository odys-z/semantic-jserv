//package io.odysz.semantic.jserv.U;
//
//import static org.junit.Assert.*;
//
//import java.io.ByteArrayInputStream;
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//
//import org.junit.Test;
//
//import io.odysz.common.Utils;
//import io.odysz.semantic.jprotocol.JHeader;
//import io.odysz.semantic.jprotocol.JHelper;
//import io.odysz.semantic.jprotocol.JMessage;
//import io.odysz.semantic.jprotocol.JMessage.Port;
//import io.odysz.semantic.jprotocol.JProtocol.CRUD;
//import io.odysz.semantics.x.SemanticException;
//import io.odysz.transact.sql.Query;
//
//public class UpdateReqTest {
//
//	protected static JHelper<UpdateReq> jreqUpd = new JHelper<UpdateReq>();
//
//	@Test
//	public void testToFromI() throws SemanticException, IOException, ReflectiveOperationException {
//		Utils.printCaller(false);
//
//		JMessage<UpdateReq> msg = new JMessage<UpdateReq>(Port.update);
//		msg.t = "a_user";
//
//		JHeader header = new JHeader("ss-id", "uid");
//		JHeader.usrAct("func-id", "update", "a_user", "U");
//		msg.header(header);
//
//		UpdateReq itm = new UpdateReq(msg, "inet-sample", "a_user", "I")
//				.nv("userId", "admin - 1")
//				.nv("userName", "junit")
//				.attach("t1.png", "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH4wQJAxIXLe1hiwAAABl0RVh0Q29tbWVudABDcmVhdGVkIHdpdGggR0lNUFeBDhcAAAAWSURBVAjXY/wvI8PAyMjE8OcPw79/ABuIBTEWd1cXAAAAAElFTkSuQmCC");
//		msg.body(itm);
//
//		OutputStream os = new ByteArrayOutputStream();
//		JHelper.writeJsonReq(os, msg);
//		String json = os.toString();
//		os.close();
//		// Utils.logi(json);
//		assertTrue(json.startsWith("{\n"));
//
//		InputStream in = new ByteArrayInputStream(json.getBytes());
//		JMessage<UpdateReq> resp = jreqUpd.readJson(in, UpdateReq.class);
//		json = resp.toString();
//		// Utils.logi(json);
//		assertTrue(json.startsWith("{gson, \n"));
//
//		assertEquals(Port.update, resp.port());
//		assertEquals(msg.t, resp.t);
//		assertEquals("ss-id", resp.header().ssid());
//		UpdateReq bd = resp.body(0);
//		assertEquals("I", bd.a()); // not printed by JBbody.toString()
//		assertEquals("a_user", bd.mtabl);
//		assertEquals("userId", bd.nvs.get(0)[Query.Ix.nvn]);
//		assertEquals("t1.png", bd.attacheds.get(0)[0]);
//	}
//
//	@Test
//	public void testToFromPost() throws SemanticException, IOException, ReflectiveOperationException {
//		Utils.printCaller(false);
//
//		JMessage<UpdateReq> msg = new JMessage<UpdateReq>(Port.update);
//		msg.t = "a_user";
//
//		JHeader header = new JHeader("ss-id", "uid");
//		JHeader.usrAct("func-id", "update", "a_user", "U");
//		msg.header(header);
//
//		UpdateReq pst = new UpdateReq(msg, "inet-sample", "postabl", CRUD.U)
//				.nv("postName", "post - 1")
//				.where("=", "postId", "'AUTO'");
//		UpdateReq pst2 = new UpdateReq(msg, "inet-sample", "postabl2", CRUD.D)
//				.where("=", "postId", "'ABC'");
//		UpdateReq itm = new UpdateReq(msg, "inet-sample", "a_user", "U")
//				.nv("userId", "admin - 1")
//				.nv("userName", "junit")
//				.where("=", "R.roleId", "'amdin'")
//				.post(pst)
//				.post(pst2)
//				;
//		msg.body(itm);
//
//		OutputStream os = new ByteArrayOutputStream();
//		JHelper.writeJsonReq(os, msg);
//		String json = os.toString();
//		os.close();
//		// Utils.logi(json);
//		assertTrue(json.startsWith("{\n"));
//
//		InputStream in = new ByteArrayInputStream(json.getBytes());
//		JMessage<UpdateReq> resp = jreqUpd.readJson(in, UpdateReq.class);
//		json = resp.toString();
//		// Utils.logi(json);
//		assertTrue(json.startsWith("{gson, \n"));
//
//		UpdateReq bd = resp.body(0);
//		assertEquals("U", bd.a()); // not printed by JBbody.toString()
//		assertEquals("=", bd.where.get(0)[Query.Ix.predicateOper]);
//
//		bd = bd.postUpds.get(0);
//		assertEquals("postabl", bd.mtabl);
//	}
//}
