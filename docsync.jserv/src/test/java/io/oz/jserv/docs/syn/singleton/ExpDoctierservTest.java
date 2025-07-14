package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.pause;
import static io.odysz.common.Utils.turngreen;
import static io.odysz.common.Utils.logrst;
import static io.odysz.semantic.syn.Docheck.printChangeLines;
import static io.odysz.semantic.syn.Docheck.printNyquv;
import static io.oz.jserv.docs.syn.Dev.X_0;
import static io.oz.jserv.docs.syn.Dev.devs;
import static io.oz.jserv.docs.syn.Dev.docm;
import static io.oz.jserv.docs.syn.Dev.devm;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.azert;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.errLog;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.jetties;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.setVolumeEnv;
import static io.oz.jserv.docs.syn.singleton.SynotierJettyApp.webinf;
import static io.oz.jserv.docs.syn.singleton.SynotierJettyApp.zsu;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.anson.JsonOpt;
import io.odysz.common.FilenameUtils;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.syn.Doclientier;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.meta.DocRef;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.syn.Docheck;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.PathsPage;
import io.odysz.semantic.tier.docs.ShareFlag;
import io.odysz.semantics.IUser;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.Dev;
import io.oz.jserv.docs.syn.SynodetierJoinTest;
import io.oz.syn.SynodeConfig;
import io.oz.syn.YellowPages;

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
	public final static int _8964 = 8966;
	
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

		ck = new Docheck[servs_conn.length];
	}
	
	/**
	 * Use -Dwait-clients for waiting client's pushing, by running {@link DoclientierTest#testSynclientUp()}.
	 * @throws Exception
	 */
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
	public static void runDoctiers(int caseid, int[] nodex,
			boolean[] waitClients, boolean[] canPush, boolean[] pushDone) throws Exception {

		int section = 0;
		
		logrst("Open domains", ++section);

		final boolean[] lights = new boolean[nodex.length];
		for (int i : nodex) {

			Syngleton.cleanDomain(jetties[i].syngleton.syncfg);

			cleanPhotos(docm, jetties[i].syngleton().domanager(zsu).synconn);

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
		Clients.init(jetties[X].myjserv());

		Dev devx0 = devs[X_0];
		logrst(new String[] {"Deleting", devx0.res}, section, 1);

		devx0.login(errLog);
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
		
		if (caseid == 0)
			case_yresolve(section);
		else if (caseid == 1)
			case_xy_resolve(section);

		ck[Z].doc(2);
		ck[Y].doc(2);
		ck[X].doc(2);
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
				.pushResove();
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

	@SuppressWarnings("deprecation")
	public static int[] startJetties(SynotierJettyApp[] jetties, Docheck[] ck) throws Exception {
		int[] nodex = new int[] { X, Y, Z, W };
		
		SynodeConfig[] cfgs = new SynodeConfig[nodex.length]; 

		Connects.init(webinf);
		
		String[] nodes = new String[] { "X", "Y", "Z", "W" };
		
		HashMap<String, String> jservs = new HashMap<String, String>(4);
		for (int i : nodex) {
			jservs.put(nodes[i], f("http://127.0.0.1:%s/jserv-album", _8964 + i));
		}

		for (int i : nodex) {
			if (jetties[i] != null)
				jetties[i].stop();
			
			String cfgxml = f("config-%s.xml", i);
			
			// install
			AppSettings settings = new AppSettings();
			settings.jservs = jservs;
			settings.vol_name = f("VOLUME_%s", i);
			settings.volume = f("../vol-%s", i);
			settings.port = _8964 + i;
			settings.installkey = "0123456789ABCDEF";	
			settings.rootkey = null;
			settings.toFile(FilenameUtils.concat(webinf, "settings.json"), JsonOpt.beautify());

			YellowPages.load("$" + settings.vol_name);
			cfgs[i] = YellowPages.synconfig();

			settings.setupdb(cfgs[i], "jserv-stub", webinf,
					 cfgxml, "ABCDEF0123465789", true);

			cleanPhotos(docm, cfgs[i].synconn);
		
			// clean and reboot
			Syngleton.cleanDomain(cfgs[i]);
			// Syngleton.cleanSynssions(cfgs[i]);

			// main()
			settings = AppSettings.checkInstall(SynotierJettyApp.servpath, webinf, cfgxml, "settings.json", true);

			jetties[i] = SynotierJettyApp.boot(webinf, cfgxml, "settings.json")
						.print("\n. . . . . . . . Synodtier Jetty Application is running . . . . . . . ");
			
			// checker
			ck[i] = new Docheck(azert, zsu, servs_conn[i],
								jetties[i].syngleton().domanager(zsu).synode,
								SynodeMode.peer, cfgs[i].chsize, docm, devm, true);
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

	@SuppressWarnings("deprecation")
	public static String[] jservs() {
		if (len(jetties) < 4 || jetties[0] == null)
			throw new NullPointerException("Initialize first.");

		String[] jrvs = new String[jetties.length];

		for (int i = 0; i < jetties.length; i++) 
			jrvs[i] = jetties[i].myjserv();

		return jrvs;
	}

}
