//package io.odysz.semantic.jprotocol;
//
//import static org.junit.Assert.assertEquals;
//
//import org.junit.Test;
//
//import io.odysz.semantic.ext.DatasetReq;
//import io.odysz.semantic.jprotocol.JMessage.Port;
//
//public class JMessageTest {
//
//	@Test
//	public void testToStringEx() {
//		DatasetReq req = new DatasetReq(null, "test-conn");
//
//		String t = "tag";
//		JHeader header = new JHeader("ssid-junit", "uid-junit");
//		String[] act = JHeader.usrAct("junit-test", "test JMessage.toStringEx()", t, "cmd-junit");
//
//		JMessage<?> jmsg = new JMessage<DatasetReq>(Port.echo);
//		jmsg.t = t;
//		
//		header.act(act);
//		jmsg.header(header);
//		jmsg.body(req);
//
//		jmsg.header(header);
//
//		// Utils.logi(jmsg.toStringEx());
//		assertEquals("test-conn", jmsg.body(0).conn);
//	}
//
//}
