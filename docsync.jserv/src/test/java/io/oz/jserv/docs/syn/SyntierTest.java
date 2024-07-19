package io.oz.jserv.docs.syn;

import static io.odysz.semantic.meta.SemanticTableMeta.setupSqliTables;
import static io.odysz.semantic.syn.ExessionAct.close;
import static io.odysz.semantic.syn.ExessionAct.ready;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io_odysz.FilenameUtils;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Configs;
import io.odysz.common.DateFormat;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.ext.DocTableMeta.Share;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.meta.AutoSeqMeta;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.meta.PeersMeta;
import io.odysz.semantic.meta.SynChangeMeta;
import io.odysz.semantic.meta.SynSessionMeta;
import io.odysz.semantic.meta.SynSubsMeta;
import io.odysz.semantic.meta.SynchangeBuffMeta;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.tier.docs.DocUtils;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.IUser;
import io.odysz.transact.x.TransException;
import io.oz.album.tier.PhotoRec;
import io.oz.jserv.docs.syn.Doclientier;
import io.oz.jserv.docs.syn.SyncReq;
import io.oz.jserv.docs.syn.SyncResp;
import io.oz.jserv.docs.syn.Syntier;
import io.oz.jserv.docsync.ZSUNodes.AnDevice;
import io.oz.jserv.docsync.ZSUNodes.Kharkiv;

/**
 * 4 Syntiers running on a static DA helper, but communicate over
 * Semnatic.jserv protocol layer.
 * 
 * @author odys-z@github.com
 */
class SyntierTest {
	public static final String clientUri = "/jnode";
	public static final String webRoot = "./src/test/res/WEB-INF";
	public static final String testDir = "./src/test/res/";
	public static final String volumeDir = "./src/test/res/volume";

	static final String uri64 = "iVBORw0KGgoAAAANSUhEUgAAADwAAAAoCAIAAAAt2Q6oAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH6AYSCBkDT4nw4QAAABl0RVh0Q29tbWVudABDcmVhdGVkIHdpdGggR0lNUFeBDhcAAABjSURBVFjD7dXBCYAwEATAO7FE27QNu7GFxA424EN8zH6XwHAEtus4K2SO2M7Udsd2e93Gl38NNDQ0NPS/sy82LydvXs5ia4fvAQ0NDQ39Zfq+XBoaGhoaGhoaGhoaGhq6qqoeVmUNAc7sDO0AAAAASUVORK5CYII=";

	// static String conn;
	static ErrorCtx errLog;

	// static DATranscxt defltSt;
	static T_PhotoMeta docm;
	
	static String passwd = "abc";
	static String domain = "zsu";
	static DATranscxt st;
	
	static final int X = 0;
	static final int Y = 1;
	static final int Z = 2;
	static final int W = 3;
	
	static Syntier[] syntiers  = new Syntier[4];
	static HashMap<String, String[]> jservcons;
	
	
	static final int U = 0;
	static final int V = 1;
	static Doclientier[] doctiers = new Doclientier[2];

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
			// defltSt = new DATranscxt(Connects.defltConn());
			// defltSt = new DBSyntableBuilder(Connects.defltConn(), "n-a", SynodeMode.peer);
			AnsonMsg.understandPorts(Port.docsync);
			// AnSession.init(defltSt);

			aum = new AutoSeqMeta();
			
			chm = new SynChangeMeta();
			sbm = new SynSubsMeta(chm);
			xbm = new SynchangeBuffMeta(chm);
			ssm = new SynSessionMeta();
			prm = new PeersMeta();
			
			jservcons = new HashMap<String, String[]>();
			for (int tx = 0; tx < syntiers.length; tx++) {
				String conn = "no-jserv.0" + tx;

				SynodeMeta snm = new SynodeMeta(conn);
				docm = new T_PhotoMeta(conn); // .replace();
				setupSqliTables(conn, aum, snm, chm, sbm, xbm, prm, ssm, docm);
				
				syntiers[tx] = new Syntier("syn-" + tx); //.regist(docm);
				jservcons.put(syntiers[tx].synode, new String[] {
					"http://127.0.0.1:809" + tx + "/docsync.jserv",
					conn
				});
				
				ArrayList<String> sqls = new ArrayList<String>();
				sqls.add(String.format("delete from %s;", aum.tbl));
				sqls.add(Utils.loadTxt("./oz_autoseq.sql"));
				sqls.add(String.format( "update oz_autoseq set seq = %d where sid = '%s.%s'",
										(long) Math.pow(64, tx+1), docm.tbl, docm.pk));

				sqls.add(String.format("delete from %s", snm.tbl));

				Connects.commit(conn, DATranscxt.dummyUser(), sqls);
			}

			errLog = new ErrorCtx() {
				@Override
				public void err(MsgCode code, String msg, String...args) {
					fail(msg);
				}
			};

			st = new DATranscxt(null);
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

	void setupeers(int no) throws SQLException, TransException, SAXException, IOException {
		Syntier x = syntiers[X].start(domain, SynodeMode.peer);

		Syntier y = syntiers[Y].start(domain, SynodeMode.peer);
		SyncReq req = y.joinpeer(
				jservcons.get(x.synode)[Syntier.jservx],
				jservcons.get(y.synode)[Syntier.myconx],
				x.synode, passwd);
		
		SyncResp rep = x.onjoin(req, jservcons.get(x.synode)[Syntier.myconx]);

		y.closejoin(rep);

		assertEquals(x.nyquence(domain, y).n, y.n0(domain).n);

		//
		Syntier z = syntiers[Z].start(domain, SynodeMode.peer);
		req = z.joinpeer(
				jservcons.get(x.synode)[Syntier.jservx],
				jservcons.get(z.synode)[Syntier.myconx],
				x.synode, passwd);
		
		rep = x.onjoin(req, jservcons.get(x.synode)[Syntier.myconx]);

		z.closejoin(rep);

		assertEquals(x.nyquence(domain, z).n, z.n0(domain).n);
	}
	
	void savephotos(int no) throws SQLException, SAXException, IOException, TransException {
		int sect = 0;
		Utils.logrst("Insert pohotos", no, ++sect);
		
		String[] pids = new String[] {
				createPhoto(X),
				createPhoto(Y)
			};
		
		Utils.logrst(pids, no, ++sect);
	}

	private String createPhoto(int synx) throws IOException, TransException, SQLException {
		PhotoRec photo = new PhotoRec();

		photo.createDate = DateFormat.format(new Date());
		photo.pname = "photo-" + synx;
		photo.fullpath(syntiers[0].synode + ":/sdcard/" + photo.pname);
		photo.uri = uri64; // accepting new value
		IUser robot = syntiers[0].trb(domain).synrobot();

		String conn = jservcons.get(syntiers[synx].synode)[Syntier.myconx];
		return DocUtils.createFileB64(st, conn, photo, robot, (ExpDocTableMeta)docm, null);
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
		
		await10s(green);
	}
	
	void syncpeers(int no) throws SQLException, TransException, SAXException, IOException {
		Syntier x = syntiers[X];
		Syntier y = syntiers[Y];
		
		syncpeer(x, y);

		assertEquals(1, x.trb(domain).entities(docm));

		Syntier z = syntiers[Z];
		syncpeer(x, z);

		assertEquals(2, x.trb(domain).entities(docm));
		assertEquals(2, z.trb(domain).entities(docm));

		syncpeer(x, y);
		assertEquals(2, y.trb(domain).entities(docm));
	}
	
	void syncpeer(Syntier s, Syntier c) throws SQLException, TransException, SAXException, IOException {
		String sconn = jservcons.get(s.synode)[Syntier.myconx];
		SyncReq req  = c.syninit(s.synode,
				jservcons.get(s.synode)[Syntier.jservx],
				sconn, domain);

		String cconn = jservcons.get(c.synode)[Syntier.myconx];
		SyncResp rep = s.onsyninit(c.synode, cconn, req);

		while (rep.synact() != close && req.synact() != ready) {
			req = c.syncdb(domain, s.synode, rep);
			rep = c.onsyncdb(domain, c.synode, req);
		}
		
		req = c.synclose(domain, s.synode, rep);
		c.onsynclose(domain, s.synode, req);
	}

	static String videoUpByApp(DocTableMeta meta) throws Exception {
		int bsize = 72 * 1024;

		// app is using Synclientier for synchronizing 
		Doclientier apptier = new Doclientier(clientUri, errLog)
				.tempRoot("app.kharkiv")
				.login(AnDevice.userId, AnDevice.device, AnDevice.passwd)
				.blockSize(bsize);

		apptier.synDel(meta.tbl, AnDevice.device, AnDevice.localFile);
		
		SyncDoc doc = (SyncDoc) new SyncDoc()
					.share(apptier.robot.uid(), Share.pub, new Date())
					.folder(Kharkiv.folder)
					.fullpath(AnDevice.localFile);
		DocsResp resp = apptier.synInsertDoc(meta.tbl, doc, (r) -> { });

		assertNotNull(resp);

		String docId = resp.doc.recId();
		assertEquals(8, docId.length());

		DocsResp rp = apptier.selectDoc(meta.tbl, docId);

		assertTrue(LangExt.isblank(rp.msg()));
		assertEquals(AnDevice.device, rp.doc.device());
		assertEquals(AnDevice.localFile, rp.doc.fullpath());

		return AnDevice.localFile;
	}

	static void await10s(boolean[] green) throws InterruptedException {
		int wait = 0;
		while (wait++ < 100) {
			for (boolean g : green)
				if (!g) Thread.sleep(100);
		}
		
		for (boolean g : green)
			if (!g) fail("Green light");
	}

}
