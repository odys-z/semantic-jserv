package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.Utils.loadTxt;
import static io.odysz.common.Utils.logT;
import static io.odysz.common.Utils.logi;
import static io.odysz.semantic.meta.SemanticTableMeta.setupSqliTables;
import static io.oz.jserv.docs.syn.SynoderTest.webinf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
import io.odysz.semantic.jserv.R.AnQuery;
import io.odysz.semantic.jserv.U.AnUpdate;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.HeartLink;
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
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.PathsPage;
import io.odysz.semantics.IUser;
import io.odysz.transact.x.TransException;
import io.oz.jserv.test.JettyHelper;

class DoclientierTest {
	public static class Dev_0_0 {
		public static final String uri = "client-at-00";
		public static final String uid = "syrskyi";
		public static final String psw = "слава україні";
		public static final String dev = "0-0";
		public static final String mp4 = "src/test/res/anclient.java/Amelia Anisovych.mp4";
		public static final String folder = "zsu";
	}

	public static class Dev_0_1 {
		public static final String uri = "client-at-00";
		public static final String uid = "syrskyi";
		public static final String psw = "слава україні";
		public static final String dev = "0-1";
		public static final String mp4 = "src/test/res/anclient.java/Amelia Anisovych.mp4";
		public static final String folder = "zsu";
	}

	public static class Dev_1_0 {
		public static final String uri = "client-at-01";
		public static final String uid = "ody";
		public static final String psw = "123456";
		public static final String dev = "1-0";
		public static final String mp4 = "src/test/res/anclient.java/Amelia Anisovych.mp4";
		public static final String folder = "zsu";
	}

	public static class Dev_1_1 {
		public static final String uri = "client-at-01";
		public static final String uid = "syrskyi";
		public static final String psw = "слава україні";
		public static final String dev = "1-1";
		public static final String mp4 = "src/test/res/anclient.java/Amelia Anisovych.mp4";
		public static final String folder = "zsu";
	}

	public static class Dev {
		public final String uri;
		public final String uid;
		public final String psw;
		public final String dev;
		public final String folder;
		
		public String jserv;
		public Doclientier client;
		public String mp4_clientpath;

		Dev(String uri, String uid, String pswd, String device, String folder) {
			this.uri = uri;
			this.uid = uid;
			this.psw = pswd;
			this.dev = device;
			this.folder = folder;
		}
	}

	static int bsize;

	static ExpDocTableMeta docm;
	static ErrorCtx errLog;
	
	static final String clientconn = "main-sqlite";

	static final String[] servs_conn = new String[] {
			"no-jserv.00", "no-jserv.01", "no-jserv.02", "no-jserv.03"};
	
	static Dev[] devs; // = new Dev[4];
	
	static final int _0_0 = 0;
	static final int _0_1 = 1;
	static final int _1_0 = 2;
	static final int _1_1 = 3;

	static {
		try {
			devs = new Dev[4];
			devs[_0_0] = new Dev(Dev_0_0.uri, Dev_0_0.uid, Dev_0_0.psw, Dev_0_0.dev, Dev_0_0.folder);
			devs[_0_1] = new Dev(Dev_0_1.uri, Dev_0_1.uid, Dev_0_1.psw, Dev_0_1.dev, Dev_0_1.folder);
			devs[_1_0] = new Dev(Dev_1_0.uri, Dev_1_0.uid, Dev_1_0.psw, Dev_1_0.dev, Dev_1_0.folder);
			devs[_1_1] = new Dev(Dev_1_1.uri, Dev_1_1.uid, Dev_1_1.psw, Dev_1_1.dev, Dev_1_1.folder);

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
	
	@BeforeAll
	static void init() throws Exception {
    	System.setProperty("VOLUME_HOME", "../volume");
    	logi("VOLUME_HOME : %s", System.getProperty("VOLUME_HOME"));

		Configs.init(webinf);
		Connects.init(webinf);

		int port = 8090;
		for (int i = 0; i < servs_conn.length; i++)
			devs[i].jserv = startSyndoctier(servs_conn[i], port++);

		initRecords(servs_conn[0]);
	}
	
	static String startSyndoctier(String serv_conn, int port) throws Exception {
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

		// synode
		String servIP = "localhost";

		JettyHelper.startJserv(webinf, serv_conn, "config-0.xml",
				servIP, port,
				new AnSession(), new AnQuery(), new AnUpdate(),
				new HeartLink());

		JettyHelper.addServPort(new Syntier(Configs.getCfg(Configs.keys.synode), serv_conn)
				   .start(SynoderTest.ura, SynoderTest.zsu, serv_conn, SynodeMode.peer));

		// client
		String jserv = String.format("http://%s:%s", servIP, port);
		logi("Server started at %s", jserv);
		return jserv;
	}

	/**
	 * initialize with files, i. e. oz_autoseq.sql, a_users.sqlite.sql.
	 * 
	 * @param conn
	 */
	private static void initRecords(String conn) {
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
		JettyHelper.stop();

		logi("Server closed");
	}

	@Test
	void testSyncUp() throws Exception {
		// 00 create
//		Clients.init(devs[0].jserv);
//		Doclientier client00 = new Doclientier(Dev_0_0.uri, errLog)
//				.tempRoot(Dev_0_0.uri)
//				.loginWithUri(Dev_0_0.uri, Dev_0_0.uid, Dev_0_0.dev, Dev_0_0.psw)
//				.blockSize(bsize);
//		
//		Utils.logi("-------------- Logged in. %s",
//				client00.client.ssInfo().toString());
//
//		String fpth00 = videoUpByApp(client00, docm.tbl);

		String fpth00 = clientPush(_0_0);
		verifyPathsPage(devs[_0_0].client, docm.tbl, fpth00);

		// 10 create
		clientPush(_1_0);

		// 11 create
		clientPush(_1_1);


		// 00 delete
		Dev d00 = devs[_0_0];
		Clients.init(d00.jserv);
		DocsResp rep = d00.client.synDel(docm.tbl, d00.dev, d00.mp4_clientpath);
		assertEquals(1, rep.total(0));

		verifyPathsPageNegative(d00.client, docm.tbl, fpth00);

		// pause("Press enter to quite ...");
	}
	
	String clientPush(int cix) throws Exception {
		Dev dev = devs[cix];

		Clients.init(dev.jserv);

		Doclientier client = new Doclientier(dev.uri, errLog)
				.tempRoot(dev.uri)
				.loginWithUri(dev.uri, dev.uid, dev.dev, dev.psw)
				.blockSize(bsize);
		dev.client = client;
		
		Utils.logi("-------------- Logged in. %s",
				client.client.ssInfo().toString());

		String fpth = videoUpByApp(client, docm.tbl);

		verifyPathsPage(client, docm.tbl, fpth);
		return fpth;
	}

 	static String videoUpByApp(Doclientier doclient, String entityName) throws Exception {

		ExpSyncDoc doc = (ExpSyncDoc) new ExpSyncDoc()
					.share(doclient.robot.uid(), Share.pub, new Date())
					.folder(Dev_0_0.folder)
					.fullpath(Dev_0_0.mp4);

		DocsResp resp = doclient.startPush(entityName, doc,
			(AnsonResp rep) -> {
				ExpSyncDoc d = ((DocsResp) rep).xdoc; 

				// pushing again should fail
				try {
					DocsResp resp2 = doclient.startPush(entityName, d,
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
		assertEquals(Dev_0_0.dev, rp.xdoc.device());
		assertEquals(Dev_0_0.mp4, rp.xdoc.fullpath());

		return Dev_0_0.mp4;
	}

	void testSynDel() {
		fail("Not yet implemented");
	}

	/**
	 * Verify paths are presenting at server.
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
