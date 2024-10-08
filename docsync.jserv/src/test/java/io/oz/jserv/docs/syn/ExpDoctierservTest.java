package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.pause;
import static io.odysz.common.Utils.waiting;
import static io.odysz.semantic.syn.Docheck.printChangeLines;
import static io.odysz.semantic.syn.Docheck.printNyquv;
import static io.oz.jserv.docs.syn.Dev.X_0;
import static io.oz.jserv.docs.syn.Dev.devs;
import static io.oz.jserv.docs.syn.Dev.docm;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.azert;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.errLog;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.jetties;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.startSyndoctier;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.zsu;
import static io.oz.jserv.test.JettyHelperTest.webinf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.meta.ExpDocTableMeta.Share;
import io.odysz.semantic.syn.Docheck;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.PathsPage;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.oz.jserv.docs.syn.singleton.Syngleton;
import io.oz.jserv.docs.syn.singleton.SynotierJettyApp;
import io.oz.syn.SynodeConfig;
import io.oz.syn.YellowPages;

/**
 * 
 * Run SynodetierJoinTest for creating table a_orgs.
 * 
 * -Dsyndocs.ip="host-ip"
 * 
 * @author ody
 */
public class ExpDoctierservTest {
	public final static int X = 0;
	public final static int Y = 1;
	public final static int Z = 2;
	public final static int W = 3;

	static final String[] servs_conn  = new String[] {
			"no-jserv.00", "no-jserv.01", "no-jserv.02", "no-jserv.03"};

	static final String[] config_xmls = new String[] {
			"config-0.xml", "config-1.xml", "config-2.xml", "config-3.xml"};
	
	private static Docheck[] ck;

	/** -Dsyndocs.ip="host-ip" */
	@BeforeAll
	static void init() throws Exception {
		Configs.init(webinf);
		ck = new Docheck[servs_conn.length];
	}
	
	@Test
	void runDoctiers() throws Exception {
		int section = 0;
		
		Utils.logrst("Starting synode-tiers", ++section);
		int[] nodex = runtimeEnv(jetties, ck);

		Utils.logrst("Open domains", ++section);

		final boolean[] lights = new boolean[nodex.length];
		for (int i : nodex) {
			// should block X's starting sessions
			jetties[i].openDomains( (domain, mynid, peer, repb, xp) -> {
				lights[i] = true;
			});
		}
		awaitAll(lights, -1);

		Utils.logrst("Pause for client's pushing", ++section);
		pause("Press Enter after pushed with client for starting synchronizing.");

		printChangeLines(ck);
		printNyquv(ck);

		Utils.logrst("Synchronizing between synodes", ++section);
		waiting(lights, Y);
		SynodetierJoinTest.syncdomain(lights, Y, ck);
		awaitAll(lights, -1);

		printChangeLines(ck);
		printNyquv(ck);

		ck[Y].doc(3);
		ck[X].doc(3);

		Utils.logrst("Bring up dev-x0 and delete", ++section);
		// 00 delete
		Clients.init(jetties[X].jserv());

		Dev devx0 = devs[X_0];
		Utils.logrst(new String[] {"Deleting", devx0.res}, section, 1);

		devx0.login(errLog);
		DocsResp rep = devx0.client.synDel(docm.tbl, devx0.dev, devx0.res);
		assertEquals(1, rep.total(0));

		ExpSyncDoc dx0 = (ExpSyncDoc) new ExpSyncDoc()
					.share(devx0.uid, Share.pub, new Date())
					.folder(devx0.folder)
					.device(devx0.dev)
					.fullpath(devx0.res);

		Utils.logrst(new String[] {"Verifying", devx0.res}, section, 2);
		verifyPathsPageNegative(devx0.client, docm.tbl, dx0.clientpath);

		Utils.logrst("Synchronizing synodes", ++section);
		waiting(lights, Y);
		SynodetierJoinTest.syncdomain(lights, Y);
		awaitAll(lights, -1);

		Utils.logrst("Finish", ++section);
		printChangeLines(ck);
		printNyquv(ck);

		ck[Y].doc(2);
		ck[X].doc(2);
	}

	private static int[] runtimeEnv(SynotierJettyApp[] jetties, Docheck[] ck) throws Exception {
		int[] nodex = new int[] { X, Y, Z };
		String host = System.getProperty("syndocs.ip");
		int port = 8090;
		
		SynodeConfig[] cfgs = new SynodeConfig[nodex.length]; 

		for (int i : nodex) {
			if (jetties[i] != null)
				jetties[i].stop();
			

			String p = new File("src/test/res").getAbsolutePath();
			System.setProperty("VOLUME_HOME", p + "/vol-" + i);
			logi("VOLUME_HOME : %s\n", System.getProperty("VOLUME_HOME"));

			Connects.init(webinf);

			YellowPages.load("$VOLUME_HOME");

			cfgs[i] = YellowPages.synconfig();
			cfgs[i].host = host;
			cfgs[i].port = port++;

			Syngleton.setupSysRecords(cfgs[i], YellowPages.robots());
			
			// Syngleton.initSynconn(cfgs[i], webinf, f("config-%s.xml", i), p, host);
			// Syngleton.setupSyntables(cfgs[i].synconn);
			// Syngleton.initSynodeRecs(cfgs[i], cfgs[i].peers());
			Syngleton.setupSyntables(cfgs[i],
					cfgs[i].syntityMeta((cfg, synreg) -> {
						if (eq(synreg.name, "T_PhotoMeta"))
							return new T_PhotoMeta(cfg.synconn);
						else
							throw new SemanticException("TODO %s", synreg.name);
					}),
					webinf, f("config-%s.xml", i), ".", "ABCDEF0123465789");
			
			cleanPhotos(docm, servs_conn[i], devs[i].dev);
			
			jetties[i] = startSyndoctier(cfgs[i]);
			
			ck[i] = new Docheck(azert, zsu, servs_conn[i],
						jetties[i].synode(), SynodeMode.peer, docm);
		}
		
		for (int i : nodex) {
			jetties[i].updateJservs(ck[i].trb.synm, cfgs[i], zsu);
		}

		return nodex;
	}

	static void cleanPhotos(ExpDocTableMeta docm, String conn, String ofDevice) throws Exception {
		ArrayList<String> sqls = new ArrayList<String>();
		IUser usr = DATranscxt.dummyUser();
		sqls.add(f("delete from %s where %s = '%s'", docm.tbl, docm.device, ofDevice));
		Connects.commit(conn, usr, sqls);
	}
	
	/**
	 * Verify device &amp; client-paths isn't presenting at server.
	 * 
	 * @param clientier
	 * @param entityName
	 * @param paths
	 * @throws Exception
	 */
	static void verifyPathsPageNegative(Doclientier clientier, String entityName,
			String... paths) throws Exception {
		PathsPage pths = new PathsPage(clientier.client.ssInfo().device, 0, 1);
		HashSet<String> pathpool = new HashSet<String>();
		for (String pth : paths) {
			pths.add(pth);
			pathpool.add(pth);
		}

		DocsResp rep = clientier.synQueryPathsPage(pths, entityName, Port.docsync);

		PathsPage pthpage = rep.pathsPage();
		assertEquals(clientier.client.ssInfo().device, pthpage.device);
		assertEquals(0, pthpage.paths().size());

		for (String pth : pthpage.paths().keySet())
			pathpool.remove(pth);

		assertEquals(isNull(paths) ? 0 : paths.length, pathpool.size());
	}

}
