package io.oz.jserv.docs.syn;

import static io.odysz.common.AESHelper.encode64;
import static io.odysz.common.AESHelper.getRandom;
import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.ev;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.ifnull;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.notNull;
import static io.oz.syn.ExessionAct.close;
import static io.oz.syn.ExessionAct.deny;
import static io.oz.syn.ExessionAct.init;
import static io.oz.syn.ExessionAct.ready;
import static io.oz.syn.ExessionAct.setupDom;
import static io.oz.syn.ExessionAct.trylater;
import static io.odysz.common.LangExt.is;
import static io.odysz.common.LangExt.indexOf;
import static io.odysz.common.LangExt.musteq;
import static io.odysz.common.LangExt.mustnonull;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.xml.sax.SAXException;

import io.odysz.anson.AnsonException;
import io.odysz.common.AESHelper;
import io.odysz.common.FilenameUtils;
import io.odysz.common.Regex;
import io.odysz.common.Utils;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.syn.ExpDocRobot;
import io.odysz.jclient.syn.IFileProvider;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.CRUD;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.JProtocol.OnDocsOk;
import io.odysz.semantic.jprotocol.JProtocol.OnError;
import io.odysz.semantic.jprotocol.JProtocol.OnOk;
import io.odysz.semantic.jprotocol.JProtocol.OnProcess;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.meta.DocRef;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.meta.SynDocRefMeta;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.IFileDescriptor;
import io.odysz.semantic.util.DAHelper;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.ExchangeException;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.parts.AnDbField;
import io.odysz.transact.sql.parts.ExtFilePaths;
import io.odysz.transact.sql.parts.Logic.op;
import io.odysz.transact.sql.parts.Sql;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.SyncReq.A;
import io.oz.syn.DBSyntableBuilder;
import io.oz.syn.ExchangeBlock;
import io.oz.syn.ExessionAct;
import io.oz.syn.ExessionPersist;
import io.oz.syn.SyncUser;
import io.oz.syn.SynodeMode;
import io.oz.syn.SyndomContext.OnMutexLock;

/**
 * @since 0.2.0
 */
public class SynssionPeer {

	public static boolean testDisableAutoDocRef = false;

	/** */
	final String conn;
	/** /syn/[peer-synode-id] */
	final String uri_syn;
	/** /sys/[peer-synode-id] */
	final String uri_sys;

	/** {@link #uri_syn}/[peer] */
	final String clienturi;

	final String mynid;
	/** The remode, server side, synode */
	final String peer;

	String peerjserv() {
		return domanager == null
				? null
				// : domanager.syngleton.settings.jserv(domanager.synode);
				: domanager.syngleton.settings.jserv(peer);
	}

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

	/** Initialized by {@link io.oz.syn.SyndomContext#dbg}, which should be from Config.debug. */
	private boolean debug;

	/**
	 * In each post synssion handling loops, avoid the doc-refs already tried or failed. 
	 */
	private HashMap<String, ArrayList<String>> avoidRefs2me;
	void addAvoidRefs(String peer, String synuid) {
		if (!avoidRefs2me.containsKey(peer))
			avoidRefs2me.put(peer, new ArrayList<String>());
		avoidRefs2me.get(peer).add(synuid);
	}

	boolean inAvoidRefs(String peer, String synuid) {
		return avoidRefs2me.containsKey(peer) && indexOf(avoidRefs2me.get(peer), synuid) >= 0;
	}

	void clearAvoidingRefs(String peer) {
		if (avoidRefs2me.containsKey(peer))
			avoidRefs2me.get(peer).clear();
	}

	public SynssionPeer(SynDomanager domanager, String peer, boolean debug) {
		// ISSUE
		// TODO It's better to change this to /syn/me, not /syn/peer once Connects is refactored. 
		// TODO for synodes' uri_syn, the semantics is hard bound to SynodeConfig.conn.
		this.uri_syn   = "/syn/" + peer; // domanager.synode;
		this.uri_sys   = "/sys/" + peer; // domanager.synode;

		this.conn      = domanager.synconn;
		this.mynid     = domanager.synode;
		this.domanager = domanager;
		this.peer      = peer;
		this.mymode    = domanager.mode;
		// this.peerjserv = peerjserv;
		this.clienturi = uri_sys;
		this.debug     = debug;
		
		this.avoidRefs2me = new HashMap<String, ArrayList<String>>();
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
			if (debug)
				Utils.logi("Locking and starting thread on domain updating: %s : %s -> %s"
						+ "\n=============================================================\n",
						domain(), mynid, peer);

			/// lock and wait local syndomx
			domanager.lockme(onMutext);

			ExchangeBlock reqb = ifnull(exesrestore(), exesinit());
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

					// FIXME
					// FIXME ISSUE if Y is interrupted, or shutdown, X can be dead locking
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
				
				if (!testDisableAutoDocRef) {
					DBSyntableBuilder tb = new DBSyntableBuilder(domanager);
					resolveRef206Stream(tb);
					pushDocRef2me(tb, peer);
				}
				else
					Utils.warn("[%s : %s - SynssionPeer] Update to peer %s, auto-resolving doc-refs is disabled.",
							domanager.synode, domanager.domain(), peer);

			}
		} catch (TransException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
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
	 * Restoring request
	 * @return null or restore-request
	 * @throws Exception
	 * @since 1.5.18
	 */
	ExchangeBlock exesrestore() throws Exception {
		DBSyntableBuilder b0 = new DBSyntableBuilder(domanager);
		xp = new ExessionPersist(b0, peer, null);
		return b0.restorexchange(xp);
	}
	/**
	 * Handle syn-init request.
	 * 
	 * @param ini request's exchange block
	 * @param domain
	 * @return respond
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws SQLException 
	 * @throws TransException 
	 * @throws Exception
	 */
	SyncResp onsyninitRep(ExchangeBlock ini, String domain) throws TransException, SQLException, SAXException, IOException {
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
		try {
			return xp == null ? null : xp.trb.closexchange(xp, rep);
		} finally {
			xp = null;
		}
	}

	SyncResp exespush(String peer, String a, ExchangeBlock reqb)
			throws SemanticException, AnsonException, IOException {
		SyncReq req = (SyncReq) new SyncReq(null, reqb.domain)
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

	/**
	 * @deprecated only for test - this is a part of domain updating process.
	 * @param docmeta
	 * @param proc4test
	 * @return worker thread
	 * @throws Exception
	 * @since 0.2.4
	 */
	public Thread createResolver(OnProcess... proc4test)
			throws Exception {
		DBSyntableBuilder tb = new DBSyntableBuilder(domanager);

		return new Thread(() -> {
			resolveRef206Stream(tb, proc4test);
		}, f("Doc Resolver %s -> %s", this.mynid, peer));
	}

	/**
	 * 
	 * @deprecated only for test - this is a part of domain updating process.
	 * @param proc4test
	 * @return thread for resolving downward doc-refs
	 * @throws Exception
	 */
	public Thread pushResolve()
			throws Exception {
		DBSyntableBuilder tb = new DBSyntableBuilder(domanager);

		return new Thread(() -> {
			try {
				pushDocRef2me(tb, peer);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}, f("Doc Resolver %s -> %s", this.mynid, peer));
	}

	/**
	 * [not synsession managed]
	 * @param tb
	 * @param report2test
	 * @since 0.2.4
	 */
	private void resolveRef206Stream(DBSyntableBuilder tb, OnProcess... report2test) {
		// 206 downloader
		SynDocRefMeta refm = domanager.refm;
		String exclude = encode64(getRandom());

		DocRef ref = nextRef(tb, refm, peer, exclude);

		HashSet<String> tobeclean = new HashSet<String>();
		if (ref != null)
			tobeclean.add(DocRef.resolveFolder(peer, conn, ref.syntabl, client.ssInfo()));

		final DocRef[] _arref = new DocRef[] {ref};

		ExpDocRobot localRobt = new ExpDocRobot(
						client.ssInfo().uid(), null, client.ssInfo().userName());

		while (ref != null) {
			ExpDocTableMeta docm = ref.docm;
			try {
				String localpath = ref.downloadPath(peer, conn, client.ssInfo());
				ExtFilePaths extpths = DocRef.createExtPaths(conn, docm.tbl, ref);
				String targetpth = extpths.decodeUriPath();

				if (debug)
					Utils.logT(new Object() {}, " Begin downloading %s\n-> %s", localpath, targetpth);

				client.download206(uri_syn, peerjserv(), Port.syntier, localpath, ref,

					isNull(report2test) ?
					(rx, r, bx, b, r_null) -> {
						// save breakpoint
						_arref[0].breakpoint(bx);
						try {
							DAHelper.updateFieldByPk(tb, tb.nonsemantext(), docm, _arref[0].docId,
									docm.uri, _arref[0].toBlock(AnDbField.jopt), localRobt);
						} catch (SQLException e) {
							e.printStackTrace();
							return true;
						}
						// TODO check record updating
						return false;
					} : report2test[0]);

				Utils.touchDir(FilenameUtils.getFullPath(targetpth));
				
				Files.move(Paths.get(localpath), Paths.get(targetpth), StandardCopyOption.REPLACE_EXISTING);

				tb.update(docm.tbl, localRobt)
					.nv(docm.uri, extpths.dburi(true))
					.whereEq(docm.pk, ref.docId)
					.whereEq(docm.io_oz_synuid, ref.uids)
					.post(tb.delete(refm.tbl)
							.whereEq(refm.fromPeer, peer)
							.whereEq(refm.io_oz_synuid, ref.uids))
					.u(tb.nonsemantext());
			} catch (ExchangeException e) {
				if (e instanceof SemanticException
						&& ((ExchangeException) e).requires() == ExessionAct.ext_docref) {
					Utils.logi("[%s] Rechead a peer DocRef while resolving a docref (%s, %s, %s)",
							Thread.currentThread().getName(), ref.syntabl, ref.docId, ref.uids);
					try {
						incRefTry(tb, docm, refm, peer, exclude, ref.uids, localRobt, 2);
					} catch (TransException | SQLException e1) {
						throw new NullPointerException(e1.getMessage());
					}
				}
			} catch (IOException | TransException | SQLException e) {
				Utils.warn("Download Doc for ref error: %s[%s], %s ", ref.docId, ref.uids, ref.pname);
				e.printStackTrace();
				try {
					incRefTry(tb, docm, refm, peer, exclude, ref.uids, localRobt);
				} catch (TransException | SQLException e1) {
					throw new NullPointerException(e1.getMessage());
				}
			}
			finally {
				ref = nextRef(tb, refm, peer, exclude);
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
	}

	/**
	 * @param trb
	 * @param docmeta
	 * @param refm
	 * @param peer
	 * @param excludeTag
	 * @param uids
	 * @param robt
	 * @param inc
	 * @throws TransException
	 * @throws SQLException
	 * @since 0.2.4
	 */
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

	/**
	 * @param synb
	 * @param refm
	 * @param peer
	 * @param excludeTag used for try only once on each updating running - tried time is increased.
	 * @return next doc-ref task
	 * @since 0.2.4
	 */
	static DocRef nextRef(DBSyntableBuilder synb, SynDocRefMeta refm,
			String peer, String excludeTag) {
		try {
			Query q = synb
				.select(refm.tbl, "ref")
				.cols(refm.io_oz_synuid, refm.syntabl)
				.whereEq(refm.fromPeer, peer)
				.where(Sql.condt("%s is null", refm.excludeTag)
						  .or(Sql.condt("%s <> '%s'", refm.excludeTag, excludeTag)))
				.orderby(refm.tried)
				.limit(1);

			ISemantext semantxt = synb.instancontxt();
			AnResultset rs = ((AnResultset) q
					.rs(semantxt).rs(0))
					.beforeFirst();
			if (rs.next()) {
				ExpDocTableMeta docm = (ExpDocTableMeta) semantxt
						.getTableMeta(rs.getString(refm.syntabl));

				String uids = rs.getString(refm.io_oz_synuid);
				rs = ((AnResultset) synb
					.batchSelect(docm.tbl)
					.cols(docm.pk, docm.uri, docm.resname, docm.io_oz_synuid).col(Funcall.isEnvelope(docm.uri), "isenvl")
					.whereEq(docm.io_oz_synuid, uids)
					.before(synb // delete records of which the uri is not an envelope now
						.delete(refm.tbl)
						.whereEq(refm.syntabl, docm.tbl)
						.whereEq(refm.fromPeer, peer)
						.whereNotIn(refm.io_oz_synuid, synb
							.select(docm.tbl)
							.col(docm.io_oz_synuid)
							.where(op.eq, Funcall.isEnvelope(docm.uri), "1"))) // envelopes should be much fewer
					.rs(synb.instancontxt())
					.rs(0))
					.nxt();

				if (!rs.getBoolean("isenvl")) {
					Utils.warnT(new Object() {},
						"Suspesiously, deleting a syn_docref record of an expired task?\n%s -> %s, %s, uids: %s",
						synb.syndomx.synode, peer, docm.tbl, uids);

					synb.delete(refm.tbl)
						.whereEq(refm.syntabl, uids)
						.d(synb.instancontxt());
					return null;
				}
					
				return ((DocRef) rs.getAnson(docm.uri))
						.uids(rs.getString(docm.io_oz_synuid))
						.docId(rs.getString(docm.pk))
						.resname(rs.getString(docm.resname))
						.docm(docm);
			}
			else return null;
		} catch (AnsonException | SQLException | TransException e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * [not synsession managed]
	 * @param tb
	 * @param peer
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws TransException 
	 * @throws AnsonException 
	 * @since 0.2.5
	 */
	private void pushDocRef2me(DBSyntableBuilder tb, String peer)
			throws Exception {
		clearAvoidingRefs(peer);
		SyncResp resp = queryDocRefPage2me(null, null);
		while (resp != null && resp.docrefs != null && resp.docrefs.size() > 0) {
			pushDocRefPage(tb, resp.docrefsTabl, resp.docrefs);
			
			resp = queryDocRefPage2me(resp.docrefsTabl, this.avoidRefs2me.get(peer));
		}
	}

	/**
	 * 
	 * @param tb
	 * @param docrefs
	 * @return replies
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws TransException 
	 * @throws AnsonException 
	 * @since 0.2.5
	 */
	private List<SyncResp> pushDocRefPage(DBSyntableBuilder tb, String docTabl, HashMap<String, DocRef> docrefs)
			throws AnsonException, TransException, IOException, SQLException, Exception {
		OnProcess proc = null;
		OnDocsOk docOk = null;

		return pushBlocks(client, uri_syn, tb, docTabl, docrefs,
				new IFileProvider() {}, null, proc, docOk, errHandler);
	}

	private IFileDescriptor queryMyPhysicalFile(DBSyntableBuilder trb, String tabl, DocRef docrefs) throws TransException, SQLException {
		ExpDocTableMeta docm = (ExpDocTableMeta) Connects.getMeta(conn, tabl);

		AnResultset rs = (AnResultset) trb
				.select(tabl)
				.cols((Object[])ExpSyncDoc.nvCols(docm))
				.whereEq(docm.io_oz_synuid, docrefs.uids)
				.rs(trb.instancontxt())
				.rs(0);
		
		if (rs.next() && !Regex.startsEvelope(rs.getString(docm.uri)))
			return new ExpSyncDoc(rs, docm);
		else return null;
	}

	List<SyncResp> pushBlocks(SessionClient client, String uri, DBSyntableBuilder trb, String tbl,
			HashMap<String, DocRef> docrefs, IFileProvider fileProvider, ExpSyncDoc template,
			OnProcess proc, OnDocsOk docsOk, OnError err, boolean... verbose) throws Exception {

		SyncResp respi = null;

		String[] act = AnsonHeader.usrAct(uri_syn, CRUD.U, A.docRefBlockUp, mynid);
		AnsonHeader header = client.header().act(act);

		List<SyncResp> reslts = new ArrayList<SyncResp>(docrefs.size());

		int px = 0;
		for (String uids : docrefs.keySet()) {
			if (inAvoidRefs(peer, uids))
				continue;
			
			try {
				final int pxx = px++;
				respi = push1docBlocks(client, uri, tbl, docrefs.get(uids), fileProvider, template, trb,
						(dx, docs, bx, blocks, msg)->{ return proc == null ? false : proc.proc(pxx, docrefs.size(), bx, blocks, msg); }, 
						err, verbose);
				reslts.add(respi);
			} catch (NoSuchFileException | FileNotFoundException ne) {
				Utils.warn("No such file in %s, uids = %s, peer %s, error: %s", tbl, uids, peer, ne.getMessage());
				addAvoidRefs(peer, uids);
			} catch (IOException | TransException | AnsonException ex) { 
				if (is(verbose)) ex.printStackTrace();

				String exmsg = ex.getMessage();
				Utils.warn(exmsg);

				SyncReq req = new SyncReq()
						.blockAbort(domanager.domain(), mynid, peer, respi)
						.docref(docrefs.get(uids));

				req.a(SyncReq.A.docRefBlockAbort);

				AnsonMsg<SyncReq> q = client.<SyncReq>userReq(uri, Port.syntier, req)
							.header(header);

				respi = client.commit(q, errHandler);

				if (ex instanceof IOException)
					continue;
				else {
					addAvoidRefs(peer, uids);

					// Tag: MVP - This is not correct way of deserialize exception at client side
					if (!isblank(exmsg)) {
						try {
							// Error code: exGeneral,
							// Docs' pushing requires device id and clientpath.
							// Doc Id: 0101, device id: Y-0(Y), client-path: src/test/res/anclient.java/3-birds.wav, resource name: 3-birds.wav
							errHandler.err(MsgCode.exSemantic, exmsg);
						}
						catch (Exception exx) {
							errHandler.err(MsgCode.exGeneral, ex.getMessage(),
								ex.getClass().getName(), isblank(ex.getCause()) ? null : ex.getCause().getMessage());
						}
					}
					else
						errHandler.err(MsgCode.exGeneral, ex.getMessage(),
							ex.getClass().getName(), isblank(ex.getCause()) ? null : ex.getCause().getMessage());
				}
			}
		}
		if (docsOk != null) docsOk.ok(reslts);

		return reslts;
	}
	
	SyncResp push1docBlocks(SessionClient client, String uri, String tbl,
			DocRef docref, IFileProvider fileProvider, ExpSyncDoc template, DBSyntableBuilder trb,
			OnProcess proc, OnError err, boolean... verbose)
					throws AnsonException, IOException, TransException, SQLException {

		mustnonull(docref.syntabl);

		int seq = 0;
		int totalBlocks = 0;
		IFileDescriptor fd = queryMyPhysicalFile(trb, tbl, docref);
		if (fd == null) {
			addAvoidRefs(peer, docref.uids);
			return (SyncResp) new SyncResp().docref(docref);
		}

		if (fileProvider == null) {
			if (isblank(fd.fullpath()) || isblank(fd.clientname()) || isblank(fd.cdate()))
				throw new IOException(
						f("File information is not enough: %s, %s, create time %s",
						fd.clientname(), fd.fullpath(), fd.cdate()));
		}
		
		long size = fileProvider.meta(fd);
		if (size < 0) {
			return 
			(SyncResp) new SyncResp().docref(docref);
		}

		ExpSyncDoc p = fd.syndoc(template);
		Path path = fileProvider.pysicalPath(fd);

		SyncReq req  = new SyncReq()
				.blockStart(domanager.domain(), mynid, peer, totalBlocks, fd)
				.docref(docref);
		
		String[] act = AnsonHeader.usrAct(uri_syn, CRUD.U, A.docRefBlockUp, mynid);
		AnsonHeader header = client.header().act(act);
		AnsonMsg<SyncReq> q = client.<SyncReq>userReq(uri, Port.syntier, req)
								.header(header);

		SyncResp resp0 = client.commit(q, errHandler);

		totalBlocks = (int) (Math.max(0, p.size - 1) / AESHelper.blockSize()) + 1;

		if (proc != null) proc.proc(-1, -1, 0, totalBlocks, resp0);

		// Let's use ranges for concurrency, in the future.
		try (FileInputStream ifs = (FileInputStream) fileProvider.open(fd)) {

			long start = 0;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			long len = AESHelper.encodeRange(path, ifs, size, baos, start, AESHelper.blockSize());
			SyncResp respi = null;
			while (len > 0) {
				req = new SyncReq()
						.docref(docref)
						.range(start, start + len)
						.blockUp(domanager.domain(), mynid, peer, seq, p, baos.toString());
				seq++;
				start += len;

				q = client.<SyncReq>userReq(uri, Port.syntier, req)
							.header(header);

				respi = client.commit(q, errHandler);
				if (proc != null) proc.proc(1, 1, seq, totalBlocks, respi);

				baos = new ByteArrayOutputStream();
				len = AESHelper.encodeRange(path, ifs, size, baos, start, AESHelper.blockSize());
			}
			
			mustnonull(docref.uids);
			req = new SyncReq()
					.blockEnd(domanager.domain(), mynid, peer, respi == null ? resp0 : respi)
					.docref(docref);

			q = client.<SyncReq>userReq(uri, Port.syntier, req)
						.header(header);
			respi = client.commit(q, errHandler);
			if (proc != null) proc.proc(1, 1, seq, totalBlocks, respi);

			return respi;
		} 
	}
	

	/**
	 * Request {@link A#queryRef2me}.
	 * @param hashSet 
	 * @param docrefsTabl 
	 * @since 0.2.5
	 * @return query results, with {@link SyncResp#docrefs}
	 * @throws IOException 
	 * @throws AnsonException 
	 * @throws SemanticException 
	 */
	SyncResp queryDocRefPage2me(String docrefsTabl, ArrayList<String> avoidUids) throws SemanticException, AnsonException, IOException {
		String[] act = AnsonHeader.usrAct(uri_syn, CRUD.R, A.queryRef2me, mynid);
		AnsonHeader header = client.header().act(act);

		SyncReq req = (SyncReq) new SyncReq()
				.exblock(new ExchangeBlock(domanager.domain(), mynid, peer, ExessionAct.mode_client))
				.avoid(docrefsTabl, avoidUids)
				.a(A.queryRef2me); 

		AnsonMsg<SyncReq> q = client // accept any page size specified by server
				.<SyncReq>userReq(uri_syn, Port.syntier, req)
				.header(header);

		SyncResp resp = client.commit(q, errHandler);

		return resp;
	}


	///////////////////////////////////
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
	 * File a request to the this.client's peer for updating *jservs* knowledge.
	 * @return jservs
	 * @throws IOException 
	 * @throws AnsonException 
	 * @throws SemanticException 
	 */
	public HashMap<String, String[]> queryJservs()
			throws SemanticException, AnsonException, IOException {

		mustnonull(client);
		SyncReq  req = (SyncReq) new SyncReq(null, domain())
				.exblock(new ExchangeBlock(domanager.domain(),
						domanager.synode, peer, ExessionAct.mode_client))
				.a(A.queryJservs);

		String[] act = AnsonHeader.usrAct(getClass().getName(), "queryJservs", A.exchange, "by " + mynid);
		AnsonHeader header = client.header().act(act);

		AnsonMsg<SyncReq> q = client.<SyncReq>userReq(uri_syn, Port.syntier, req)
							.header(header);

		SyncResp resp = client.commit(q, errHandler);
		
		mustnonull(resp);
		musteq(resp.domain, domain());
		return resp.jservs;
	}

	public HashMap<String, String[]> submitJserv(String jserv)
			throws SemanticException, AnsonException, IOException {
		mustnonull(client);
		SynodeMeta m = domanager.synm;
		SyncReq req = (SyncReq) new SyncReq(null, domain())
				.exblock(new ExchangeBlock(domanager.domain(),
						domanager.synode, peer, ExessionAct.mode_client))
				.a(A.reportJserv);

		req.data(m.jserv, jserv);

		String[] act = AnsonHeader.usrAct(getClass().getName(), "queryJservs", A.exchange, "by " + mynid);
		AnsonHeader header = client.header().act(act);

		AnsonMsg<SyncReq> q = client.<SyncReq>userReq(uri_syn, Port.syntier, req)
							.header(header);

		SyncResp resp = client.commit(q, errHandler);
		
		mustnonull(resp);
		musteq(resp.domain, domain());

		return // FIXME ISSUE check nyquence?
			eq((String)resp.data().get(m.remarks), SynodeMode.hub.name())
			? resp.jservs
			: null;
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

		xp.trb.domainitMe(xp, admin, peerjserv(), rep.domain, rep.exblock);

		ExchangeBlock req = xp.trb.domainCloseJoin(xp, rep.exblock);
		return new SyncReq(null, domanager.domain())
				.exblock(req);
	}

	public void checkLogin(SyncUser docuser)
			throws SemanticException, AnsonException, SsException, IOException, TransException {
		if (client == null || !client.isSessionValid())
			loginWithUri(peerjserv(), docuser.uid(), docuser.pswd(), docuser.deviceId());
	}
}