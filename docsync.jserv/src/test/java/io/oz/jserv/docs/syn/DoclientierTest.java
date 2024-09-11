package io.oz.jserv.docs.syn;

import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.waiting;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.Utils.loadTxt;
import static io.odysz.common.Utils.logT;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.pause;
import static io.oz.jserv.docs.syn.SynoderTest.azert;
import static io.oz.jserv.docs.syn.SynoderTest.zsu;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.docm;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.errLog;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.initSysRecords;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.jetties;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.slava;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.startSyndoctier;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.syrskyi;
import static io.oz.jserv.test.JettyHelperTest.webinf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.JProtocol.OnOk;
import io.odysz.semantic.meta.ExpDocTableMeta.Share;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.syn.Docheck;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.PathsPage;
import io.odysz.semantics.IUser;
import io.odysz.transact.x.TransException;

class DoclientierTest {
	public final static int X = 0;
	public final static int Y = 1;
	public final static int Z = 2;
	public final static int W = 3;

	public static class Dev {
		public final String uri;
		public final String uid;
		public final String psw;
		public final String dev;
		public final String folder;

		public String res;
		public Doclientier client;

		Dev(String uri, String uid, String pswd, String device, String folder, String fres) {
			this.uri = uri;
			this.uid = uid;
			this.psw = pswd;
			this.dev = "test-doclient/" + device;
			this.folder = folder;
			this.res = fres;
		}
	}

	static int bsize;

	static final String clientconn = "main-sqlite";

	static final String[] servs_conn  = new String[] {
			"no-jserv.00", "no-jserv.01", "no-jserv.02", "no-jserv.03"};

	static final String[] config_xmls = new String[] {
			"config-0.xml", "config-1.xml", "config-2.xml", "config-3.xml"};
	
	static Dev[] devs;
	private static Docheck[] ck;
	
	static final int X_0 = 0;
	static final int X_1 = 1;
	static final int Y_0 = 2;
	static final int Y_1 = 3;

	static {
		try {
			devs = new Dev[4];
			devs[X_0] = new Dev("client-at-00", syrskyi, slava, "X-0", zsu,
								"src/test/res/anclient.java/1-pdf.pdf");

			devs[X_1] = new Dev("client-at-00", "syrskyi", "слава україні", "X-1", zsu,
								"src/test/res/anclient.java/2-ontario.gif");

			devs[Y_0] = new Dev("client-at-01", "odyz", "8964", "Y-0", zsu,
								"src/test/res/anclient.java/3-birds.wav");

			devs[Y_1] = new Dev("client-at-01", "syrskyi", "слава україні", "Y-1", zsu,
								"src/test/res/anclient.java/Amelia Anisovych.mp4");

			bsize = 72 * 1024;
			docm = new T_PhotoMeta(clientconn);
		} catch (TransException e) {
			e.printStackTrace();
		}
	}
	
	@BeforeAll
	static void init() throws Exception {
		String p = new File("src/test/res").getAbsolutePath();
    	System.setProperty("VOLUME_HOME", p + "/volume");
    	logi("VOLUME_HOME : %s", System.getProperty("VOLUME_HOME"));

		Configs.init(webinf);
		Connects.init(webinf);

		ck = new Docheck[servs_conn.length];
		
		int port = 8090;
		for (int i = 0; i < servs_conn.length; i++) {
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
		for (int i = 0; i < servs_conn.length; i++) {
			Utils.logi("Jservs at %s", servs_conn[i]);

			for (int j = 0; j < jetties.length; j++) {
				SynodeMeta synm = ck[i].trb.synm;

				ck[i].b0.update(synm.tbl, robot)
					.nv(synm.jserv, jetties[j].jserv())
					.whereEq(synm.pk, jetties[j].synode())
					.whereEq(synm.domain, ck[i].trb.domain())
					.u(ck[i].b0.instancontxt(servs_conn[i], robot));
			}
		}

		for (int i = 0; i < servs_conn.length; i++)
			jetties[i].openDomains();
	}

	@Test
	void testSyncUp() throws Exception {
//		try {
			// 00 create
			ExpSyncDoc dx = clientPush(X, X_0);
			verifyPathsPage(devs[X_0].client, docm.tbl, dx.clientpath);
	
			// 10 create
			clientPush(Y, Y_0);
	
			// 11 create
			clientPush(Y, Y_1);
	
			boolean[] lights = new boolean[] {true, false};
			SynodetierJoinTest.syncdomain(lights, Y);
			awaitAll(lights, -1);

			ck[Y].doc(3);
			ck[X].doc(3);
	
			// 00 delete
			Dev devx0 = devs[X_0];
			Clients.init(jetties[X].jserv());
			DocsResp rep = devx0.client.synDel(docm.tbl, devx0.dev, devx0.res);
			assertEquals(1, rep.total(0));
	
			verifyPathsPageNegative(devx0.client, docm.tbl, dx.clientpath);

			waiting(lights, Y);
			SynodetierJoinTest.syncdomain(lights, Y);
			awaitAll(lights);
			ck[Y].doc(2);
			ck[X].doc(2);
	
			pause("Press enter to quite ...");
//		} catch (Exception e) {
//			e.printStackTrace();
//	
//			Utils.warn(e.getMessage());
//			pause("Press enter to quite ...");
//
//			fail(e.getMessage());
//		}
	}


	@AfterAll
	static void close() throws Exception {
		logi("Server is closed.");
	}

	ExpSyncDoc clientPush(int to, int cix) throws Exception {
		Dev dev = devs[cix];

		Clients.init(jetties[to].jserv());

		Doclientier client = new Doclientier(dev.uri, errLog)
				.tempRoot(dev.uri)
				.loginWithUri(dev.uri, dev.uid, dev.dev, dev.psw)
				.blockSize(bsize);
		dev.client = client;
		
		Utils.logi("-------------- Logged in. %s",
				client.client.ssInfo().toString());

		ExpSyncDoc xdoc = videoUpByApp(dev, client, docm.tbl);
		assertEquals(dev.dev, xdoc.device());
		assertEquals(dev.res, xdoc.fullpath());

		verifyPathsPage(client, docm.tbl, xdoc.clientpath);
		return xdoc;
	}

 	static ExpSyncDoc videoUpByApp(Dev atdev, Doclientier doclient, String entityName) throws Exception {

		ExpSyncDoc doc = (ExpSyncDoc) new ExpSyncDoc()
					.share(doclient.robt.uid(), Share.pub, new Date())
					.folder(atdev.folder)
					.device(atdev.dev)
					.fullpath(atdev.res);

		DocsResp resp = doclient.startPush(entityName, doc,
			(AnsonResp rep) -> {
				ExpSyncDoc d = ((DocsResp) rep).xdoc; 

				// pushing again should fail
				try {
					doclient.startPush(entityName, d,
						new OnOk() {
							@Override
							public void ok(AnsonResp rep)
									throws IOException, AnsonException {
								logT(new Object() {}, rep.msg());
								fail("Double checking failed.");
							}
						},
						new ErrorCtx() {
							@Override
							public void err(MsgCode code, String msg, String...args) {
								// expected
							}
						});
				} catch (TransException | IOException | SQLException e) {
					e.printStackTrace();
					fail(e.getMessage());
				}
			});

		assertNotNull(resp);

		String docId = resp.xdoc.recId();
		assertTrue(4 == docId.length() || 8 == docId.length());

		DocsResp rp = doclient.selectDoc(entityName, docId);

		assertTrue(isblank(rp.msg()));
		assertNotNull(rp.xdoc);

		return rp.xdoc;
	}

	/**
	 * Verify device &amp; client-paths are presenting at server.
	 * 
	 * @param clientier
	 * @param entityName
	 * @param paths
	 * @throws Exception
	 */
	static void verifyPathsPage(Doclientier clientier, String entityName, String... paths) throws Exception {
		PathsPage pths = new PathsPage(clientier.client.ssInfo().device, 0, 1);
		HashSet<String> pathpool = new HashSet<String>();
		for (String pth : paths) {
			pths.add(pth);
			pathpool.add(pth);
		}

		DocsResp rep = clientier.synQueryPathsPage(pths, entityName, Port.docsync);

		PathsPage pthpage = rep.pathsPage();

		assertEquals(clientier.client.ssInfo().device, pthpage.device);
		assertEquals(len(paths), pthpage.paths().size());

		for (String pth : paths)
			pathpool.remove(pth);

		assertEquals(0, pathpool.size());
	}

	/**
	 * Verify device &amp; client-paths isn't presenting at server.
	 * 
	 * @param clientier
	 * @param entityName
	 * @param paths
	 * @throws Exception
	 */
	static void verifyPathsPageNegative(Doclientier clientier, String entityName, String... paths) throws Exception {
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
}
