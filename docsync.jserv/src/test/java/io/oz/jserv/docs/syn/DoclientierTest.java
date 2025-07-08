package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.LangExt.musteqi;
import static io.odysz.common.LangExt.mustnonull;
import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.logT;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.logrst;
import static io.odysz.common.Utils.pause;
import static io.odysz.common.Utils.turngreen;
import static io.odysz.common.Utils.waiting;
import static io.odysz.common.Utils.warn;
import static io.oz.jserv.docs.syn.Dev.X_0;
import static io.oz.jserv.docs.syn.Dev.Y_0;
import static io.oz.jserv.docs.syn.Dev.Y_1;
import static io.oz.jserv.docs.syn.Dev.devs;
import static io.oz.jserv.docs.syn.Dev.docm;
import static io.oz.jserv.docs.syn.singleton.ExpDoctierservTest.X;
import static io.oz.jserv.docs.syn.singleton.ExpDoctierservTest.Y;
import static io.oz.jserv.docs.syn.singleton.ExpDoctierservTest.ck;
import static io.odysz.semantic.syn.Docheck.printChangeLines;
import static io.odysz.semantic.syn.Docheck.printNyquv;
import static io.oz.jserv.docs.syn.SynodetierJoinTest.errLog;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.odysz.anson.AnsonException;
import io.odysz.jclient.Clients;
import io.odysz.jclient.syn.Doclientier;
import io.odysz.jclient.syn.IFileProvider;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.JProtocol.OnOk;
import io.odysz.semantic.tier.docs.Device;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.PathsPage;
import io.odysz.semantic.tier.docs.ShareFlag;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.singleton.ExpDoctierservTest;
import io.oz.syn.YellowPages;

/**
 */
class DoclientierTest {
	
	static String[] jserv_xyzw;

	private static Thread thr;

	/** Stop service node quit automatically. */
	static final boolean[] serviceLight = new boolean[1];
	
	/** Permission to push docs by clients, this test. */
	static final boolean[] canpush = new boolean[] {false};

	/** Tell service node pushings are done. */
	static final boolean[] pushingDone  = new boolean[] {false};

	@BeforeAll
	static void init() throws Exception {
		init(ExpDoctierservTest.case_yresolve);
	}
	
	static void init(int caseid) throws Exception {
		AnsonMsg.understandPorts(AnsonMsg.Port.echo);

		String p = new File("src/test/res").getAbsolutePath();
    	System.setProperty("VOLUME_HOME", p + "/volume");
    	logi("VOLUME_HOME : %s", System.getProperty("VOLUME_HOME"));

		YellowPages.load("$VOLUME_HOME");

		ExpDoctierservTest.init();

		logrst("[DoclientierTest] Starting synode-tiers", 0);
		int[] nodex = ExpDoctierservTest.startJetties(SynodetierJoinTest.jetties, ck);
		//must finished
		musteqi(4, len(SynodetierJoinTest.jetties));
		mustnonull(SynodetierJoinTest.jetties[0]);

		// Wait for ExpDoctierservTest.runDoctiers() check the initial state
		waiting(serviceLight, -1);

		thr = new Thread(() -> {
			try {
				ExpDoctierservTest.runDoctiers(caseid, nodex, serviceLight, canpush, pushingDone);
			} catch (InterruptedException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}, "X Y Z W by DoclientierTest");
		thr.start();
		
		jserv_xyzw = ExpDoctierservTest.jservs();
		
		SynssionPeer.testDisableAutoDocRef = true;
	}

	@Test
	void testSynclientUp() throws Exception {
		synclientUp();
	}
	static void synclientUp() throws Exception {
		int no = 0;
		logrst("testSynclientUp: waiting pushing permission", ++no);

		awaitAll(canpush, 5 * 60 * 10); // mvn test on 200 can be this slow?

		logrst(f("X <- %s", devs[X_0].device.id), ++no);

		// 10 create X
		printChangeLines(ck);
		printNyquv(ck);
		ExpSyncDoc dx = clientPush(X, X_0);
		verifyPathsPage(devs[X_0].client, docm.tbl, dx.clientpath);

		// 10 create Y
		printChangeLines(ck);
		printNyquv(ck);
		logrst(f("Y <- %s", devs[Y_0].device.id), ++no);
		clientPush(Y, Y_0);

		// 11 create Y
		printChangeLines(ck);
		printNyquv(ck);
		logrst(f("Y <- %s", devs[X_0].device.id), ++no);
		clientPush(Y, Y_1);

		turngreen(pushingDone);
		
		logrst("Waiting server ending...", ++no);
		thr.join();

		printChangeLines(ck);
		printNyquv(ck);
		ck[Y].doc(2);
		ck[X].doc(2);
	}

	@Test
	@Disabled
	void testSyncUp() throws Exception {
		ExpSyncDoc dx = clientPush(X, X_0);
		verifyPathsPage(devs[X_0].client, docm.tbl, dx.clientpath);

		// 10 create
		clientPush(Y, Y_0);

		// 11 create
		clientPush(Y, Y_1);

//		boolean[] lights = new boolean[] {true, false};
//		SynodetierJoinTest.syncdomain(lights, Y);
//		awaitAll(lights, -1);
		SynodetierJoinTest.syncdomain(Y);

		// 00 delete
		Dev devx0 = devs[X_0];
		Clients.init(jserv_xyzw[X]);
		DocsResp rep = devx0.client.synDel(docm.tbl, devx0.device.id, devx0.res);
		assertEquals(1, rep.total(0));

//		waiting(lights, Y);
//		SynodetierJoinTest.syncdomain(lights, Y);
//		awaitAll(lights);
		SynodetierJoinTest.syncdomain(Y);

		pause("Press enter to quite ...");
	}

	@AfterAll
	static void close() throws Exception {
		logi("Pushes are closed.");
		logi("Closing service...");
		serviceLight[0] = true;
		thr.join();
	}

	static ExpSyncDoc clientPush(int to, int cix) throws Exception {
		Dev dev = devs[cix];

		Clients.init(jserv_xyzw[to]);

		dev.login(errLog);

		dev.client.fileProvider(new IFileProvider() {});

		logi("client pushing: uid %s, device %s",
				dev.client.client.ssInfo().uid(), dev.client.client.ssInfo().device);
		logi(dev.res);

		ExpSyncDoc xdoc = videoUpByApp(dev.client, dev.device, dev.res, docm.tbl, ShareFlag.publish);
		assertEquals(dev.device.id, xdoc.device());
		assertEquals(dev.res, xdoc.fullpath());

		verifyPathsPage(dev.client, docm.tbl, xdoc.clientpath);
		return xdoc;
	}

	static ExpSyncDoc videoUpByApp(Doclientier doclient, Device atdev, String respath,
 			String entityName, ShareFlag share) throws Exception {

		ExpSyncDoc xdoc = Doclientier.videoUpByApp(doclient, atdev, respath, entityName, share,
			(AnsonResp rep) -> {
				ExpSyncDoc doc = ((DocsResp) rep).xdoc; 

				// push again should fail
				try {
					warn("******** ********\n*** Can safely ignore the following file's duplicating error:");
					doclient.startPush(null, entityName, doc,
						new OnOk() {
							@Override
							public void ok(AnsonResp rep)
									throws IOException, AnsonException {
								if (rep != null) {
									logT(new Object() {}, rep.msg());
									fail("Double checking failed.");
								}
								else logi("No docs pushed, which is expected.");
							}
						},
						null,
						new ErrorCtx() {
							@Override
							public void err(MsgCode code, String msg, String...args) {
								warn("There should be some error message from server.");
								logi("Expected: Fail on pushing again test passed. doc: %s, device: %s, clientpath: %s",
									doc.recId, doc.device(), doc.clientpath);
								try {
									// avoid existing without error logs.
									Thread.sleep(200);
								} catch (InterruptedException e) { }
							}
						});
				} catch (TransException | IOException | SQLException e) {
					e.printStackTrace();
					fail(e.getMessage());
				}
			}, null);

		assertNotNull(xdoc);

		String docId = xdoc.recId();
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

		DocsResp rep = clientier.synQueryPathsPage(pths, Port.docstier);

		PathsPage pthpage = rep.pathsPage();

		assertEquals(clientier.client.ssInfo().device, pthpage.device);
		assertEquals(len(paths), pthpage.paths().size());

		for (String pth : paths)
			pathpool.remove(pth);

		assertEquals(0, pathpool.size());
	}


}
