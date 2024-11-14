package io.odysz.semantic.jsession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import io.odysz.common.AESHelper;
import io.odysz.common.Configs;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.R.AnQueryReq;
import io.odysz.semantics.SessionInf;

class AnSessionTest {
	static {
		Configs.init("src/main/webapp/WEB-INF");
	}

	@Test
	void testAllocateSsid() {
		assertFalse(AnSession.allocateSsid().startsWith("00"));
		assertEquals(8, AnSession.allocateSsid().length());
	}

	@Test
	void testSessionToken() throws Exception {
		DATranscxt.key("user-pswd", "io.github.odys-z");
		String uid = "ody", pswd = "123456";

		// login
		JUser login = (JUser) AnSession
				.createUserByClassname("io.odysz.semantic.jsession.JUser", uid, pswd, null, "test")
				.touch();

		SessionInf ssinf = login.getClientSessionInf(login);
		AnSessionResp bd = new AnSessionResp(null, ssinf).profile(login.profile());
		
		// client
		bd.ssInf.ssToken = AESHelper.repackSessionToken(bd.ssInf.ssToken, pswd, uid);
		T_SessionClient client = new T_SessionClient(bd);

		// a session talk
		AnsonMsg<AnQueryReq> msg = client.query("uri", "a_users", "u", 0, -1, "/0/sys/a_usrs");

		AnsonHeader header = msg.header();
		
		try { AnSession.touchSessionToken(login, header.token(), login.sessionKey()); }
		catch (Exception e) { fail("Token verification failed."); }
	}
}
