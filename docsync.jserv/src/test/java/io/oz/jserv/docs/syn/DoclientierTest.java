package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.logT;
import static io.odysz.common.Utils.pause;
import static io.oz.jserv.docsync.ZSUNodes.clientUri;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.anson.x.AnsonException;
import io.odysz.jclient.Clients;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.JProtocol.OnOk;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.HeartLink;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.meta.ExpDocTableMeta.Share;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docsync.ZSUNodes.AnDevice;
import io.oz.jserv.docsync.ZSUNodes.Kharkiv;
import io.oz.jserv.test.JettyHelper;

class DoclientierTest {
	static int bsize;

	static ExpDocTableMeta docm;
	static ErrorCtx errLog;
	
	// static Doclientier doclient;

	// static final String synode = "test-0";
	static final String clientconn = "main-sqlite";
	static final String serv_conn = "what?";

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

		String servIP = "localhost";
		int port = 8090;


		JettyHelper.startJserv("config-0.xml", servIP, port,
				new AnSession(),
				new HeartLink());

		JettyHelper.addPort(new Syntier(null, serv_conn));

		// client
		String jserv = String.format("http://%s:%s", servIP, port);
		logi("Server started at %s", jserv);
		Clients.init(jserv);
	}

	@AfterAll
	static void close() throws Exception {
		JettyHelper.stop();

		logi("Server closed");
	}

	@Test
	void testSyncUp() throws Exception {

		videoUpByApp(docm);

		pause("Press enter to quite ...");
	}

	static String videoUpByApp(ExpDocTableMeta docm)
			throws AnsonException, SsException, SemanticException, IOException, TransException, SQLException {

		// app is using Doclientier for synchronizing 
		Doclientier doclient = new Doclientier(clientUri, errLog)
				.tempRoot("app.kharkiv")
				.login(AnDevice.userId, AnDevice.device, AnDevice.passwd)
				.blockSize(bsize);

		doclient.synDel(docm.tbl, AnDevice.device, AnDevice.localFile);

		SyncDoc doc = (SyncDoc) new SyncDoc()
					.share(doclient.robot.uid(), Share.pub, new Date())
					.folder(Kharkiv.folder)
					.fullpath(AnDevice.localFile);

		DocsResp resp = doclient.startPush(docm.tbl, doc, new OnOk() {

			@Override
			public void ok(AnsonResp resp)
					throws IOException, AnsonException {
				SyncDoc doc = ((DocsResp) resp).doc; 

				try {
					doclient.login(Kharkiv.Synode.worker, Kharkiv.Synode.nodeId, Kharkiv.Synode.passwd)
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
					DocsResp resp2 = doclient.startPush(docm.tbl, doc,
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

		String docId = resp.doc.recId();
		assertEquals(8, docId.length());

		DocsResp rp = doclient.selectDoc(docm.tbl, docId);

		assertTrue(isblank(rp.msg()));
		assertEquals(AnDevice.device, rp.doc.device());
		assertEquals(AnDevice.localFile, rp.doc.fullpath());

		return AnDevice.localFile;
	}

	void testSynDel() {
		fail("Not yet implemented");
	}

	void testSynQueryPathsPage() throws Exception {
		Doclientier clientier = new Doclientier(clientUri, errLog)
				.tempRoot("app.kharkiv")
				.login(AnDevice.userId, AnDevice.device, AnDevice.passwd)
				.blockSize(bsize);

		clientier.synDel(docm.tbl, AnDevice.device, AnDevice.localFile);

		SyncDoc doc = (SyncDoc) new SyncDoc()
					.share(clientier.robot.uid(), Share.pub, new Date())
					.folder(Kharkiv.folder)
					.fullpath(AnDevice.localFile);

		DocsResp resp = clientier.startPush(docm.tbl, doc, new OnOk() {
			@Override
			public void ok(AnsonResp resp) throws IOException, AnsonException, TransException {
			}
		});
	}
}
