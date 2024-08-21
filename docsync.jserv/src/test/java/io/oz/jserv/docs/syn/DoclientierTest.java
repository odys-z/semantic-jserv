package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.isNull;
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

	static int bsize;

	static ExpDocTableMeta docm;
	static ErrorCtx errLog;
	
	static final String clientconn = "main-sqlite";
	static final String serv_conn = "no-jserv.00";

	static {
		try {
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

		AutoSeqMeta asqm = new AutoSeqMeta();
		JRoleMeta arlm = new JUser.JRoleMeta();
		JOrgMeta  aorgm = new JUser.JOrgMeta();
		
		SynChangeMeta chm = new SynChangeMeta();
		SynSubsMeta sbm = new SynSubsMeta(chm);
		SynchangeBuffMeta xbm = new SynchangeBuffMeta(chm);
		SynSessionMeta ssm = new SynSessionMeta();
		PeersMeta prm = new PeersMeta();
		
		SynodeMeta snm = new SynodeMeta(serv_conn);
		docm = new T_PhotoMeta(serv_conn); // .replace();
		setupSqliTables(serv_conn, asqm, arlm, aorgm, snm, chm, sbm, xbm, prm, ssm, docm);
		setupSqliTables(serv_conn, asqm, arlm, aorgm, snm, chm, sbm, xbm, prm, ssm, docm);

		initRecords(serv_conn);
		
		Connects.reload(webinf); // reload metas

		// synode
		String servIP = "localhost";
		int port = 8090;

		JettyHelper.startJserv(webinf, serv_conn, "config-0.xml",
				servIP, port,
				new AnSession(), new AnQuery(), new AnUpdate(),
				new HeartLink());

		JettyHelper.addPort(new Syntier(Configs.getCfg(Configs.keys.synode), serv_conn)
				   .start(SynoderTest.ura, SynoderTest.zsu, serv_conn, SynodeMode.peer));

		// client
		String jserv = String.format("http://%s:%s", servIP, port);
		logi("Server started at %s", jserv);
		Clients.init(jserv);
	}

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
		Doclientier client00 = new Doclientier(Dev_0_0.uri, errLog)
				.tempRoot("app.kharkiv")
				.loginWithUri(Dev_0_0.uri, Dev_0_0.uid, Dev_0_0.dev, Dev_0_0.psw)
				.blockSize(bsize);
		
		Utils.logi("-------------- Logged in. %s",
				client00.client.ssInfo().toString());

		String fpth = videoUpByApp(client00, docm.tbl);

		verifyPathsPage(client00, docm.tbl, fpth);

		DocsResp rep = client00.synDel(docm.tbl, Dev_0_0.dev, Dev_0_0.mp4);
		assertEquals(1, rep.total(0));

		verifyPathsPageNegative(client00, docm.tbl, fpth);

		// pause("Press enter to quite ...");
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
	void verifyPathsPage(Doclientier clientier, String entityName, String... paths) throws Exception {
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

	void verifyPathsPageNegative(Doclientier clientier, String entityName, String... paths) throws Exception {
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
