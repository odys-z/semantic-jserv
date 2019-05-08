//package io.odysz.jsample.cheap;
//
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
//import io.odysz.jsample.protocol.Samport;
//import io.odysz.semantic.jprotocol.JHeader;
//import io.odysz.semantic.jprotocol.JHelper;
//import io.odysz.semantic.jprotocol.JMessage;
//import io.odysz.semantics.x.SemanticException;
//import io.odysz.sworkflow.EnginDesign.Req;
//
//public class CheapReqTest {
//	protected static JHelper<CheapReq> jreqCheap = new JHelper<CheapReq>();
//
//	@Test
//	public void testToFrom() throws SemanticException, IOException, ReflectiveOperationException {
//		Utils.printCaller(false);
//		JMessage.understandPorts(Samport.cheapflow);
//
//		JMessage<CheapReq> req = new JMessage<CheapReq>(Samport.cheapflow);
//		// req.t = "a_user";
//		String wfId = "t01";
//
//		JHeader header = new JHeader("ss-id", "uid");
//		JHeader.usrAct("func-id", "query", "a_user", "R");
//		req.header(header);
//
//		CheapReq itm = CheapReq.format(null, Req.start, wfId)
//				.nodeDesc("Desc: bla")
//				.taskNv("remarks", "task bbb")
//				.childTabl("task_details")
//				.newChildInstRow().childInsert("remarks", "client detail - 00").childInsert("extra", "01")
//				.newChildInstRow().childInsert("remarks", "client detail - 01");
//		req.body(itm);
//
//		OutputStream os = new ByteArrayOutputStream();
//		JHelper.writeJsonReq(os, req);
//		String json = os.toString();
//		os.close();
//		Utils.logi(json);
//		assertTrue(json.startsWith("{\n"));
//
//		InputStream in = new ByteArrayInputStream(json.getBytes());
//		JMessage<CheapReq> resp = jreqCheap.readJson(in, CheapReq.class);
//		json = resp.toString();
//		Utils.logi(json);
//		assertTrue(json.startsWith("{\n"));
//
//		assertEquals(Samport.cheapflow, resp.port());
//		assertEquals(req.t, resp.t);
//		CheapReq bd = resp.body(0);
//		assertEquals(Req.start.name(), bd.a());
//		assertEquals(wfId, bd.wftype());
//		assertEquals(1, bd.taskNvs().size());
//	}
//
//	@Test
//	public void testRightReq() throws IOException, SemanticException, ReflectiveOperationException {
//		Utils.printCaller(false);
//		JMessage.understandPorts(Samport.cheapflow);
//
//		String wfid = "t01";
//		CheapReq req = new CheapReq(null)
//				.cmdsRight("n-01", "user-1", "task 1")
//				.wftype(wfid);
//
//		JHeader header = new JHeader("ss-id", "uid");
//		JHeader.usrAct("func-id", "query", "a_user", "R");
//		JMessage<CheapReq> jmsg = new JMessage<CheapReq>(Samport.cheapflow);
//		jmsg.header(header);
//		jmsg.body(req);
//
//		OutputStream os = new ByteArrayOutputStream();
//		JHelper.writeJsonReq(os, jmsg);
//		String json = os.toString();
//		os.close();
//
//		InputStream in = new ByteArrayInputStream(json.getBytes());
//		JMessage<CheapReq> resp = jreqCheap.readJson(in, CheapReq.class);
//		json = resp.toString();
//		Utils.logi(json);
//
//		CheapReq bd = resp.body(0);
//		assertEquals("n-01", bd.args()[0]);
//
//	}
//}
