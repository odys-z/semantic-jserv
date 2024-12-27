package io.oz.syntier.serv;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.LangExt.prefix;
import static io.odysz.common.Utils.logT;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.pause;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.syn.Doclientier;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.JProtocol.OnOk;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.tier.docs.Device;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.PathsPage;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.album.peer.PhotoMeta;
import io.oz.album.peer.ShareFlag;

class Deviclientx {
	
	static class Dev {
		static int bsize = 12 * 4096;

		public final String sysuri;
		public final String synuri;

		public final String uid;
		public final String psw;
		public final Device device;

		public String res;
		public Doclientier client;

		Dev(String uid, String pswd, String folder, String fres) {
			sysuri = "/album/sys/x";
			synuri = "/album/syn/x";
			this.uid = uid;
			this.psw = pswd;
			this.res = fres;
			this.device = new Device("x-00", "x-00", "Deviclient x-00")
					.folder(folder);
		}

		
		public void login(ErrorCtx errLog) throws SemanticException, AnsonException, SsException, IOException {
			client = new Doclientier(docm.tbl, sysuri, synuri, errLog)
					.tempRoot(sysuri)
					.loginWithUri(uid, device.id, psw)
					.blockSize(bsize);
		}
	}

	static int X = 0;
	static int Y = 1;
	static String[] jserv_xyzw;

	static ExpDocTableMeta docm;
	static ErrorCtx errLog;
	static Dev dev;
	
	static {
		try {
			docm = new PhotoMeta(null);
			
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
		AnsonMsg.understandPorts(AnsonMsg.Port.echo);

		// -Djservs="ip-1:port-1,ip-2:port-2"
		jserv_xyzw = prefix(System.getProperty("jservs").split(","), "http://");
		
		dev = new Dev("ody", "8964", "dev-x-00", "src/test/resources/182x121.png");
	}

	@Test
	void testSyncUp() throws Exception {
		ExpSyncDoc dx = clientPush(X);
		verifyPathsPage(dev.client, docm.tbl, dx.clientpath);

		pause("Paused on pushing Y ...");

		// 10 create
		clientPush(Y);

		// 11 create
		clientPush(Y);


		// 00 delete
		pause("Paused on deleting at X ...");
		Clients.init(jserv_xyzw[X]);
		DocsResp rep = dev.client.synDel(docm.tbl, dev.device.id, dev.res);
		assertEquals(1, rep.total(0));

		verifyPathsPageNegative(dev.client, docm.tbl, dx.clientpath);

		// ck[Y].doc(2);
		// ck[X].doc(2);

		pause("Press enter to quite ...");
	}

	@AfterAll
	static void close() throws Exception {
		logi("Pushes are closed.");
	}

	ExpSyncDoc clientPush(int to) throws Exception {
		Clients.init(jserv_xyzw[to]);

		dev.login(errLog);
		Utils.logi("client pushing: uid %s, device %s",
				dev.client.client.ssInfo().uid(), dev.client.client.ssInfo().device);
		Utils.logi(dev.res);

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
					doclient.startPush(null, entityName, doc,
						new OnOk() {
							@Override
							public void ok(AnsonResp rep)
									throws IOException, AnsonException {
								if (rep != null) {
									logT(new Object() {}, rep.msg());
									fail("Double checking failed.");
								}
								else Utils.logi("No docs pushed, which is expected.");
							}
						},
						null,
						new ErrorCtx() {
							@Override
							public void err(MsgCode code, String msg, String...args) {
								Utils.warn("There should be some error message from server.");
								Utils.logi("Expected: Fail on pushing again test passed. doc: %s, device: %s, clientpath: %s",
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

		DocsResp rep = clientier.synQueryPathsPage(pths, Port.docsync);

		PathsPage pthpage = rep.pathsPage();

		assertEquals(clientier.client.ssInfo().device, pthpage.device);
		assertEquals(len(paths), pthpage.paths().size());

		for (String pth : paths)
			pathpool.remove(pth);

		assertEquals(0, pathpool.size());
	}

	void verifyPathsPageNegative(Doclientier client, String tbl, String clientpath) {
	}
}
