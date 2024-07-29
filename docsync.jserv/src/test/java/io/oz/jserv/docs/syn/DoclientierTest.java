package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.isblank;
import static io.oz.jserv.docsync.ZSUNodes.clientUri;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;

import org.junit.jupiter.api.Test;

import io.odysz.anson.x.AnsonException;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.JProtocol.OnOk;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.meta.ExpDocTableMeta.Share;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docsync.ZSUNodes.AnDevice;
import io.oz.jserv.docsync.ZSUNodes.Kharkiv;

class DoclientierTest {
	static int bsize;

	static ExpDocTableMeta docm;
	static ErrorCtx errLog;
	
	static Doclientier doclient;

	static final String clientconn = "main-sqlite";

	static {
		try {
			bsize = 72 * 1024;
			docm = new T_PhotoMeta(clientconn);
			
			doclient = new Doclientier(clientUri, errLog);

			errLog = new ErrorCtx() {
				@Override
				public void err(MsgCode code, String msg, String...args) {
					fail(msg);
				}
			};

		} catch (TransException | IOException e) {
			e.printStackTrace();
		}
	}


	@Test
	void testLogin() {
		fail("Not yet implemented");
	}

	@Test
	void testSyncUp() throws Exception {
		videoUpByApp(docm);
	}

	static String videoUpByApp(ExpDocTableMeta docm)
			throws AnsonException, SsException, SemanticException, IOException, TransException, SQLException {

		// app is using Doclientier for synchronizing 
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
			public void ok(AnsonResp resp)
					throws IOException, AnsonException {
				SyncDoc doc = ((DocsResp) resp).doc; 

//				try {
//					doclient.tempRoot("synode.kharkiv")
//							.login(Kharkiv.Synode.worker, Kharkiv.Synode.nodeId, Kharkiv.Synode.passwd)
//							.blockSize(bsize);
//				} catch (SsException e1) {
//					e1.printStackTrace();
//					fail(e1.getMessage());
//				} catch (TransException e) {
//					e.printStackTrace();
//				}

				// pushing again should fail
				// List<DocsResp> resps2 = null;
				@SuppressWarnings("unused")
				DocsResp resp2 = null;
				try {

					resp2 = clientier.startPush(docm.tbl, doc,
					new OnOk() {
						@Override
						public void ok(AnsonResp resp)
								throws IOException, AnsonException {
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

		DocsResp rp = clientier.selectDoc(docm.tbl, docId);

		assertTrue(isblank(rp.msg()));
		assertEquals(AnDevice.device, rp.doc.device());
		assertEquals(AnDevice.localFile, rp.doc.fullpath());

		return AnDevice.localFile;
	}

	@Test
	void testSynDel() {
		fail("Not yet implemented");
	}

	@Test
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
			public void ok(AnsonResp resp) throws IOException, AnsonException, SemanticException {
			}
		});
	}

}
