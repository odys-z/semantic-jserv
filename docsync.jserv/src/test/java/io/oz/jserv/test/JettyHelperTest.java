package io.oz.jserv.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import static io.odysz.common.Utils.pause;

import java.io.IOException;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.HeartLink;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.IUser;
import io.oz.jserv.docs.syn.Doclientier;
import io.oz.jserv.docs.syn.Syntier;

/**
 * Start 3 jservs and ping the login.serv port.
 */
public class JettyHelperTest {
	public static final String clientUri = "/jnode";
	public static final String webinf    = "./src/test/res/WEB-INF";
	public static final String testDir   = "./src/test/res/";
	public static final String volumeDir = "./src/test/res/volume";

	static final String[] servs_conn = new String[] {
			"no-jserv.00", "no-jserv.01", "no-jserv.02", "no-jserv.03"};
	
	static final String clienturi = "/uri/test";
	static ErrorCtx errLog;	
	static int bsize = 12 * 64;

	final String servIP = "localhost";
	int port = 8964;

	static {
		errLog = new ErrorCtx() {
			@Override
			public void err(MsgCode code, String msg, String...args) {
				fail(msg);
			}
		};
	}

	@Test
	void test3jetties() throws Exception {
    	System.setProperty("VOLUME_HOME", "../volume");

		Configs.init(webinf);
		Connects.init(webinf);

		JettyHelper h1 = startJetty(servs_conn[0], "odyx", port++);
		JettyHelper h2 = startJetty(servs_conn[1], "odyy", port++);	
		JettyHelper h3 = startJetty(servs_conn[2], "odyz", port++);	
		
		Clients.init(h1.jserv);
		Doclientier client = new Doclientier("jetty-0", errLog)
				.tempRoot("odyx")
				.loginWithUri("jetty-0", "odyx", "test", "123456")
				.blockSize(bsize);
		assertNotNull(client);

		Clients.init(h2.jserv);
		client = new Doclientier("jetty-1", errLog)
				.tempRoot("odyy")
				.loginWithUri("jetty-1", "odyy", "test", "123456")
				.blockSize(bsize);
		assertNotNull(client);
				
		Clients.init(h3.jserv);
		client = new Doclientier("jetty-2", errLog)
				.tempRoot("odyz")
				.loginWithUri("jetty-2", "odyz", "test", "123456")
				.blockSize(bsize);
		assertNotNull(client);
		
		Utils.logi(h1.jserv + "/login.serv");
		Utils.logi(h2.jserv + "/login.serv");
		Utils.logi(h3.jserv + "/login.serv");
		pause("Errors because of no r.serv port can be ignred. Press any key to continue ...");
	}
	
	/**
	 * Start Jetty and allow uid to login.
	 * 
	 * @param conn
	 * @param uid
	 * @param port
	 * @return JettyHelper
	 * @throws IOException
	 * @throws Exception
	 */
	private JettyHelper startJetty(String conn, String uid, int port) throws IOException, Exception {
		IUser usr = DATranscxt.dummyUser();
		ArrayList<String> sqls = new ArrayList<String>();
		sqls.add("delete from a_users;");
		sqls.add(String.format("INSERT INTO "
				+ "a_users (userId, userName, roleId, orgId, counter, birthday, pswd,   iv)\n"
				+ "values  ('%s',  'Ody %s',  'r01',  'zsu', 0, '1989-06-04', '123456', null);",
				uid, port));

		Connects.commit(conn, usr, sqls, Connects.flag_nothing);

		return JettyHelper
			.startJettyServ(webinf, conn, "config-0.xml",
				servIP, port,
				new AnSession(),
				new HeartLink())
			.addServPort(new Syntier(Configs.getCfg(Configs.keys.synode), conn)
			.start("URA", "zsu", conn, SynodeMode.peer))
			;
	}
}
