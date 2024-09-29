package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.LangExt.prefix;
import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.logT;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.pause;
import static io.odysz.common.Utils.waiting;
import static io.oz.jserv.docs.syn.Dev.X_0;
import static io.oz.jserv.docs.syn.Dev.Y_0;
import static io.oz.jserv.docs.syn.Dev.Y_1;
import static io.oz.jserv.docs.syn.Dev.bsize;
import static io.oz.jserv.docs.syn.Dev.devs;
import static io.oz.jserv.docs.syn.Dev.docm;
import static io.oz.jserv.docs.syn.ExpDoctierservTest.X;
import static io.oz.jserv.docs.syn.ExpDoctierservTest.Y;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.errLog;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;
import java.util.HashSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.JProtocol.OnOk;
import io.odysz.semantic.meta.ExpDocTableMeta.Share;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.PathsPage;
import io.odysz.transact.x.TransException;
import io.oz.synode.jclient.YellowPages;

class DoclientierTest {
	static String[] jserv_xyzw;
	@BeforeAll
	static void init() throws Exception {
		String p = new File("src/test/res").getAbsolutePath();
    	System.setProperty("VOLUME_HOME", p + "/volume");
    	logi("VOLUME_HOME : %s", System.getProperty("VOLUME_HOME"));

		YellowPages.load("$VOLUME_HOME");
		jserv_xyzw = prefix(System.getProperty("jservs").split(","), "http://");
	}

	@Test
	void testSynclientUp() throws Exception {
		ExpSyncDoc dx = clientPush(X, X_0);
		verifyPathsPage(devs[X_0].client, docm.tbl, dx.clientpath);

		// 10 create
		clientPush(Y, Y_0);

		// 11 create
		clientPush(Y, Y_1);
	}

	@Test
	@Disabled
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

			// ck[Y].doc(3);
			// ck[X].doc(3);
	
			// 00 delete
			Dev devx0 = devs[X_0];
			Clients.init(jserv_xyzw[X]);
			DocsResp rep = devx0.client.synDel(docm.tbl, devx0.dev, devx0.res);
			assertEquals(1, rep.total(0));
	
			// verifyPathsPageNegative(devx0.client, docm.tbl, dx.clientpath);

			waiting(lights, Y);
			SynodetierJoinTest.syncdomain(lights, Y);
			awaitAll(lights);
			// ck[Y].doc(2);
			// ck[X].doc(2);
	
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
		logi("Pushes are closed.");
	}

	ExpSyncDoc clientPush(int to, int cix) throws Exception {
		Dev dev = devs[cix];

		Clients.init(jserv_xyzw[to]);

		Doclientier client = new Doclientier(dev.uri, errLog)
				.tempRoot(dev.uri)
				.loginWithUri(dev.uri, dev.uid, dev.dev, dev.psw)
				.blockSize(bsize);
		dev.client = client;
		
		Utils.logi("-------------- client pushing: uid %s, device %s",
				client.client.ssInfo().uid(), client.client.ssInfo().device);

		ExpSyncDoc xdoc = videoUpByApp(dev, client, docm.tbl);
		assertEquals(dev.dev, xdoc.device());
		assertEquals(dev.res, xdoc.fullpath());

		verifyPathsPage(client, docm.tbl, xdoc.clientpath);
		return xdoc;
	}

 	static ExpSyncDoc videoUpByApp(Dev atdev, Doclientier doclient,
 			String entityName) throws Exception {

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
								Utils.logi("Pushing again failed test passed. doc: %s, device: %s, clientpath: %s",
									doc.recId, doc.device(), doc.clientpath);
							}
						});
				} catch (TransException | IOException | SQLException e) {
					e.printStackTrace();
					// fail(e.getMessage());
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
	static void verifyPathsPage(Doclientier clientier, String entityName,
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
		assertEquals(len(paths), pthpage.paths().size());

		for (String pth : paths)
			pathpool.remove(pth);

		assertEquals(0, pathpool.size());
	}


}
