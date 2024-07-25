package io.oz.jserv.docs.syn;

import static io.odysz.semantic.meta.SemanticTableMeta.setupSqliTables;
import static io.odysz.semantic.syn.ExessionAct.close;
import static io.odysz.semantic.syn.Docheck.ck;
import static io.odysz.semantic.syn.Docheck.printChangeLines;
import static io.odysz.semantic.syn.Docheck.printNyquv;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.io_odysz.FilenameUtils;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Configs;
import io.odysz.common.DateFormat;
import io.odysz.common.Utils;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.x.SsException;
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
import io.odysz.semantic.syn.ExpSyncDoc;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.tier.docs.DocUtils;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docsync.ZSUNodes.AnDevice;

/**
 * 4 Syntiers running on a static DA helper, but communicate over
 * Semnatic.jserv protocol layer.
 * 
 * @author odys-z@github.com
 */
class SynoderTest {
	public static final String clientUri = "/jnode";
	public static final String webRoot = "./src/test/res/WEB-INF";
	public static final String testDir = "./src/test/res/";
	public static final String volumeDir = "./src/test/res/volume";

	static final String uri64 = "iVBORw0KGgoAAAANSUhEUgAAADwAAAAoCAIAAAAt2Q6oAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH6AYSCBkDT4nw4QAAABl0RVh0Q29tbWVudABDcmVhdGVkIHdpdGggR0lNUFeBDhcAAABjSURBVFjD7dXBCYAwEATAO7FE27QNu7GFxA424EN8zH6XwHAEtus4K2SO2M7Udsd2e93Gl38NNDQ0NPS/sy82LydvXs5ia4fvAQ0NDQ39Zfq+XBoaGhoaGhoaGhoaGhq6qqoeVmUNAc7sDO0AAAAASUVORK5CYII=";
	static final int U = 0;
	static final int V = 1;
	static final int _8080 = 8090;
	static final String IP = "127.0.0.1";

	static ErrorCtx errLog;

	static T_PhotoMeta docm;
	
	static String passwd = "abc";
	static String zsu = "zsu";
	static String ura = "URA";
	
	static final int X = 0;
	static final int Y = 1;
	static final int Z = 2;
	static final int W = 3;
	
	static Doclientier[] doctiers = new Doclientier[2];
	static Syntier[] syntiers  = new Syntier[4];
	static String[] jservs;
	
	private static AutoSeqMeta aum;
	private static SynChangeMeta chm;
	private static SynSubsMeta sbm;
	private static SynchangeBuffMeta xbm;
	private static SynSessionMeta ssm;
	private static PeersMeta prm;
	
	static {
		try {
			Path currentRelativePath = Paths.get("");
			String p = currentRelativePath.toAbsolutePath().toString();
			System.setProperty("VOLUME_HOME", FilenameUtils.concat(p, volumeDir));

			// Test with docker?
			String wwwinf = FilenameUtils.concat(p, webRoot);
			Configs.init(wwwinf);
			Connects.init(wwwinf);
			AnsonMsg.understandPorts(Port.docsync);

			aum = new AutoSeqMeta();
			
			chm = new SynChangeMeta();
			sbm = new SynSubsMeta(chm);
			xbm = new SynchangeBuffMeta(chm);
			ssm = new SynSessionMeta();
			prm = new PeersMeta();
			
			jservs = new String[4];
			for (int s = 0; s < syntiers.length; s++) {
				String conn = "no-jserv.0" + s;

				SynodeMeta snm = new SynodeMeta(conn);
				docm = new T_PhotoMeta(conn); // .replace();
				setupSqliTables(conn, aum, snm, chm, sbm, xbm, prm, ssm, docm);
				
				ArrayList<String> sqls = new ArrayList<String>();
				sqls.add(String.format("delete from %s;", aum.tbl));
				sqls.add(Utils.loadTxt("./oz_autoseq.sql"));
				sqls.add(String.format( "update oz_autoseq set seq = %d where sid = '%s.%s'",
										(long) Math.pow(64, s+1), docm.tbl, docm.pk));

				sqls.add(String.format("delete from %s", snm.tbl));

				Connects.commit(conn, DATranscxt.dummyUser(), sqls);

				
				String synode = String.valueOf((char)(Integer.valueOf('X') + (s == W ? -1 : s)));

				syntiers[s] = new Syntier(synode, conn); // .born(conn, 0, 0, ura);
				jservs[s]   = "http://" + IP + ":" + (_8080 + s) + "/docsync.jserv";
			}

			errLog = new ErrorCtx() {
				@Override
				public void err(MsgCode code, String msg, String...args) {
					fail(msg);
				}
			};
		} catch (TransException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	@Test
	void testSyntiers() throws Exception {
		int no = 0;
		setupeers(++no);
		// uploadocs(++no);
		savephotos(++no);
		syncpeers(++no);
	}

	void setupeers(int test) throws Exception {
		Utils.logrst("setupeers()", test);

		int no = 0;
		Utils.logrst("X starting", test, ++no);
		Syntier xtier = syntiers[X];
		Synoder x = xtier.start(ura, zsu, xtier.myconn, SynodeMode.peer);

		ck[X] = new Docheck(zsu, x.myconn, x.synode, SynodeMode.peer, docm);
		ck[X].synodes(X);

		Utils.logrst("Y starting", test, ++no);
		Syntier ytier = syntiers[Y];
		Synoder y = ytier.start(ura, zsu, ytier.myconn, SynodeMode.peer);
		ck[Y] = new Docheck(zsu, y.myconn, y.synode, SynodeMode.peer, docm);
		ck[Y].synodes(-1, Y);

		printChangeLines(ck);
		printNyquv(ck);

		Utils.logrst("X is joining by Y", test, ++no);
		joinby(X, Y, test, no);
		ck[X].synodes(X, Y);
		ck[Y].synodes(X, Y);

		printChangeLines(ck);
		printNyquv(ck);

		//
		Utils.logrst("Z starting", test, ++no);

		printChangeLines(ck);
		printNyquv(ck);

		Syntier ztier = syntiers[Z];
		Synoder z = syntiers[Z].start(ura, zsu, ztier.myconn, SynodeMode.peer);
		ck[Z] = new Docheck(zsu, z.myconn, z.synode, SynodeMode.peer, docm);

		Utils.logrst("X is joining by Z", test, ++no);
		joinby(X, Z, test, no);

		ck[X].synodes(X, Y, Z);
		// X dosen't have chang-log of Y for Z as Z joined latter
		ck[Y].synodes(X, Y, -1);
		ck[Z].synodes(X, -1, Z);

		Utils.logrst("signing up sessions closed", test, ++no);

		printChangeLines(ck);
		printNyquv(ck);
		
		Utils.logrst("X <= Y", test, ++no);
		syncpair(zsu, X, Y, test, no);
		ck[X].synodes(X, Y, Z);
		ck[Y].synodes(X, Y, Z);
		ck[Z].synodes(X, -1, Z);
	}
	
	void joinby(int at, int by, int test, int sub) throws Exception {

		int no = 0;
		Synoder y = syntiers[by].synoder(zsu);
		Synoder x = syntiers[at].synoder(zsu);

		SyncReq req = y.joinpeer(jservs[X], x.synode, passwd);
		
		Utils.logrst("A on B joining", test, sub, ++no);
		SyncResp rep = x.onjoin(req);

		assertEquals(x.nyquence(y.synode).n, y.n0(x.synode).n);

		Utils.logrst("A answering to B", test, no, 1);
		rep.exblock.print(System.out);

		Utils.logrst("B close joining", test, ++no);
		req = y.closejoin(rep);

		rep = x.onclosejoin(req);
	}

	void savephotos(int test) throws SQLException, SAXException, IOException, TransException {
		Utils.logrst("savephotos()", test);

		int no = 0;
		
		String[] pids = new String[] {
				"  X:", createPhoto(X),
				", Y:", createPhoto(Y)
			};
		
		Utils.logrst(pids, test, ++no);
		printChangeLines(ck);
		printNyquv(ck);
	}

	private String createPhoto(int synx) throws IOException, TransException, SQLException, SAXException {
		Syntier syntier = syntiers[synx];
		T_Photo photo = new T_Photo(docm, zsu, syntier.synode);

		photo.createDate = DateFormat.format(new Date());
		photo.pname = "photo-" + synx;
		photo.fullpath(syntier.synode + ":/sdcard/" + photo.pname);
		photo.uri = uri64; // accepting new value
		photo.folder(syntier.synode);
		photo.share("ody-" + syntier.synode, Share.pub, new Date());

		return DocUtils.createFileBy64(syntier.doctrb(), syntier.myconn,
				(ExpSyncDoc)photo, syntier.locrobot(), (ExpDocTableMeta)docm, null);
	}

	/**
	 * @deprecated needs to setup jserv
	 * @param no
	 * @throws AnsonException
	 * @throws TransException
	 * @throws IOException
	 * @throws SsException
	 * @throws InterruptedException
	 */
	void uploadocs(int no) throws AnsonException, TransException, IOException, SsException, InterruptedException {
		Doclientier u = doctiers[U];
		u.login(u.robot.uid(), u.robot.deviceId(), passwd);
		Doclientier v = doctiers[V];
		v.login(v.robot.uid(), v.robot.deviceId(), passwd);
		
		boolean[] green = new boolean[2];
		List<SyncDoc> p = null;
		u.syncUp(docm.tbl, p, null, (resp) -> {
			green[U] = true;
		});

		v.syncUp(docm.tbl, p, null, (resp) -> {
			green[V] = true;
		});
		
		awaitAll(green);
	}
	
	void syncpeers(int test) throws SQLException, TransException, SAXException, IOException {
		Utils.logrst("syncpeers()", test);
		int no = 0;
		
		Synoder x = syntiers[X].synoder(zsu);
		Synoder y = syntiers[Y].synoder(zsu);
		
		Utils.logrst("X sync by Y", test, ++no);
		syncpair(zsu, X, Y, test, no);
		printChangeLines(ck);
		printNyquv(ck);

		assertEquals(1, x.trb().entities(docm));

		Utils.logrst("X sync by Z", test, ++no);
		Synoder z = syntiers[Z].synoder(zsu);
		syncpair(zsu, X, Z, test, no);
		printChangeLines(ck);
		printNyquv(ck);

		assertEquals(2, x.trb().entities(docm));
		assertEquals(2, z.trb().entities(docm));

		Utils.logrst("X sync by Y", test, ++no);
		syncpair(zsu, X, Y, test, no);
		printChangeLines(ck);
		printNyquv(ck);
		assertEquals(2, y.trb().entities(docm));
	}
	
	void syncpair(String domain, int sx, int cx, int testno, int subno)
			throws SQLException, TransException, SAXException, IOException {
		Utils.logrst("syncpair()", testno, subno);
		int no = 0;
		Synoder srv = syntiers[sx].synoder(domain);
		Synoder clt = syntiers[cx].synoder(domain);

		Utils.logrst("client initate", testno, subno, ++no);
		SyncReq req  = clt.syninit(srv.synode, jservs[sx], zsu);

		printChangeLines(ck);
		printNyquv(ck);
		Utils.logrst("server on-initate", testno, subno, ++no);
		SyncResp rep = srv.onsyninit(clt.synode, req.exblock);

		int ex = 0;
		printChangeLines(ck);
		printNyquv(ck);
		Utils.logrst("exchanges", testno, subno, ++no);
		
		if (rep != null)
			clt.onsyninit(srv.synode, rep.exblock);
			while (rep.synact() != close || req.synact() != close) {
				Utils.logrst("client exchange", testno, subno, no, ++ex);
				req = clt.syncdb(srv.synode, rep);
				req.exblock.print(System.out);

				Utils.logrst("server on-exchange", testno, subno, no, ++ex);
				rep = srv.onsyncdb(clt.synode, req);
				rep.exblock.print(System.out);
			}
		
		Utils.logrst("close exchange", testno, subno, ++no);
		req = clt.synclose(zsu, srv.synode, rep);
		srv.onsynclose(zsu, clt.synode, req);

		printChangeLines(ck);
		printNyquv(ck);
	}

	static String videoUpByApp(ExpDocTableMeta meta) throws Exception {
//		int bsize = 72 * 1024;
//		// app is using Synclientier for synchronizing 
//		Doclientier apptier = new Doclientier(clientUri, errLog)
//				.tempRoot("app.kharkiv")
//				.login(AnDevice.userId, AnDevice.device, AnDevice.passwd)
//				.blockSize(bsize);
//
//		apptier.synDel(meta.tbl, AnDevice.device, AnDevice.localFile);
//		
//		SyncDoc doc = (SyncDoc) new SyncDoc()
//					.share(apptier.robot.uid(), Share.pub, new Date())
//					.folder(Kharkiv.folder)
//					.fullpath(AnDevice.localFile);
//		DocsResp resp = apptier.synInsertDoc(meta.tbl, doc, (r) -> { });
//
//		assertNotNull(resp);
//
//		String docId = resp.doc.recId();
//		assertEquals(8, docId.length());
//
//		DocsResp rp = apptier.selectDoc(meta.tbl, docId);
//
//		assertTrue(LangExt.isblank(rp.msg()));
//		assertEquals(AnDevice.device, rp.doc.device());
//		assertEquals(AnDevice.localFile, rp.doc.fullpath());

		return AnDevice.localFile;
	}

	/**
	 * Wait untile all lights turn int green (true).
	 * @param greenlights
	 * @param x100ms default 100 times
	 * @throws InterruptedException
	 */
	static void awaitAll(boolean[] greenlights, int... x100ms) throws InterruptedException {
		int wait = 0;
		int times = (x100ms == null ? 100 : x100ms[0]);
		while (wait++ < times) {
			for (boolean g : greenlights)
				if (!g) Thread.sleep(100);
		}
		
		for (boolean g : greenlights)
			if (!g) fail("Green light");
	}

}
