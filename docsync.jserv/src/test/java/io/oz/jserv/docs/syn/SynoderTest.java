package io.oz.jserv.docs.syn;

import static io.odysz.semantic.meta.SemanticTableMeta.setupSqliTables;
import static io.odysz.semantic.syn.ExessionPersist.loadNyquvect;
import static io.odysz.semantic.syn.Docheck.ck;
import static io.odysz.semantic.syn.Docheck.printChangeLines;
import static io.odysz.semantic.syn.Docheck.printNyquv;
import static io.odysz.semantic.syn.Docheck.pushDebug;
import static io.odysz.semantic.syn.ExessionAct.close;
import static io.oz.jserv.test.JettyHelperTest.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;

import org.apache.commons.io_odysz.FilenameUtils;
import org.junit.jupiter.api.Test;

import io.odysz.common.Configs;
import io.odysz.common.DateFormat;
import io.odysz.common.Utils;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
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
import io.odysz.semantic.syn.IAssert;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.tier.docs.DocUtils;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.AssertImpl;

/**
 * The synchronizing tiers running on a static DA helper, but communicating
 * over the Semnatic.jserv protocol layer.
 * 
 * @author odys-z@github.com
 */
class SynoderTest {
	static final String _uri64 = "iVBORw0KGgoAAAANSUhEUgAAADwAAAAoCAIAAAAt2Q6oAAAACXBIWXMAAAsTAAALEwEAmpwYAAAAB3RJTUUH6AYSCBkDT4nw4QAAABl0RVh0Q29tbWVudABDcmVhdGVkIHdpdGggR0lNUFeBDhcAAABjSURBVFjD7dXBCYAwEATAO7FE27QNu7GFxA424EN8zH6XwHAEtus4K2SO2M7Udsd2e93Gl38NNDQ0NPS/sy82LydvXs5ia4fvAQ0NDQ39Zfq+XBoaGhoaGhoaGhoaGhq6qqoeVmUNAc7sDO0AAAAASUVORK5CYII=";
	static final int U = 0;
	static final int V = 1;

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
	
	static Syntier[] syntiers  = new Syntier[4];
	
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
			String wwwinf = FilenameUtils.concat(p, webinf);
			Configs.init(wwwinf);
			Connects.init(wwwinf);
			AnsonMsg.understandPorts(Port.docsync);

			aum = new AutoSeqMeta();
			
			chm = new SynChangeMeta();
			sbm = new SynSubsMeta(chm);
			xbm = new SynchangeBuffMeta(chm);
			ssm = new SynSessionMeta();
			prm = new PeersMeta();
			
			for (int s = 0; s < syntiers.length; s++) {
				String conn = "no-jserv.0" + s;

				SynodeMeta snm = new SynodeMeta(conn);
				docm = new T_PhotoMeta(conn); // .replace();
				setupSqliTables(conn, aum, snm, chm, sbm, xbm, prm, ssm, docm);
				
				ArrayList<String> sqls = new ArrayList<String>();
				sqls.add(String.format("delete from %s;", aum.tbl));
				sqls.add(Utils.loadTxt("./oz_autoseq.sql"));
				sqls.add(String.format("update oz_autoseq set seq = %d where sid = '%s.%s'",
										(long) Math.pow(64, s+1), docm.tbl, docm.pk));

				sqls.add(String.format("delete from %s", snm.tbl));

				Connects.commit(conn, DATranscxt.dummyUser(), sqls);

				// X, Y, Z, W
				String synode = String.valueOf((char)(Integer.valueOf('X') + (s == W ? -1 : s)));

				syntiers[s] = new Syntier(synode, conn);
			}

			errLog = new ErrorCtx() {
				@Override
				public void err(MsgCode code, String msg, String...args) {
					fail(msg);
				}
			};
		} catch (TransException | SQLException | IOException e) {
			e.printStackTrace();
		}
	}

	static IAssert azert = new AssertImpl();

	@Test
	void testSynoders() throws Exception {
		int no = 0;
		setupeers(++no);
		savephotos(++no);
		syncpeers(++no);
	}

	/**
	 * <ol>
	 * <li>x start, y start</li>
	 * <li>x accept y, no propagaion for z</li>
	 * <li>x accept z, with propagation to y</li>
	 * <li>x, y exchange, and x, y know z; z dosen't know y</li>
	 * </ol>
	 * Results:<br>
	 * x, y know each others
	 * y knows z, 
	 * z don't know y
	 * <pre>
	 * FIXME z should know both x, y when signed up.
	 * 
	 *                   X                 |                  Y                 |                  Z                 
	 * ------------------------------------+------------------------------------+------------------------------------
	 * 
	 *       X    Y    Z
	 * X [   3,   2,   1 ]
	 * Y [   3,   3,   1 ]
	 * Z [   1,    ,   2 ]</pre>
	 * @param test
	 * @throws Exception
	 */
	void setupeers(int test) throws Exception {
		Utils.logrst("setupeers()", test);

		int no = 0;
		Utils.logrst("X starting", test, ++no);
		Syntier xtir = syntiers[X];
		SynDomanager x = xtir.start(ura, zsu, xtir.myconn, SynodeMode.peer)
						.domanager(zsu);

		ck[X] = new Docheck(azert, zsu, x.myconn, x.synode, SynodeMode.peer, docm);
		ck[X].synodes(X);

		Utils.logrst("Y starting", test, ++no);
		Syntier ytir = syntiers[Y];
		SynDomanager y = ytir.start(ura, zsu, ytir.myconn, SynodeMode.peer)
						.domanager(zsu);

		ck[Y] = new Docheck(azert, zsu, y.myconn, y.synode, SynodeMode.peer, docm);
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

		Syntier ztir = syntiers[Z];
		SynDomanager z = ztir.start(ura, zsu, ztir.myconn, SynodeMode.peer)
						.domanager(zsu);

		ck[Z] = new Docheck(azert, zsu, z.myconn, z.synode, SynodeMode.peer, docm);

		Utils.logrst("X <= join = Z", test, ++no);
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
		SynDomanager y = syntiers[by].domanager(zsu);
		SynDomanager x = syntiers[at].domanager(zsu);

		SyncReq req = y.joinpeer(x.synode, passwd);
		
		Utils.logrst(new String[] {x.synode, "on", y.synode, "joining"}, test, sub, ++no);
		SyncResp rep = x.onjoin(req);

		printChangeLines(ck);
		printNyquv(ck);

		Utils.logrst(new String[] {x.synode, "answer to", y.synode}, test, sub, ++no);
		rep.exblock.print(System.out);

		Utils.logrst(new String[] {y.synode, "close joining"}, test, sub, ++no);
		req = y.closejoin(rep);

		rep = x.onclosejoin(req);
		printChangeLines(ck);
		printNyquv(ck);

		pushDebug()
		.assertl(
			// ck[by].n0().n, loadNyquvect(y.expiredxp.trb).get(x.synode).n + 1,
			ck[by].n0().n, loadNyquvect(y.expiredClientier.xp.trb).get(x.synode).n + 1,
			// ck[at].n0().n, loadNyquvect(x.expiredxp.trb).get(y.synode).n + 1,
			ck[at].n0().n, loadNyquvect(x.expiredClientier.xp.trb).get(y.synode).n + 1,
			ck[at].n0().n, y.lastn0(x.synode).n,
			ck[at].n0().n, ck[by].n0().n)
		.popDebug();
	}

	void savephotos(int test) throws SQLException, IOException, TransException {
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

	private String createPhoto(int synx) throws IOException, TransException, SQLException {
		Syntier syntier = syntiers[synx];
		T_Photo photo = new T_Photo(docm, ura, syntier.synode);

		photo.createDate = DateFormat.format(new Date());
		photo.pname = "photo-" + synx;
		photo.fullpath(syntier.synode + ":/sdcard/" + photo.pname);
		photo.uri64 = _uri64;
		photo.folder(syntier.synode);
		photo.share("ody-" + syntier.synode, Share.pub, new Date());

		return DocUtils.createFileBy64(syntier.stampbuilder(), syntier.myconn,
				(ExpSyncDoc)photo, syntier.locrobot(), (ExpDocTableMeta)docm);
	}

	void syncpeers(int test) throws Exception {
		Utils.logrst("syncpeers()", test);
		int no = 0;
		
		SynDomanager x = syntiers[X].domanager(zsu);
		SynDomanager y = syntiers[Y].domanager(zsu);
		
		Utils.logrst("X sync by Y", test, ++no);
		syncpair(zsu, X, Y, test, no);
		printChangeLines(ck);
		printNyquv(ck);

		assertEquals(2, x.synssion(y.synode).xp.trb.entities(docm));

		Utils.logrst("X sync by Z", test, ++no);
		SynDomanager z = syntiers[Z].domanager(zsu);
		syncpair(zsu, X, Z, test, no);
		printChangeLines(ck);
		printNyquv(ck);

		assertEquals(2, x.synssion(z.synode).xp.trb.entities(docm));
		assertEquals(2, z.synssion(x.synode).xp.trb.entities(docm));

		Utils.logrst("X sync by Y", test, ++no);
		syncpair(zsu, X, Y, test, no);
		printChangeLines(ck);
		printNyquv(ck);

		assertEquals(2, y.synssion(x.synode).xp.trb.entities(docm));
	}
	
	void syncpair(String domain, int sx, int cx, int testno, int subno)
			throws Exception {
		Utils.logrst("syncpair()", testno, subno);
		int no = 0;
		SynDomanager srv = syntiers[sx].domanager(domain);
		SynDomanager clt = syntiers[cx].domanager(domain);

		Utils.logrst("client initate", testno, subno, ++no);
		// SyncReq req  = clt.synssion(srv.synode).syninit();
		SyncReq req  = clt.syninit(srv.synode, domain);

		printChangeLines(ck);
		printNyquv(ck);
		Utils.logrst("server on-initate", testno, subno, ++no);
		SyncResp rep = srv.onsyninit(clt.synode, req.exblock);

		int ex = 0;
		printChangeLines(ck);
		printNyquv(ck);
		Utils.logrst("exchanges", testno, subno, ++no);
		
		if (rep != null) {
			// clt.synssion(srv.synode).onsyninit(rep.exblock);
			clt.onsyninit(srv.synode, rep.exblock);
			while (rep.synact() != close || req.synact() != close) {
				Utils.logrst("client exchange", testno, subno, no, ++ex);
				req = clt.synssion(srv.synode).syncdb(rep);
				req.exblock.print(System.out);

				Utils.logrst("server on-exchange", testno, subno, no, ++ex);
				rep = srv.synssion(clt.synode).onsyncdb(req);
				rep.exblock.print(System.out);
			}
		
			Utils.logrst("close exchange", testno, subno, ++no);
			req = clt.synssion(srv.synode).synclose(rep);
			srv.synssion(clt.synode).onsynclose(req);
		}

		printChangeLines(ck);
		printNyquv(ck);
	}

	/**
	 * Wait until all lights turn into green (true).
	 * @param green lights
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
