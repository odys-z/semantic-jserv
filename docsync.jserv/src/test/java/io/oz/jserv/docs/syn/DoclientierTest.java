package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.Utils.loadTxt;
import static io.odysz.common.Utils.logT;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.pause;
import static io.odysz.semantic.meta.SemanticTableMeta.setupSqliTables;
import static io.oz.jserv.docsync.ZSUNodes.clientUriZsu;

import static io.oz.jserv.docs.syn.SynoderTest.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Configs;
import io.odysz.jclient.Clients;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.JProtocol.OnOk;
import io.odysz.semantic.jserv.R.AnQuery;
import io.odysz.semantic.jserv.U.AnUpdate;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.HeartLink;
import io.odysz.semantic.jsession.JUser;
import io.odysz.semantic.jsession.JUser.JOrgMeta;
import io.odysz.semantic.jsession.JUser.JRoleMeta;
import io.odysz.semantic.meta.AutoSeqMeta;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.meta.ExpDocTableMeta.Share;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.meta.PeersMeta;
import io.odysz.semantic.meta.SynChangeMeta;
import io.odysz.semantic.meta.SynSessionMeta;
import io.odysz.semantic.meta.SynSubsMeta;
import io.odysz.semantic.meta.SynchangeBuffMeta;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantics.IUser;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docsync.ZSUNodes.AnDevice;
import io.oz.jserv.docsync.ZSUNodes.Kharkiv;
import io.oz.jserv.test.JettyHelper;

class DoclientierTest {
	static final String clientUri0 = "client-at-00";
	static final String clientUri1 = "client-at-01";

	static int bsize;

	static ExpDocTableMeta docm;
	static ErrorCtx errLog;
	
	static final String clientconn = "main-sqlite";
	static final String serv_conn = "no-jserv.00";

	// static final String wwwinf = "src/test/res/WEB-INF";


	static {
		try {
			bsize = 72 * 1024;
			docm = new T_PhotoMeta(clientconn);
			
			// doclient = new Doclientier(clientUri, errLog);

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

		/*
		ArrayList<String> sqls = new ArrayList<String>();
		sqls.add(String.format("delete from %s;", aum.tbl));
		sqls.add(Utils.loadTxt("./oz_autoseq.sql"));
		sqls.add(String.format( "update oz_autoseq set seq = %d where sid = '%s.%s'",
								(long) Math.pow(64, 2), docm.tbl, docm.pk));
		sqls.add(String.format("delete from %s", snm.tbl));
		Connects.commit(conn, DATranscxt.dummyUser(), sqls);
		*/
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
			for (String tbl : new String[] {
					"oz_autoseq", "a_users"}) {
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
			Connects.reload(webinf); // reload metas
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

		videoUpByApp(docm.tbl);

		pause("Press enter to quite ...");
	}

	static String videoUpByApp(String entityName) throws Exception {

		// app is using Doclientier for synchronizing 
		Doclientier doclient = new Doclientier(clientUri0, errLog)
				.tempRoot("app.kharkiv")
				.loginWithUri(clientUri0, AnDevice.userId, AnDevice.device, AnDevice.passwd)
				.blockSize(bsize);

		doclient.synDel(entityName, AnDevice.device, AnDevice.localFile);

		ExpSyncDoc doc = (ExpSyncDoc) new ExpSyncDoc()
					.share(doclient.robot.uid(), Share.pub, new Date())
					.folder(Kharkiv.folder)
					.fullpath(AnDevice.localFile);

		DocsResp resp = doclient.startPush(entityName, doc, new OnOk() {

			@Override
			public void ok(AnsonResp resp)
					throws IOException, AnsonException {
				ExpSyncDoc doc = ((DocsResp) resp).xdoc; 

				try {
					doclient.loginWithUri(clientUri0, AnDevice.userId, AnDevice.device, AnDevice.passwd)
							.blockSize(bsize);
				} catch (SsException e1) {
					e1.printStackTrace();
					fail(e1.getMessage());
				} catch (TransException e) {
					e.printStackTrace();
				}

				// pushing again should fail
				// List<DocsResp> resps2 = null;
				try {
					DocsResp resp2 = doclient.startPush(entityName, doc,
						new OnOk() {
							@Override
							public void ok(AnsonResp resp)
									throws IOException, AnsonException {
								logT(new Object() {}, resp.msg());
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
			}
		});

		assertNotNull(resp);

		String docId = resp.xdoc.recId();
		assertTrue(4 == docId.length() || 8 == docId.length());

		DocsResp rp = doclient.selectDoc(entityName, docId);

		assertTrue(isblank(rp.msg()));
		assertEquals(AnDevice.device, rp.xdoc.device());
		assertEquals(AnDevice.localFile, rp.xdoc.fullpath());

		return AnDevice.localFile;
	}

	void testSynDel() {
		fail("Not yet implemented");
	}

	void testSynQueryPathsPage(String entityName) throws Exception {
		Doclientier clientier = new Doclientier(clientUriZsu, errLog)
				.tempRoot("app.kharkiv")
				.loginWithUri(clientUri0, AnDevice.userId, AnDevice.device, AnDevice.passwd)
				.blockSize(bsize);

		clientier.synDel(entityName, AnDevice.device, AnDevice.localFile);

		ExpSyncDoc doc = (ExpSyncDoc) new ExpSyncDoc()
					.share(clientier.robot.uid(), Share.pub, new Date())
					.folder(Kharkiv.folder)
					.fullpath(AnDevice.localFile);

		DocsResp resp = clientier.startPush(entityName, doc, new OnOk() {
			@Override
			public void ok(AnsonResp resp) throws IOException, AnsonException, TransException {
			}
		});
	}
}
