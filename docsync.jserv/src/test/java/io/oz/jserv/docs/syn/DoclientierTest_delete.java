package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.loadTxt;
import static io.odysz.common.Utils.logT;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.pause;
import static io.odysz.semantic.meta.SemanticTableMeta.setupSqliTables;
import static io.oz.jserv.test.JettyHelperTest.webinf;
import static io.oz.jserv.docs.syn.SynoderTest.*;
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
import org.junit.jupiter.api.Disabled;
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
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.JUser;
import io.odysz.semantic.jsession.JUser.JOrgMeta;
import io.odysz.semantic.jsession.JUser.JRoleMeta;
import io.odysz.semantic.meta.AutoSeqMeta;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.meta.ExpDocTableMeta.Share;
import io.odysz.semantic.meta.PeersMeta;
import io.odysz.semantic.meta.SynChangeMeta;
import io.odysz.semantic.meta.SynSessionMeta;
import io.odysz.semantic.meta.SynSubsMeta;
import io.odysz.semantic.meta.SynchangeBuffMeta;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.syn.Docheck;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.PathsPage;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.jetty.SynotierJettyApp;

class DoclientierTest_delete {
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
		public String jserv;
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

	static ExpDocTableMeta docm;
	static ErrorCtx errLog;
	
	static final String clientconn = "main-sqlite";

	static final String[] servs_conn  = new String[] {
			"no-jserv.00", "no-jserv.01", "no-jserv.02", "no-jserv.03"};

	static final String[] config_xmls = new String[] {
			"config-0.xml", "config-1.xml", "config-2.xml", "config-3.xml"};
	
	static Dev[] devs; // = new Dev[4];
	static SynotierJettyApp[] jetties;
	private static Docheck[] ck;
	
	static final int X_0 = 0;
	static final int X_1 = 1;
	static final int Y_0 = 2;
	static final int Y_1 = 3;

	static {
		try {
			jetties = new SynotierJettyApp[4];
			devs = new Dev[4];
			devs[X_0] = new Dev("client-at-00", "syrskyi", "слава україні", "X-0", zsu,
								"src/test/res/anclient.java/1-pdf.pdf");

			devs[X_1] = new Dev("client-at-00", "syrskyi", "слава україні", "X-1", zsu,
								"src/test/res/anclient.java/2-ontario.gif");

			devs[Y_0] = new Dev("client-at-01", "odyz", "8964", "Y-0", zsu,
								"src/test/res/anclient.java/3-birds.wav");

			devs[Y_1] = new Dev("client-at-01", "syrskyi", "слава україні", "Y-1", zsu,
								"src/test/res/anclient.java/Amelia Anisovych.mp4");

			bsize = 72 * 1024;
			docm = new T_PhotoMeta(clientconn);
			
			errLog = new ErrorCtx() {
				@Override
				public void err(MsgCode code, String msg, String...args) {
					fail(msg);
				}
			};

		} catch (TransException e) {
			e.printStackTrace();
		}
	}
	
	@Disabled
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
			jetties[i] = startSyndoctier(servs_conn[i], config_xmls[i], port++);
			devs[i].jserv = jetties[i].jserv();
			initRecords(servs_conn[i]);

			ck[i] = new Docheck(azert, zsu, servs_conn[i], zsu, SynodeMode.peer, docm);
		}
	}

	@Disabled
	@Test
	void testSyncUp() {
		try {
			setupDomain();
	
			// 00 create
			ExpSyncDoc dx = clientPush(X_0);
			verifyPathsPage(devs[X_0].client, docm.tbl, dx.clientpath);
	
			// 10 create
			clientPush(Y_0);
	
			// 11 create
			clientPush(Y_1);
	
			syncdomain(Y);

			ck[Y].doc(2);
			ck[X].doc(2);
	
			// 00 delete
			Dev d00 = devs[X_0];
			Clients.init(d00.jserv);
			DocsResp rep = d00.client.synDel(docm.tbl, d00.dev, d00.res);
			assertEquals(1, rep.total(0));
	
			verifyPathsPageNegative(d00.client, docm.tbl, dx.clientpath);
	
			pause("Press enter to quite ...");
		} catch (Exception e) {
			e.printStackTrace();
	
			pause(e.getMessage());
			fail(e.getMessage());
		}
	}

	static SynotierJettyApp startSyndoctier(String serv_conn, String config_xml, int port) throws Exception {
		AutoSeqMeta asqm = new AutoSeqMeta();
		JRoleMeta arlm = new JUser.JRoleMeta();
		JOrgMeta  aorgm = new JUser.JOrgMeta();
	
		SynChangeMeta chm = new SynChangeMeta();
		SynSubsMeta sbm = new SynSubsMeta(chm);
		SynchangeBuffMeta xbm = new SynchangeBuffMeta(chm);
		SynSessionMeta ssm = new SynSessionMeta();
		PeersMeta prm = new PeersMeta();
	
		SynodeMeta snm = new SynodeMeta(serv_conn);
		docm = new T_PhotoMeta(serv_conn);
		setupSqliTables(serv_conn, asqm, arlm, aorgm, snm, chm, sbm, xbm, prm, ssm, docm);

		return SynotierJettyApp .createSyndoctierApp(serv_conn, config_xml, null, port, webinf, ura, zsu)
								.start(() -> System.out, () -> System.err);
	}

	/**
	 * initialize with files, i. e. oz_autoseq.sql, a_users.sqlite.sql.
	 * 
	 * @param conn
	 */
	static void initRecords(String conn) {
		ArrayList<String> sqls = new ArrayList<String>();
		IUser usr = DATranscxt.dummyUser();

		try {
			for (String tbl : new String[] {"oz_autoseq", "a_users"}) {
				sqls.add("drop table if exists " + tbl);
				Connects.commit(conn, usr, sqls, Connects.flag_nothing);
				sqls.clear();
			}

			for (String tbl : new String[] {
					"oz_autoseq.ddl",  "oz_autoseq.sql",
					"a_users.sqlite.ddl", "a_users.sqlite.sql"}) {

				sqls.add(loadTxt(DoclientierTest.class, tbl));
				Connects.commit(conn, usr, sqls, Connects.flag_nothing);
				sqls.clear();
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@AfterAll
	static void close() throws Exception {
		for (SynotierJettyApp h : jetties)
			h.stop();

		logi("Server closed");
	}

	/**
	 * <pre>
	 * X ←join- Y, X ←join- Z;
	 * X ←sync- Z, X ←sync- Y.
	 * </pre>
	 * @throws Exception
	 */
	void setupDomain() throws Exception {
		boolean[] lights = new boolean[] {true, false, false};
		joinby(lights, X, Y);
		joinby(lights, X, Z);

		awaitAll(lights, 36000);
		syncdomain(Z);
		syncdomain(Y);
	}
	
	void joinby(boolean[] lights, int to, int by) throws Exception {
		SynotierJettyApp hub = jetties[to];
		SynotierJettyApp prv = jetties[by];
		Dev dev = devs[by];
		for (String servpattern : hub.synodetiers.keySet()) {
			if (len(hub.synodetiers.get(servpattern)) > 1 || len(prv.synodetiers.get(servpattern)) > 1)
				fail("Multiple synchronizing domain schema is an issue not handled in v 2.0.0.");
			
			for (String dom : hub.synodetiers.get(servpattern).keySet()) {
				SynDomanager hubmanger = hub.synodetiers.get(servpattern).get(dom);
				SynDomanager prvmanger = prv.synodetiers.get(servpattern).get(dom);
	
				prvmanger.joinDomain(dom, hubmanger.synode, hub.jserv(), dev.uid, dev.psw,
						(rep) -> { lights[by] = true; });
			}
		}
	}

	ExpSyncDoc clientPush(int cix) throws Exception {
		Dev dev = devs[cix];

		Clients.init(dev.jserv);

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
					.share(doclient.robot.uid(), Share.pub, new Date())
					.folder(atdev.folder)
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

	void syncdomain(int tx) throws SemanticException, AnsonException, SsException, IOException {
		SynotierJettyApp t = jetties[tx];

		for (String servpattern : t.synodetiers.keySet()) {
			if (len(t.synodetiers.get(servpattern)) > 1)
				fail("Multiple synchronizing domainschema is an issue not handled in v 2.0.0.");

			for (String dom : t.synodetiers.get(servpattern).keySet()) {
				t.synodetiers.get(servpattern).get(dom).updomains();
			}
		}
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

}
