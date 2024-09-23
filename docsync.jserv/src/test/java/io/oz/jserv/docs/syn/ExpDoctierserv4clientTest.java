package io.oz.jserv.docs.syn;

import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.waiting;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.Utils.loadTxt;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.pause;
import static io.oz.jserv.docs.syn.Dev.docm;
import static io.oz.jserv.docs.syn.Dev.devs;
import static io.oz.jserv.docs.syn.Dev.X_0;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.initSysRecords;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.jetties;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.startSyndoctier;
import static io.oz.jserv.docs.syn.SynoderTest.azert;
import static io.oz.jserv.docs.syn.SynoderTest.zsu;
import static io.oz.jserv.test.JettyHelperTest.webinf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

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
import io.odysz.semantic.meta.ExpDocTableMeta.Share;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.syn.Docheck;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.PathsPage;
import io.odysz.semantics.IUser;

/**
 * 
 * Run SynodetierJoinTest for creating table a_orgs.
 * 
 * @author ody
 */
public class ExpDoctierserv4clientTest {
	public final static int X = 0;
	public final static int Y = 1;
	public final static int Z = 2;
	public final static int W = 3;

	static final String[] servs_conn  = new String[] {
			"no-jserv.00", "no-jserv.01", "no-jserv.02", "no-jserv.03"};

	static final String[] config_xmls = new String[] {
			"config-0.xml", "config-1.xml", "config-2.xml", "config-3.xml"};
	
	private static Docheck[] ck;

	@BeforeAll
	static void init() throws Exception {
		String p = new File("src/test/res").getAbsolutePath();
    	System.setProperty("VOLUME_HOME", p + "/volume");
    	logi("VOLUME_HOME : %s", System.getProperty("VOLUME_HOME"));

		Configs.init(webinf);
		Connects.init(webinf);

		ck = new Docheck[servs_conn.length];
	}
	
	@Test
	void runDoctiers() throws Exception {
		int[] nodex = new int[] { X, Y, Z };
		
		int port = 8090;
		// for (int i : new int[] {X, Y, Z, W}) {
		for (int i : nodex) {
			if (jetties[i] != null)
				jetties[i].stop();

			initSysRecords(servs_conn[i]);

			initSynodeRecs(servs_conn[i]);
			
			jetties[i] = startSyndoctier(servs_conn[i], config_xmls[i], port++, false)
						; // .loadSynclients();
			
			ck[i] = new Docheck(azert, zsu, servs_conn[i],
								jetties[i].synode(), SynodeMode.peer, docm);
		}
		
		IUser robot = DATranscxt.dummyUser();
		// for (int i = 0; i < servs_conn.length; i++) {
		for (int i : nodex) {
			Utils.logi("Jservs at %s", servs_conn[i]);

			// for (int j = 0; j < jetties.length; j++) {
			for (int j : nodex) {
				SynodeMeta synm = ck[i].trb.synm;

				ck[i].b0.update(synm.tbl, robot)
					.nv(synm.jserv, jetties[j].jserv())
					.whereEq(synm.pk, jetties[j].synode())
					.whereEq(synm.domain, ck[i].trb.domain())
					.u(ck[i].b0.instancontxt(servs_conn[i], robot));
			}
		}

		final boolean[] lights = new boolean[nodex.length];
		// for (int i = 0; i < servs_conn.length; i++)
		for (int i : nodex)
			jetties[i].openDomains( (domain, mynid, peer, xp) -> {
				lights[i] = true;
			});
		awaitAll(lights);
		pause("Press Enter for starting synchronizing.");

		// lights = new boolean[] {true, false};
		waiting(lights, Y);
		SynodetierJoinTest.syncdomain(lights, Y);
		awaitAll(lights, -1);

		ck[Y].doc(3);
		ck[X].doc(3);

		// 00 delete
		Dev devx0 = devs[X_0];
		Clients.init(jetties[X].jserv());
		DocsResp rep = devx0.client.synDel(docm.tbl, devx0.dev, devx0.res);
		assertEquals(1, rep.total(0));

		ExpSyncDoc dx0 = (ExpSyncDoc) new ExpSyncDoc()
					.share(devx0.uid, Share.pub, new Date())
					.folder(devx0.folder)
					.device(devx0.dev)
					.fullpath(devx0.res);

		verifyPathsPageNegative(devx0.client, docm.tbl, dx0.clientpath);

		waiting(lights, Y);
		SynodetierJoinTest.syncdomain(lights, Y);

		awaitAll(lights);
		ck[Y].doc(2);
		ck[X].doc(2);
	}

	/**
	 * Initialize syn_* tables' records, must be called after #SynodetierJoinTest#setupSqliTables()}.
	 * 
	 * @param conn
	 */
	static void initSynodeRecs(String conn) {
		ArrayList<String> sqls = new ArrayList<String>();
		IUser usr = DATranscxt.dummyUser();

		try {
			for (String tbl : new String[] {
					"syn_synode_all_ready.sqlite.sql"}) {

				sqls.add(loadTxt(DoclientierTest.class, tbl));
				Connects.commit(conn, usr, sqls, Connects.flag_nothing);
				sqls.clear();
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
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
