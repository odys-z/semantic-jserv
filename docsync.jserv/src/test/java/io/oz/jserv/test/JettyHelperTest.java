package io.oz.jserv.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.touchDir;
// import static io.odysz.common.Utils.pause;
import static io.odysz.common.Utils.logOut;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Scanner;

import org.eclipse.jetty.util_ody.RolloverFileOutputStream;
import org.junit.jupiter.api.Test;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.R.AnQuery;
import io.odysz.semantic.jserv.ServPort.PrintstreamProvider;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.HeartLink;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.IUser;
import io.odysz.semantic.jserv.echo.Echo;
import io.oz.jserv.docs.syn.Doclientier;
import io.oz.jserv.docs.syn.ExpDoctier;
import io.oz.jserv.docs.syn.SynoderTest;
import io.oz.jserv.docs.syn.jetty.SynotierJettyApp;

/**
 * Start 3 jservs and ping the login.serv port.
 */
public class JettyHelperTest {
	public class PrintStream1 extends PrintStream {
		String tag;

		public PrintStream1(RolloverFileOutputStream os, String tag) {
			super(os);
			this.tag = tag;
		}
	}

	public static final String clientUri = "/jnode";
	public static final String webinf    = "./src/test/res/WEB-INF";
	public static final String testDir   = "./src/test/res/";
	public static final String volumeDir = "./src/test/res/volume";

	static final String[] servs_conn = new String[] {
			"no-jserv.00", "no-jserv.01", "no-jserv.02", "no-jserv.03"};
	
	static final String clienturi = "/uri/test";
	static ErrorCtx errLog;	
	static int bsize = 12 * 64;

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
	void testAzertFile() throws FileNotFoundException {
		azertFile("src/test/res/lines.txt", 0, "8961");
		azertFile("src/test/res/lines.txt", 1, "8962");
		azertFile("src/test/res/lines.txt", 2, "8963");
		azertFile("src/test/res/lines.txt", 3, "8964");

		azertFile("src/test/res/lines.txt", -1, "8964");
		azertFile("src/test/res/lines.txt", -2, "8963");
		azertFile("src/test/res/lines.txt", -3, "8962");
		azertFile("src/test/res/lines.txt", -4, "8961");
	}

	@Test
	void test3jetties() throws Exception {
    	System.setProperty("VOLUME_HOME", "../volume");

		Configs.init(webinf);
		Connects.init(webinf);

		SynotierJettyApp h1 = startJetty(null, servs_conn[0], "odyx", port++);
		SynotierJettyApp h2 = startJetty(null, servs_conn[1], "odyy", port++);	

		boolean[] lights = new boolean[] {false};
		touchDir("jetty-log");
        RolloverFileOutputStream os = new RolloverFileOutputStream("jetty-log/yyyy_mm_dd.out", true);
        RolloverFileOutputStream es = new RolloverFileOutputStream("jetty-log/yyyy_mm_dd.err", true);
        String outfile = os.getDatedFilename();

		SynotierJettyApp h3 = startJetty(lights, servs_conn[2], "odyz", port++,
							() -> { return new PrintStream1(os, "3-out"); }, 
							() -> { return new PrintStream1(es, "3-err"); });
		
		Clients.init(h1.jserv());
		Doclientier client = new Doclientier("jetty-0", errLog)
				.tempRoot("temp/odyx")
				.loginWithUri("jetty-0", "odyx", "test", "123456")
				.blockSize(bsize);
		assertNotNull(client);

		Clients.init(h2.jserv());
		client = new Doclientier("jetty-1", errLog)
				.tempRoot("temp/odyy")
				.loginWithUri("jetty-1", "odyy", "test", "123456")
				.blockSize(bsize);
		assertNotNull(client);
				
		Clients.init(h3.jserv());
		client = new Doclientier("jetty-2", errLog)
				.tempRoot("temp/odyz")
				.loginWithUri("jetty-2", "odyz", "test", "123456")
				.blockSize(bsize);
		assertNotNull(client);
		
		Utils.logi(h1.jserv() + "/login.serv");
		Utils.logi(h2.jserv() + "/login.serv");
		Utils.logi(h3.jserv() + "/login.serv");

		Clients.pingLess("jetty-2", errLog);
		
		os.flush(); es.flush();

		logOut(System.out);
		// pause("Errors because of no r.serv port can be ignred. Press any key to continue ...");
		awaitAll(lights);
		os.close(); es.close();
		
		azertFile(outfile, -1, "Echo: 127.0.0.1 : jetty-2");
	}
	
	/**
	 * Assert n-th line of file fn equals to str.
	 * @param fn
	 * @param lindex n-th, start from 0, -1 for last line
	 * @param str
	 * @throws FileNotFoundException
	 */
	static void azertFile(String fn, int lindex, String str)
			throws FileNotFoundException {
		File f = new File(fn);
		Scanner freader = new Scanner(f);

		if (lindex < 0) {
			ArrayList<String> linebuf = new ArrayList<String> (-lindex);

			while (freader.hasNextLine()) {
				String ln = freader.nextLine();
				linebuf.add(ln);
				if (linebuf.size() > -lindex)
					linebuf.remove(0);
			}
			assertEquals(linebuf.get(0), str);
		}

		else {
			while (lindex >= 0 && freader.hasNextLine()) {
				String data = freader.nextLine();
				if (lindex == 0) {
					assertEquals(str, data);
					break;
				}
				else lindex--;
			}
		}
	   freader.close();
	}

	/**
	 * Start Jetty and allow uid to login.
	 * @param echolights green lights for {@link Echo}'s call-backing signals, paused on by awaitAll().
	 * @param conn
	 * @param uid
	 * @param port
	 * @return JettyHelper
	 * @throws IOException
	 * @throws Exception
	 */
	private SynotierJettyApp startJetty(boolean[] echolights, String conn, String uid, int port, PrintstreamProvider ... oe) throws IOException, Exception {
		IUser usr = DATranscxt.dummyUser();
		ArrayList<String> sqls = new ArrayList<String>();
		sqls.add("drop table if exists a_users;");
		sqls.add("drop table if exists a_orgs;");
		sqls.add("drop table if exists a_roles;");
		sqls.add(Utils.loadTxt(SynoderTest.class, "a_users.sqlite.ddl"));
		sqls.add(Utils.loadTxt(SynoderTest.class, "a_orgs.sqlite.ddl"));
		sqls.add(Utils.loadTxt(SynoderTest.class, "a_roles.sqlite.ddl"));

		sqls.add("delete from a_users;");
		sqls.add(String.format("INSERT INTO "
				+ "a_users (userId, userName, roleId, orgId, counter, birthday, pswd,   iv)\n"
				+ "values  ('%s',  'Ody %s',  'r01',  'zsu', 0, '1989-06-04', '123456', null);",
				uid, port));

		Connects.commit(conn, usr, sqls, Connects.flag_nothing);

		return SynotierJettyApp
			.registerPorts(webinf, conn, "config-0.xml",
				"127.0.0.1", port,
				new AnSession(), new AnQuery(), new HeartLink(),
				new Echo(true).setCallbacks(() -> { if (echolights != null) echolights[0] = true; }))
			.addServPort(new ExpDoctier(Configs.getCfg(Configs.keys.synode), conn)
			.start("URA", "zsu", SynodeMode.peer))
			.start(oe)
			;
	}
}
