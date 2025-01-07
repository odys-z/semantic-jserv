package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.logOut;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.touchDir;
import static io.odysz.semantic.syn.Docheck.printChangeLines;
import static io.odysz.semantic.syn.Docheck.printNyquv;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import org.eclipse.jetty.util_ody.RolloverFileOutputStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.common.Configs;
import io.odysz.common.IAssert;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.syn.Doclientier;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.ServPort.PrintstreamProvider;
import io.odysz.semantic.jserv.R.AnQuery;
import io.odysz.semantic.jserv.echo.Echo;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.HeartLink;
import io.odysz.semantic.syn.Docheck;
import io.odysz.semantic.syn.SyncUser;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.syn.registry.Syntities;
import io.odysz.semantics.x.SemanticException;
import io.oz.jserv.docs.AssertImpl;
import io.oz.jserv.docs.syn.DocUser;
import io.oz.jserv.docs.syn.T_PhotoMeta;
import io.oz.syn.SynodeConfig;
import io.oz.syn.YellowPages;

/**
 * Start 3 jservs, ping the login.serv port, and verify the print streams.
 * 
 * @since 0.2.0
 */
public class CreateSyndocTierTest {
	public class PrintStream1 extends PrintStream {
		String tag;

		public PrintStream1(RolloverFileOutputStream os, String tag) {
			super(os);
			this.tag = tag;
		}
	}
	public static String zsu = "zsu";
	public static String ura = "URA";

	public static final String syntity_json = "syntity.json";
	public static final String clientUri = "/jetty";
	public static final String webinf    = "./src/test/res/WEB-INF";
	public static final String testDir   = "./src/test/res/";
	public static final String volumeDir = "./src/test/res/volume";

	static final String[] servs_conn = new String[] {
			"no-jserv.00", "no-jserv.01", "no-jserv.02", "no-jserv.03"};
	
	static final String clienturi = "/uri/test";
	static ErrorCtx errLog;	
	static int bsize = 12 * 64;

	static IAssert azert;

	private static Docheck[] ck;

	/** -Dsyndocs.ip="host-ip" */
	@BeforeAll
	static void init() throws Exception {
		ck = new Docheck[servs_conn.length];
	}
	
	static {
		errLog = new ErrorCtx() {
			@Override
			public void err(MsgCode code, String msg, String...args) {
				fail(msg);
			}
		};
		
		azert = new AssertImpl();
	}
	
	@Test
	void test3jetties() throws Exception {
		setVolumeEnv("v-");

		Configs.init(webinf);
		Connects.init(webinf);

		SynotierJettyApp h1 = createStartSyndocTierTest(null, "X", "$VOLUME_0", "ABCDEF0123465789");
		T_PhotoMeta docm = new T_PhotoMeta(servs_conn[0]);
		ck[0] = new Docheck(azert, zsu, servs_conn[0],
					"X", SynodeMode.peer, docm, null, true);

		SynotierJettyApp h2 = createStartSyndocTierTest(null, "Y", "$VOLUME_1", "ABCDEF0123465789");	
		docm = new T_PhotoMeta(servs_conn[1]);
		ck[1] = new Docheck(azert, zsu, servs_conn[1],
					"Y", SynodeMode.peer, docm, null, true);

		boolean[] lights = new boolean[] {false};
		touchDir("jetty-log");
        RolloverFileOutputStream os = new RolloverFileOutputStream("jetty-log/yyyy_mm_dd.out", true);
        RolloverFileOutputStream es = new RolloverFileOutputStream("jetty-log/yyyy_mm_dd.err", true);
        String outfile = os.getDatedFilename();

		SynotierJettyApp h3 = createStartSyndocTierTest(lights, "Z", "$VOLUME_2", "ABCDEF0123465789", 
							() -> { return new PrintStream1(os, "3-out"); }, 
							() -> { return new PrintStream1(es, "3-err"); });
		
		docm = new T_PhotoMeta(servs_conn[2]);
		ck[2] = new Docheck(azert, zsu, servs_conn[2],
					"Z", SynodeMode.peer, docm, null, true);

		Clients.init(h1.jserv());
		Doclientier client = new Doclientier(docm.tbl, "/sys/X", "/syn/X", errLog)
				.tempRoot("temp/odyx")
				.loginWithUri("ody", "test", "8964")
				.blockSize(bsize);
		assertNotNull(client);

		Clients.init(h2.jserv());
		client = new Doclientier(docm.tbl, "/sys/Y", "/syn/Y", errLog)
				.tempRoot("temp/odyy")
				.loginWithUri("ody", "test", "8964")
				.blockSize(bsize);
		assertNotNull(client);
				
		Clients.init(h3.jserv());
		client = new Doclientier(docm.tbl, "/sys/Z", "/syn/Z", errLog)
				.tempRoot("temp/odyz")
				.loginWithUri("ody", "test", "8964")
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
		
		printChangeLines(ck);
		printNyquv(ck);
		
		azert.lineEq(outfile, -1, "Echo: [0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3} : jetty-2");
	}

	public static void setVolumeEnv(String vol_prefix) {
		String p = new File("src/test/res").getAbsolutePath();
    	System.setProperty("VOLUME_HOME", p + "/volume");
    	logi("VOLUME_HOME : %s", System.getProperty("VOLUME_HOME"));

		for (int c = 0; c < 4; c++) {
			System.setProperty(f("VOLUME_%s", c), f("%s/%s%s", p, vol_prefix, c));
			logi("VOLUME %s : %s\n", c, System.getProperty(f("VOLUME_%s", c)));
		}
	}

	/**
	 * Start Jetty and allow uid to login.
	 * @param greenlights green lights for {@link Echo}'s call-backing signals, paused on by awaitAll().
	 * @param conn
	 * @param port
	 * @return JettyHelper
	 * @throws IOException
	 * @throws Exception
	 */
	private SynotierJettyApp createStartSyndocTierTest(boolean[] greenlights, String synode, String envolume, 
			String rootkey, PrintstreamProvider ... oe) throws IOException, Exception {

		YellowPages.load(envolume);
		SynodeConfig cfg = YellowPages.synconfig();
		cfg.mode = SynodeMode.peer;
		
		Syntities regists = Syntities.load(webinf, syntity_json, 
				(synreg) -> {
					throw new SemanticException("TODO %s (configure an entity table with meta type)", synreg.table);
				});	
		
		AppSettings.setupdb(cfg, webinf, envolume, "config.xml", rootkey);

		SynotierJettyApp app = SynotierJettyApp
				.instanserver(webinf, cfg, "config.xml", "127.0.0.1", cfg.port);
		app.syngleton.loadomains(cfg, new DocUser(((ArrayList<SyncUser>) YellowPages.robots()).get(0)));

		return SynotierJettyApp
			.registerPorts(app, "/", cfg.sysconn,
				new AnSession(), new AnQuery(), new HeartLink(),
				new Echo(true).setCallbacks(() -> { if (greenlights != null) greenlights[0] = true; }))
			.addDocServPort(cfg, regists.syntities)
			.start(isNull(oe) ? () -> System.out : oe[0], !isNull(oe) && oe.length > 1 ? oe[1] : () -> System.err)
			;
	}
}
