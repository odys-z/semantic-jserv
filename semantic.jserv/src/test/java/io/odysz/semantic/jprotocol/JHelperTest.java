//package io.odysz.semantic.jprotocol;
//
//import java.io.ByteArrayInputStream;
//import java.io.IOException;
//
//import org.junit.Test;
//
//import io.odysz.common.Utils;
//import io.odysz.semantic.jserv.R.QueryReq;
//import io.odysz.semantics.x.SemanticException;
//
//public class JHelperTest {
//
//	@Test
//	public void testReadJson() throws SemanticException, IOException, ReflectiveOperationException {
//		JHelper<QueryReq> jhelperReq = new JHelper<QueryReq>();
//		byte[] b = "{header:{}, body:[{}]}"
//				.getBytes();
//		ByteArrayInputStream in = new ByteArrayInputStream(b);
//		JMessage<QueryReq> msg = (JMessage<QueryReq>)jhelperReq.readJson(in, QueryReq.class);
//		in.close();
//		Utils.logi(msg.toStringEx());
//	}
//
//}
