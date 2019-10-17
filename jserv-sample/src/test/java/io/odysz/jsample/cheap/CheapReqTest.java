//package io.odysz.jsample.cheap;
//
//import static org.junit.Assert.assertEquals;
//import static org.junit.Assert.assertTrue;
//
//import java.io.ByteArrayInputStream;
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//
//import org.junit.Test;
//
//import io.odysz.anson.Anson;
//import io.odysz.anson.x.AnsonException;
//import io.odysz.common.Utils;
//import io.odysz.jsample.protocol.Samport;
//import io.odysz.semantic.jprotocol.AnsonHeader;
//import io.odysz.semantic.jprotocol.AnsonMsg;
//import io.odysz.semantic.jprotocol.AnsonResp;
//import io.odysz.semantics.x.SemanticException;
//import io.odysz.sworkflow.EnginDesign.Req;
//
//public class CheapReqTest {
//
//	@Test
//	public void testToFrom() throws SemanticException, IOException, ReflectiveOperationException, AnsonException {
//		Utils.printCaller(false);
//		AnsonMsg.understandPorts(Samport.cheapflow);
//
//		AnsonMsg<CheapReq> req = new AnsonMsg<CheapReq>(Samport.cheapflow);
//		// req.t = "a_user";
//		String wfId = "t01";
//
//		AnsonHeader header = new AnsonHeader("ss-id", "uid");
//		AnsonHeader.usrAct("func-id", "query", "a_user", "R");
//		req.header(header);
//
//		CheapReq itm = CheapReq.format(null, Req.start, wfId)
//				.nodeDesc("Desc: bla")
//				.taskNv("remarks", "task bbb")
////				.childTabl("task_details")
////				.newChildInstRow().childInsert("remarks", "client detail - 00").childInsert("extra", "01")
////				.newChildInstRow().childInsert("remarks", "client detail - 01")
//				;
//		req.body(itm);
//
//		OutputStream os = new ByteArrayOutputStream();
//		// JHelper.writeJsonReq(os, req);
//		req.toBlock(os);
//		String json = os.toString();
//		os.close();
//		Utils.logi(json);
//		assertTrue(json.startsWith("{\n"));
//
//		InputStream in = new ByteArrayInputStream(json.getBytes());
//		@SuppressWarnings("unchecked")
//		AnsonMsg<AnsonResp> resp = (AnsonMsg<AnsonResp>) Anson.fromJson(in);
//		json = resp.toString();
//		Utils.logi(json);
//		assertTrue(json.startsWith("{gson, \n"));
//
//		assertEquals(Samport.cheapflow, resp.port());
////		assertEquals(req.body(0).a(), resp.a());
//		AnsonResp bd = resp.body(0);
//		assertEquals(Req.start.name(), bd.a());
//		// TODO how to test?
////		assertEquals(wfId, bd.wftype());
////		assertEquals(1, bd.taskNvs().size());
//	}
//
//	@Test
//	public void testRightReq() throws IOException, SemanticException, ReflectiveOperationException, AnsonException {
//		Utils.printCaller(false);
//		AnsonMsg.understandPorts(Samport.cheapflow);
//
//		String wfid = "t01";
//		CheapReq req = new CheapReq(null)
//				.cmdsRight("n-01", "user-1", "task 1")
//				.wftype(wfid);
//
//		AnsonHeader header = new AnsonHeader("ss-id", "uid");
//		AnsonHeader.usrAct("func-id", "query", "a_user", "R");
//		AnsonMsg<CheapReq> jmsg = new AnsonMsg<CheapReq>(Samport.cheapflow);
//		jmsg.header(header);
//		jmsg.body(req);
//
//		OutputStream os = new ByteArrayOutputStream();
//		// JHelper.writeJsonReq(os, jmsg);
//		jmsg.toBlock(os);
//		String json = os.toString();
//		os.close();
//
//		InputStream in = new ByteArrayInputStream(json.getBytes());
//		// AnsonMsg<CheapReq> resp = jreqCheap.readJson(in, CheapReq.class);
//		 @SuppressWarnings("unchecked")
//		AnsonMsg<CheapReq> resp = (AnsonMsg<CheapReq>) Anson.fromJson(in);
//		json = resp.toString();
//		Utils.logi(json);
//
//		CheapReq bd = resp.body(0);
//		assertEquals("n-01", bd.args("nodeId"));
//
//	}
//}
