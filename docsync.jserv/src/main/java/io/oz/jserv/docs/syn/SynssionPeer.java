package io.oz.jserv.docs.syn;

import static io.odysz.common.AESHelper.encode64;
import static io.odysz.common.AESHelper.getRandom;
import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.ev;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.notNull;
import static io.odysz.semantic.syn.ExessionAct.close;
import static io.odysz.semantic.syn.ExessionAct.deny;
import static io.odysz.semantic.syn.ExessionAct.init;
import static io.odysz.semantic.syn.ExessionAct.ready;
import static io.odysz.semantic.syn.ExessionAct.setupDom;
import static io.odysz.semantic.syn.ExessionAct.trylater;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.HashSet;

import org.apache.commons.io.FileUtils;

import io.odysz.anson.AnsonException;
import io.odysz.common.FilenameUtils;
import io.odysz.common.Utils;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.syn.ExpDocRobot;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.JProtocol.OnError;
import io.odysz.semantic.jprotocol.JProtocol.OnOk;
import io.odysz.semantic.jprotocol.JProtocol.OnProcess;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.meta.DocRef;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.meta.SynDocRefMeta;
import io.odysz.semantic.syn.DBSyntableBuilder;
import io.odysz.semantic.syn.ExchangeBlock;
import io.odysz.semantic.syn.Exchanging;
import io.odysz.semantic.syn.ExessionPersist;
import io.odysz.semantic.syn.SyndomContext.OnMutexLock;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.util.DAHelper;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.ExchangeException;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.parts.AnDbField;
import io.odysz.transact.sql.parts.ExtFilePaths;
import io.odysz.transact.sql.parts.Sql;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.SyncReq.A;

/**
 */
public class SynssionPeer {

	/** /syn/[peer-synode-id] */
	final String uri_syn;
	/** /sys/[peer-synode-id] */
	final String uri_sys;

	/** {@link #uri_syn}/[peer] */
	final String clienturi;

	final String mynid;
	final String conn;
	final String peer;
	public final String peerjserv;

	String domain() {
		return domanager.domain();
	}

	SynodeMode mymode;

	SynDomanager domanager;

	ExessionPersist xp;
	public SynssionPeer xp(ExessionPersist xp) {
		this.xp = xp;
		return this;
	}

	OnError errHandler;
	public SynssionPeer onErr(OnError err) {
		errHandler = err;
		return this;
	}

	/**
	 * This field is also used as a flag of login state - not logged in if null
	 * 
	 * @since 0.2.0
	 */
	protected SessionClient client;

	/** Initialized by {@link io.odysz.semantic.syn.SyndomContext#dbg}, which should be from Config.debug. */
	private boolean debug;

	public SynssionPeer(SynDomanager domanager, String peer, String peerjserv, boolean debug) {
		// ISSUE
		// TODO It's better to change this to /syn/me, not /syn/peer once Connects is refactored. 
		this.uri_syn   = "/syn/" + peer; // domanager.synode;
		this.uri_sys   = "/sys/" + peer; // domanager.synode;

		this.conn      = domanager.synconn;
		this.mynid     = domanager.synode;
		this.domanager = domanager;
		this.peer      = peer;
		this.mymode    = domanager.mode;
		this.peerjserv = peerjserv;
		this.clienturi = uri_sys;
		this.debug     = debug;
	}

	/**
	 * [Synchronous]<br>
	 * Start a domain updating process (handshaking) with this.peer, in this.domain.
	 * @param onMutext 
	 * @return this
	 * @throws ExchangeException not ready yet.
	 * @since 0.2.0
	 */
	public SynssionPeer update2peer(OnMutexLock onMutext) throws ExchangeException {
		if (client == null || isblank(peer) || isblank(domain()))
			throw new ExchangeException(ready, null,
					"Synchronizing information is not ready, or not logged in. From synode %s to peer %s, domain %s%s.",
					domanager.synode, peer, domain(), client == null ? ", client is null" : "");

		SyncResp rep = null;
		try {
			// start session

			if (debug)
				Utils.logi("Locking and starting thread on domain updating: %s : %s -> %s"
						+ "\n=============================================================\n",
						domain(), mynid, peer);

			/// lock and wait local syndomx
			domanager.lockme(onMutext);

			ExchangeBlock reqb = exesinit();
			rep = exespush(peer, A.exinit, reqb);

			if (rep != null) {
				// lock remote
				while (rep.synact() == trylater) {
					if (debug)
						Utils.logT(new Object() {},
								"%s: %s is locked, waiting...",
								mynid, peer);
						
					domanager.unlockme();

					double sleep = rep.exblock.sleeps;
					Thread.sleep((long) (sleep * 1000)); // wait for next try
					domanager.lockme(onMutext);

					rep = exespush(peer, A.exinit, reqb);
				}

				if (rep.exblock != null && rep.exblock.synact() != deny) {
					// on start reply
					onsyninitRep(rep.exblock, rep.domain);
						
					while (rep.synact() != close) {
						ExchangeBlock exb = syncdb(rep.exblock);
						rep = exespush(peer, A.exchange, exb);
						if (rep == null)
							throw new ExchangeException(exb.synact(), xp,
									"Got null reply for exchange session. %s : %s -> %s",
									domain(), domanager.synode, peer);
					}
					
					// close
					reqb = synclose(rep.exblock);
					rep = exespush(peer, A.exclose, reqb);
				}
			}
		} catch (IOException e) {
			Utils.warn(e.getMessage());
		} catch (ExchangeException e) {
			e.printStackTrace();
			try {
				ExchangeBlock reqb = synclose(rep.exblock);
				rep = exespush(peer, A.exclose, reqb);
			} catch (TransException | SQLException | AnsonException | IOException e1) {
				e1.printStackTrace();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally { domanager.unlockme(); }
		return this;
	}

	/**
	 * Initiate a synchronization exchange session using ExessionPersist provided by manager.
	 * @param peer
	 * @return initiate request
	 * @throws Exception 
	 */
	ExchangeBlock exesinit() throws Exception {
		DBSyntableBuilder b0 = new DBSyntableBuilder(domanager);
		xp = new ExessionPersist(b0, peer, null);
		return b0.initExchange(xp);
	}

	/**
	 * Handle syn-init request.
	 * 
	 * @param ini request's exchange block
	 * @param domain
	 * @return respond
	 * @throws ExchangeException peer id from {@code ini} doesn't match with mine.
	 * @throws Exception
	 */
	SyncResp onsyninitRep(ExchangeBlock ini, String domain) throws ExchangeException, Exception {
		if (!eq(ini.srcnode, peer))
			throw new ExchangeException(init, null, "Request.srcnode(%s) != peer (%s)", ini.srcnode, peer);

		DBSyntableBuilder b0 = new DBSyntableBuilder(domanager);
		xp = new ExessionPersist(b0, peer, ini);
		ExchangeBlock b = b0.onInit(xp, ini);

		return new SyncResp(domain()).exblock(b);
	}

	ExchangeBlock syncdb(ExchangeBlock rep)
			throws SQLException, TransException {
		return xp.nextExchange(rep);
	}

	ExchangeBlock synclose(ExchangeBlock rep)
			throws TransException, SQLException {
		return xp.trb.closexchange(xp, rep);
	}

	SyncResp exespush(String peer, String a, ExchangeBlock reqb)
			throws SemanticException, AnsonException, IOException {

		SyncReq req = (SyncReq) new SyncReq(null, peer)
					.exblock(reqb)
					.a(a);

		return exespush(peer, req);
	}

	SyncResp exespush(String peer, SyncReq req)
			throws SemanticException, AnsonException, IOException {
		String[] act = AnsonHeader.usrAct(getClass().getName(), "push", A.exchange, "by " + mynid);
		AnsonHeader header = client.header().act(act);

		AnsonMsg<SyncReq> q = client.<SyncReq>userReq(uri_syn, Port.syntier, req)
							.header(header);

		return client.commit(q, errHandler);
	}

	void resolveDocrefs(ExpDocTableMeta docmeta) throws Exception {
		createResolver(docmeta).start();
	}
	
	public Thread createResolver(ExpDocTableMeta docmeta, OnProcess... proc4test)
			throws Exception {
		DBSyntableBuilder tb = new DBSyntableBuilder(domanager);

		// TODO not thread pool?
		return new Thread(() -> {
			// 206 downloader
			SynDocRefMeta refm = domanager.refm;
			String exclude = encode64(getRandom());

			DocRef ref = nextRef(xp.trb, docmeta, refm, peer, exclude);

			HashSet<String> tobeclean = new HashSet<String>();
			if (ref != null)
				tobeclean.add(DocRef.resolveFolder(peer, conn, ref.syntabl, client.ssInfo()));

			final DocRef[] _arref = new DocRef[] {ref};

			ExpDocRobot localRobt = new ExpDocRobot(
							client.ssInfo().uid(), null, client.ssInfo().userName());

			while (_arref[0] != null) {
				try {
					String localpath = ref.downloadPath(peer, conn, client.ssInfo());
					ExtFilePaths extpths = DocRef.createExtPaths(conn, docmeta.tbl, ref);
					String targetpth = extpths.decodeUriPath();

					if (debug)
						Utils.logT(new Object() {}, " Begin downloading %s\n-> %s", localpath, targetpth);

					client.download206(uri_syn, peerjserv, Port.syntier, localpath, ref,

						isNull(proc4test) ?
						(rx, r, bx, b, r_null) -> {
							// save breakpoint
							_arref[0].breakpoint(bx);
							try {
								DAHelper.updateFieldByPk(tb, tb.nonsemantext(), docmeta, _arref[0].docId,
										docmeta.uri, _arref[0].toBlock(AnDbField.jopt), localRobt);
							} catch (SQLException e) {
								e.printStackTrace();
								return true;
							}
							// TODO check record updating
							return false;
						} : proc4test[0]);

					Utils.touchDir(FilenameUtils.getFullPath(targetpth));
					Files.move(Paths.get(localpath), Paths.get(targetpth), StandardCopyOption.REPLACE_EXISTING);

					tb.update(docmeta.tbl, localRobt)
						.nv(docmeta.uri, extpths.dburi(true))
						.whereEq(docmeta.pk, ref.docId)
						.whereEq(docmeta.io_oz_synuid, ref.uids)
						.post(tb.delete(refm.tbl)
								.whereEq(refm.fromPeer, peer)
								.whereEq(refm.io_oz_synuid, ref.uids))
						.u(tb.nonsemantext());
				} catch (ExchangeException e) {
					if (e instanceof SemanticException
							&& ((ExchangeException) e).requires() == Exchanging.ext_docref) {
						Utils.logi("[%s] Rechead a DocRef while resolving a docref (%s, %s, %s)",
								Thread.currentThread().getName(), ref.syntabl, ref.docId, ref.uids);
						try {
							incRefTry(xp.trb, docmeta, refm, peer, exclude, ref.uids, localRobt, 2);
						} catch (TransException | SQLException e1) {
							throw new NullPointerException(e1.getMessage());
						}
					}
				} catch (IOException | TransException | SQLException e) {
					e.printStackTrace();
					try {
						incRefTry(xp.trb, docmeta, refm, peer, exclude, ref.uids, localRobt);
					} catch (TransException | SQLException e1) {
						throw new NullPointerException(e1.getMessage());
					}
				}
				finally {
					ref = nextRef(xp.trb, docmeta, refm, peer, exclude);
					_arref[0] = ref;
				}
			}
			
			for (String ps : tobeclean) {
				try {
					if (ps != null) {
						Path p = Paths.get(ps); 
						Utils.logi("[%s:DocRef Resolver] Removing temporary dowloading folder: %s",
							Thread.currentThread().getName(), p.toAbsolutePath());
						FileUtils.deleteDirectory(p.toFile());
					}
				} catch (IOException e) { }
			}

		}, f("Doc Resolver %s -> %s", this.mynid, peer));
	}

	static void incRefTry(DBSyntableBuilder trb, ExpDocTableMeta docmeta, SynDocRefMeta refm,
			String peer, String excludeTag, String uids, IUser robt, int... inc) throws TransException, SQLException {
		trb.update(refm.tbl, robt)
			.nv(refm.tried, Funcall.add(refm.tried, isNull(inc) ? 1 : inc[0]))
			.nv(refm.excludeTag,  excludeTag)
			.whereEq(refm.io_oz_synuid, uids)
			.whereEq(refm.syntabl, docmeta.tbl)
			.u(trb.instancontxt())
			;
	}

	static DocRef nextRef(DBSyntableBuilder synb, ExpDocTableMeta docm,
			SynDocRefMeta refm, String peer, String excludeTag) {
		try {
			Query q = synb
				.select(refm.tbl, "ref")
				.cols_byAlias("ref", refm.io_oz_synuid).cols_byAlias("d", docm.pk, docm.uri, docm.resname)
				.je("ref", docm.tbl, "d", refm.io_oz_synuid, docm.io_oz_synuid)
				.whereEq(refm.syntabl, docm.tbl)
				.whereEq(refm.fromPeer, peer)
				.where(Sql.condt("%s is null", refm.excludeTag).or(Sql.condt("%s <> '%s'", refm.excludeTag, excludeTag)))
				.orderby(refm.tried)
				.limit(1);

			AnResultset rs = ((AnResultset) q
					.rs(synb.instancontxt()).rs(0))
					.beforeFirst();
			if (rs.next())
				return ((DocRef) rs.getAnson(docm.uri))
						.uids(rs.getString(refm.io_oz_synuid))
						.docId(rs.getString(docm.pk))
						.resname(rs.getString(docm.resname));
			else return null;
		} catch (AnsonException | SQLException | TransException e) {
			e.printStackTrace();
			return null;
		}
	}

//	public void pingPeers() {
//	}

	/**
	 * Go through the handshaking process of sing up to a domain. 
	 * 
	 * @param admid
	 * @param myuid
	 * @param mypswd
	 * @param ok
	 * @throws TransException 
	 * @throws IOException 
	 * @throws AnsonException 
	 * @throws SQLException 
	 * @since 0.2.0
	 */
	public void joindomain(String admid, String myuid, String mypswd, OnOk ok)
			throws AnsonException, IOException, TransException, SQLException {
		SyncReq  req = signup(admid);
		SyncResp rep = exespush(admid, (SyncReq)req.a(A.initjoin));

		req = closejoin(admid, rep);
		rep = exespush(admid, (SyncReq)req.a(A.closejoin));

		if (!isNull(ok))
			ok.ok(rep);
	}

	SessionClient loginWithUri(String jservroot, String myuid, String pswd, String device)
			throws SemanticException, AnsonException, SsException, IOException {
		client = new SessionClient(jservroot, null)
				.loginWithUri(clienturi, myuid, pswd, device);
		return client;
	}

	/**
	 * Create a sign up request.
	 * <p>[Synode sign up in a hub, not user sign up.]</p>
	 * Step n-stamp, create a request package.
	 * 
	 * @param admid
	 * @return the request
	 * @throws TransException
	 * @throws SQLException
	 */
	SyncReq signup(String admid) throws TransException, SQLException {
		ExchangeBlock xb  = xp.trb.domainSignup(xp, admid);
		return new SyncReq(null, admid)
				.exblock(xb);
	}

	public SyncReq closejoin(String admin, SyncResp rep) throws TransException, SQLException {
		if (!isblank(domanager.domain()) && !eq(notNull(rep.domain), domanager.domain()))
			throw new ExchangeException(close, xp,
				"Close joining session for different ids? Rep.domain: %s, Domanager.domain: %s",
				rep.domain, domanager.domain());
		
		if (ev(deny, rep.exblock.synact()))
			throw new ExchangeException(setupDom, xp,
					"Joining domain is denied: %s", rep.msg());
		if (!ev(setupDom, rep.exblock.synact()))
			throw new ExchangeException(setupDom, xp,
					"Joining domain information indicates an action of code (setupDom)%s, rather than %s.\n%s",
					setupDom, rep.exblock.synact(), rep.msg());

		xp.trb.domainitMe(xp, admin, peerjserv, rep.domain, rep.exblock);

		ExchangeBlock req = xp.trb.domainCloseJoin(xp, rep.exblock);
		return new SyncReq(null, domanager.domain())
				.exblock(req);
	}
}
