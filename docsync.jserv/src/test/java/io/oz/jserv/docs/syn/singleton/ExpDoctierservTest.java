package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.pause;
import static io.odysz.common.Utils.turngreen;
import static io.odysz.common.Utils.logrst;
import static io.oz.jserv.docs.syn.Dev.X_0;
import static io.oz.jserv.docs.syn.Dev.devs;
import static io.oz.jserv.docs.syn.Dev.docm;
import static io.oz.jserv.docs.syn.Dev.devm;
import static io.oz.jserv.docs.syn.singleton.SynodetierJoinTest.azert;
import static io.oz.jserv.docs.syn.singleton.SynodetierJoinTest.errLog;
import static io.oz.jserv.docs.syn.singleton.SynodetierJoinTest.jetties;
import static io.oz.jserv.docs.syn.singleton.SynodetierJoinTest.setVolumeEnv;
import static io.oz.jserv.docs.syn.singleton.SynotierJettyApp.webinf;
import static io.oz.jserv.docs.syn.singleton.SynotierJettyApp.zsu;
import static io.oz.syn.Docheck.printChangeLines;
import static io.oz.syn.Docheck.printNyquv;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import io.odysz.anson.JsonOpt;
import io.odysz.common.EnvPath;
import io.odysz.common.FilenameUtils;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.syn.Doclientier;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jprotocol.JServUrl;
import io.odysz.semantic.meta.DocRef;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.PathsPage;
import io.odysz.semantic.tier.docs.ShareFlag;
import io.odysz.semantics.IUser;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.Dev;
import io.oz.syn.Docheck;
import io.oz.syn.Synode;
import io.oz.syn.SynodeMode;
import io.oz.syn.registry.SynodeConfig;
import io.oz.syn.registry.YellowPages;

/**
 * 
 * Run SynodetierJoinTest for creating table a_orgs.
 * 
 * -Dsyndocs.ip="host-ip"
 * 
 * <p>When paused, run {@link io.oz.jserv.docs.syn.DoclientierTest}, press return</p>
 * 
 * @author ody
 * 
 * @disabled
 */
public class ExpDoctierservTest {
	/** Sarting port for each Jetty service to bind, _8964 + 1, +2, ... */
	public final static int _8964 = 9966;
	
	public final static int X = 0;
	public final static int Y = 1;
	public final static int Z = 2;
	public final static int W = 3;
	
	public static final int case_yresolve = 0;
	public static final int case_xy_resolve = 1;

	static final String[] servs_conn  = new String[] {
			"no-jserv.00", "no-jserv.01", "no-jserv.02", "no-jserv.03"};

	static final String[] config_xmls = new String[] {
			"config-0.xml", "config-1.xml", "config-2.xml", "config-3.xml"};
	
	public static Docheck[] ck;

	/** -Dsyndocs.ip="host-ip" */
	@BeforeAll
	public static void init() throws Exception {
		setVolumeEnv("vol-");
		JProtocol.setup(SynodetierJoinTest.servpath, Port.echo);

		ck = new Docheck[servs_conn.length];
	}
	
	/**
	 * Use -Dwait-clients for waiting client's pushing,
	 * by running {@link DoclientierTest#testSynclientUp()}.
	 * @throws Exception
	 */
	@SuppressWarnings("deprecation")
	@Test
	void runDoctiers() throws Exception {
		if (System.getProperty("wait-clients") == null) {

			Utils.logrst("Starting synode-tiers", 0);
			Utils.logrst("wait-clients = null.", 0, 0);

			int[] nodex = startJetties(jetties, ck);
			runDoctiers(0, nodex, null, null, null);

			Utils.warnT(new Object() {}, "Test is running in automatic style, quite without waiting clients' pushing!");
		}
		else {
			Utils.logrst("Starting synode-tiers", 0);
			Utils.logrst(f("wait-clients = %s.", System.getProperty("wait-client")), 0, 0);

			int[] nodex = startJetties(jetties, ck);

			final boolean[] noAutoQuit = new boolean[] { true };
			final boolean[] canPush = new boolean[] { false };
			final boolean[] pushDone = new boolean[] { false };

			runDoctiers(0, nodex, noAutoQuit, canPush, pushDone);

			pause("Now can run DoclienterTest. Press Enter once manully uploaded");
			awaitAll(canPush);

			turngreen(pushDone);
			pause("This thread will be killed asap when main thread quite.");
		}
	}

	/**
	 * 
	 * @param nodex
	 * @param waitClients [in] wait for client's pushing, otherwise quit immediately after boot.
	 * @param canPush [out] wait my checking
	 * @param pushDone [in] pushes are finished as expected (1 to X, 2 to Y)
	 * @throws Exception
	 */
	@SuppressWarnings("deprecation")
	public static void runDoctiers(int caseid, int[] nodex, boolean[] waitClients,
			boolean[] canPush, boolean[] pushDone) throws Exception {

		int section = 0;
		
		logrst("Open domains", ++section);

		final boolean[] lights = new boolean[nodex.length];
		for (int i : nodex) {

			SynodetierJoinTest.cleanDomain(jetties[i].syngleton.syncfg);

			cleanPhotos(docm, jetties[i].syngleton().domanager(zsu).synconn);

			assertTrue(JServUrl.valid(jetties[i].jserv()));
			fixJservs(ck, jetties[i]);
			jetties[i].syngleton().asyOpenDomains(
				(domain, mynid, peer, xp) -> {
					lights[i] = true;
				});
			
		}

		awaitAll(lights, -1);

		// This won't pass if h_photos is not cleared
		// And sometimes error if starting new test case while another case's threads are still working?
		ck[Z].doc(0);
		ck[Y].doc(0);
		ck[X].doc(0);

		logrst("Pause for client's pushing", ++section);
		printChangeLines(ck);
		printNyquv(ck);
		
		for (SynotierJettyApp j : jetties)
			if (j != null) j.print();

		if (waitClients == null) return;
		// else wait on lights (turn on by clients or users)

		turngreen(canPush); // Tell clients can push now
		logrst("Told clients can push now, waiting...", ++section);
		
		awaitAll(pushDone, -1);

		printChangeLines(ck);
		printNyquv(ck);

		// NOte
		// 1. There can be syn-workers, don't break clients
		// 2. Only works without synworks
		ck[X].doc(1);
		ck[Y].doc(2);
		ck[Z].doc(0);

		logrst("Synchronizing between synodes", ++section);
		SynodetierJoinTest.syncdomain(Y, ck);

		printChangeLines(ck);
		printNyquv(ck);

		ck[Y].doc(3);
		ck[X].doc(3);
		
		assert_Arefs_atB(X, Y, 1, 2);
		assert_Arefs_atB(Y, X, 2, 1);

		logrst("Bring up dev-x0 and delete", ++section);
		// 00 delete
		Clients.init(jetties[X].jserv());

		Dev devx0 = devs[X_0];
		logrst(new String[] {"Deleting", devx0.res}, section, 1);

		devx0.login(jetties[X].jserv(), errLog);
		DocsResp rep = devx0.client.synDel(docm.tbl, devx0.device.id, devx0.res);
		assertEquals(1, rep.total(0));

		ExpSyncDoc dx0 = (ExpSyncDoc) new ExpSyncDoc()
					.share(devx0.uid, ShareFlag.publish.name(), new Date())
					.folder(devx0.device.tofolder)
					.device(devx0.device.id)
					.fullpath(devx0.res);

		logrst(new String[] {"Verifying", devx0.res}, section, 2);
		verifyPathsPageNegative(devx0.client, docm.tbl, dx0.clientpath);

		logrst("Synchronizing synodes", ++section);
		printChangeLines(ck);
		printNyquv(ck);

		ck[Y].doc(3);
		ck[X].doc(2);

		SynodetierJoinTest.syncdomain(Y, ck);
		

		for (DocRef dr : assert_Arefs_atB(Y, X, 2, 0))
			assertEquals(ck[Y].synode(), dr.synoder);
		
		if (caseid == 1)
			SynodetierJoinTest.syncdomain(X, ck);

		logrst("Session can lost if break too long for debug. There must be something like:\n"
				+ "Sesssion refeshed. Session(s) idled (expired) in last 3 minutes:\n"
				+ "[+rZNei2j, ura : Y : ody]\n"
				+ "[+3jTpGK6, ura : Z : ody]", section, 1);
		if (caseid == 0)
			case_yresolve(section);
		else if (caseid == 1)
			case_xy_resolve(section);

		ck[Z].doc(2);
		ck[Y].doc(2);
		ck[X].doc(2);
	}

	static void fixJservs(Docheck[] cks, SynotierJettyApp synotierApp) throws TransException, SQLException {
		for (Docheck ck : cks) {
			SynodeMeta synm = ck.synb.syndomx.synm;
			ck.synb.update(synm.tbl, ck.synb.synrobot())
				.nv(synm.jserv, synotierApp.jserv())
				.whereEq(synm.pk, synotierApp.syngleton.synode())
				.u(ck.synb.instancontxt());
		}
	}


	@SuppressWarnings("deprecation")
	static void case_xy_resolve(int section) throws Exception {
		logrst("[branch x-y] Create DocRef streaming thread at Y...", ++section);
		Thread yresolve = SynodetierJoinTest
				.jetties[Y].syngleton.domanager(zsu)
				.synssion(ck[X].synode())
				.createResolver();

		logrst("Create DocRef streaming thread at X...", ++section);
		Thread xresolve = SynodetierJoinTest
				.jetties[X].syngleton.domanager(zsu)
				.synssion(ck[Y].synode())
				.createResolver();

		// Now y doesn't keep any docref as it is deleted. But this branch should work.
		logrst("Start DocRef streaming thread at Y...", ++section);
		yresolve.start();

		logrst("Start DocRef streaming thread at X...", ++section);
		xresolve.start();

		logrst("Waiting DocRef streaming thread at Y & X", ++section);
		yresolve.join();
		xresolve.join();

		logrst("Resolving docrefs finished.", ++section);
		printChangeLines(ck);
		printNyquv(ck);

		assert_Arefs_atB(X, Y, 0, 0);
		assert_Arefs_atB(Y, X, 0, 0);
	}
	
	@SuppressWarnings("deprecation")
	static void case_yresolve(int section) throws Exception {
		logrst("[branch X <- Y] Create DocRef streaming thread at Y...", ++section);
		Thread yresolve = SynodetierJoinTest
				.jetties[Y].syngleton.domanager(zsu)
				.synssion(ck[X].synode())
				.createResolver();

		// Now y doesn't keep any docref as it is deleted. But this branch should work.
		logrst("Start DocRef streaming thread at Y...", ++section);
		yresolve.start();


		yresolve.join();

		logrst("Resolving docrefs finished.", ++section);
		printChangeLines(ck);
		printNyquv(ck);

		assert_Arefs_atB(X, Y, 0, 0);
		assert_Arefs_atB(Y, X, 2, 0);

		yresolve = SynodetierJoinTest
				.jetties[Y].syngleton.domanager(zsu)
				.synssion(ck[X].synode())
				.pushResolve();
		logrst("Start DocRef pushing thread at Y...", ++section);
		yresolve.start();
		yresolve.join();

		logrst("Resolving docrefs finished.", ++section);
		printChangeLines(ck);
		printNyquv(ck);

		assert_Arefs_atB(X, Y, 0, 0);
		assert_Arefs_atB(Y, X, 0, 0);
	}
	
	/**
	 * Assert X-docs are synchronized to Y, as DocRefs.
	 * @param a x
	 * @param b y
	 * @param yrefs2xdoc x docs at y as refs
	 * @param xrefs2ydoc y docs at x as refs
	 * @return doc-refs at b to docs at a
	 * @throws SQLException
	 * @throws TransException
	 */
	static ArrayList<DocRef> assert_Arefs_atB(int a, int b, int yrefs2xdoc, int xrefs2ydoc)
			throws SQLException, TransException {
		List<DocRef> refs_atY = ck[b]
				.docRef()
				.stream()
				.filter(v -> v != null).toList();
		assertEquals(yrefs2xdoc, len(refs_atY));

		int xatys = 0;
		ArrayList<DocRef> xdlst = new ArrayList<DocRef>(yrefs2xdoc);

		for (DocRef xdref : refs_atY) {
			if (xdref == null) continue;

			assertEquals(ck[a].synb.syndomx.synode, xdref.synoder);
			assertTrue(xdref.uids.startsWith(ck[a].synb.syndomx.synode + ","));
			assertTrue(xdref.uri64.startsWith("$VOL"));

			xatys++;
			xdlst.add(xdref);
		}
		assertEquals(yrefs2xdoc, xatys);
		return xdlst;
	}

	/**
	 * Debug Notes:
	 * A slow machine will pollute the settings variables if not buffered
	 * This test cannot work on slow machine?
	 */
	public static int[] startJetties(SynotierJettyApp[] jetties, Docheck[] ck) throws Exception {
		int[] nodex = new int[] { X, Y, Z, W };
		
		SynodeConfig[] cfgs = new SynodeConfig[nodex.length]; 

		Connects.init(webinf);
		
		String[] nodes = new String[] { "X", "Y", "Z", "W" };
		
		HashMap<String, String> jservs = new HashMap<String, String>(4);
		for (int i : nodex) {
			jservs.put(nodes[i], f("http://127.0.0.1:%s/jserv-album", _8964 + i));
		}

		AppSettings[] settings = new AppSettings[nodex.length];

		for (int i : nodex) {
			if (jetties[i] != null)
				jetties[i].stop();
			
			String cfgxml = f("config-%s.xml", i);
			
			// install
			AppSettings _settings = new AppSettings();
			_settings.jservs(jservs);
			_settings.centralPswd = "8964";
			_settings.vol_name = f("VOLUME_%s", i);
			_settings.volume = f("../vol-%s", i);
			_settings.port = _8964 + i;
			
			// If there is no registry central service, these two lines should work
			// _settings.installkey = null;
			// _settings.rootkey = "0123456789ABCDEF";	

			_settings.installkey = "0123456789ABCDEF";	
			_settings.rootkey = null;

			String settings_json = f("settings.gitignore.%s", i);
			_settings.toFile(FilenameUtils.concat(webinf, settings_json), JsonOpt.beautify());

			YellowPages.load(EnvPath.concat(webinf, "$" + _settings.vol_name));
			cfgs[i] = YellowPages.synconfig();
			
			// won't work otherwise if the jserv-worker is disabled
			for (Synode p : cfgs[i].peers) {
				p.jserv = jservs.get(p.synid);
			}

			_settings.setupdb(cfgs[i], "jserv-stub", webinf,
					 cfgxml, "ABCDEF0123465789", true);

			cleanPhotos(docm, cfgs[i].synconn);
		
			// clean and reboot
			SynodetierJoinTest.cleanDomain(cfgs[i]);

			// main()
			settings[i] = AppSettings.checkInstall(SynotierJettyApp.servpath,
					webinf, cfgxml, settings_json, true);

			Utils.logi("+======================================= %s, %s",
					settings[i].reversedPort(false), i);

			// Notes for debug tests: sleep longer if binding failed
			jetties[i] = SynotierJettyApp.boot(webinf, cfgxml, settings[i], false)
						.afterboot()
						.print("\n. . . . . . . . Synodtier Jetty Application (Test) is running . . . . . . . ");
			
			// ISSUE afterboot() will write the same settings.json again, in another thread. 
			// Using different json files for the test?
			// Thread.sleep(10000);
			
			// checker
			ck[i] = new Docheck(azert, zsu, servs_conn[i],
								jetties[i].syngleton().domanager(zsu).synode,
								SynodeMode.peer, cfgs[i].chsize, docm, devm, true);
			
			Utils.logi("%s: %s - %s", i, settings[i].port, _settings.port);
		}
		
		return nodex;
	}

	static void cleanPhotos(ExpDocTableMeta docm, String conn, Dev[] devs) throws Exception {
		ArrayList<String> sqls = new ArrayList<String>();
		IUser usr = DATranscxt.dummyUser();
		for (Dev d : devs)
			sqls.add(f("delete from %s where %s = '%s'", docm.tbl, docm.device, d.device.id));
		Connects.commit(conn, usr, sqls);
	}
	
	static void cleanPhotos(ExpDocTableMeta docm, String conn) throws Exception {
		ArrayList<String> sqls = new ArrayList<String>();
		IUser usr = DATranscxt.dummyUser();
		sqls.add(f("delete from %s", docm.tbl));
		Connects.commit(conn, usr, sqls);
	}
		
	/**
	 * Verify device &amp; client-paths doesn't present at server.
	 * 
	 * @param clientier
	 * @param entityName
	 * @param paths
	 * @throws Exception
	 */
	private static void verifyPathsPageNegative(Doclientier clientier, String entityName,
			String... paths) throws Exception {
		PathsPage pths = new PathsPage(clientier.client.ssInfo().device, 0, 1);
		HashSet<String> pathpool = new HashSet<String>();
		for (String pth : paths) {
			pths.add(pth);
			pathpool.add(pth);
		}

		DocsResp rep = clientier.synQueryPathsPage(pths, Port.docstier);

		PathsPage pthpage = rep.pathsPage();
		assertEquals(clientier.client.ssInfo().device, pthpage.device);
		assertEquals(0, pthpage.paths().size());

		for (String pth : pthpage.paths().keySet())
			pathpool.remove(pth);

		assertEquals(isNull(paths) ? 0 : paths.length, pathpool.size());
	}

	public static String[] jservs() throws SQLException, TransException, SAXException, IOException {
		if (len(jetties) < 4 || jetties[0] == null)
			throw new NullPointerException("Initialize first.");

		String[] jrvs = new String[jetties.length];

		for (int i = 0; i < jetties.length; i++) 
			jrvs[i] = jetties[i].jserv();

		return jrvs;
	}

}
