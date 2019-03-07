package io.odysz.semantic.jprotocol;

// import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.odysz.common.Utils;
import io.odysz.semantic.ext.DatasetReq;
import io.odysz.semantic.jprotocol.JMessage.Port;

class JMessageTest {

	@Test
	void testToStringEx() {
		DatasetReq req = new DatasetReq(null, "test-conn");

		String t = "tag";
		JHeader header = new JHeader("ssid-junit", "uid-junit");
		String[] act = JHeader.usrAct("junit-test", "test JMessage.toStringEx()", t, "cmd-junit");

		JMessage<?> jmsg = new JMessage<DatasetReq>(Port.echo);
		jmsg.t = t;
		
		header.act(act);
		jmsg.header(header);
		jmsg.body(req);

		jmsg.header(header);

		Utils.logi(jmsg.toStringEx());
	}

}
