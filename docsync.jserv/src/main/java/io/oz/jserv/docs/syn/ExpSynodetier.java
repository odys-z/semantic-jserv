package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.LangExt.musteqi;
import static io.odysz.common.LangExt.musteqs;
import static io.odysz.common.LangExt.mustge;
import static io.odysz.common.LangExt.mustnonull;
import static io.odysz.common.LangExt.notNull;
import static io.oz.syn.ExessionAct.deny;
import static io.oz.syn.ExessionAct.mode_server;
import static io.oz.syn.ExessionAct.ready;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.common.AESHelper;
import io.odysz.common.Regex;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.JProtocol.OnError;
import io.odysz.semantic.jprotocol.JServUrl;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.meta.DocRef;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.meta.SynDocRefMeta;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.tier.docs.BlockChain;
import io.odysz.semantic.tier.docs.BlockChain.IBlock;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.ExchangeException;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.parts.ExtFilePaths;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.SyncReq.A;
import io.oz.jserv.docs.syn.singleton.AppSettings;
import io.oz.syn.DBSyntableBuilder;
import io.oz.syn.ExchangeBlock;
import io.oz.syn.ExessionAct;
import io.oz.syn.SynodeMode;

@WebServlet(description = "Synode Tier Workder", urlPatterns = { "/sync.tier" })
public class ExpSynodetier extends ServPort<SyncReq> {
	private static final long serialVersionUID = 1L;
	
	final String domain;
	final String synid;
	
	/** peer | non-syn | leaf */
	final SynodeMode mode;

	SynDomanager domanager0;
	
	public boolean debug;

	ExpSynodetier(String org, String domain, String synode, String conn, SynodeMode mode)
			throws SQLException, SAXException, IOException, TransException {
		super(Port.syntier);
		this.domain = domain;
		this.synid  = synode;
		this.mode   = mode;
		try {
			this.st = new DATranscxt(conn);
		} catch (Exception e) {
			e.printStackTrace();
			throw new SemanticException("Fail to create DATranscxt on %s.", conn);
		}
		this.debug  = Connects.getDebug(conn);
	}

	public ExpSynodetier(SynDomanager domanger)
			throws Exception {
		this(domanger.org, domanger.domain(), domanger.synode, domanger.synconn, domanger.mode);
		domanager0 = domanger;
	}

	@Override
	protected void onGet(AnsonMsg<SyncReq> msg, HttpServletResponse resp) {
		Utils.warnT(new Object() {}, "Targeted? Source header: %s\nbody: %s",
				msg.header().toString(),
				msg.body(0).toString());
	}

	@Override
	protected void onPost(AnsonMsg<SyncReq> jmsg, HttpServletResponse resp)
			throws ServletException, IOException {
		AnsonBody jreq = jmsg.body(0); // SyncReq | DocsReq
		String a = jreq.a();
		SyncResp rsp = null;
		try {
			SyncReq req = jmsg.body(0);

			DocUser usr = (DocUser) JSingleton.getSessionVerifier().verify(jmsg.header());
			notNull(usr.deviceId());
			
			if (req.exblock != null) {
				if (!isblank(req.exblock.domain) && !eq(req.exblock.domain, domain))
					throw new ExchangeException(ready, null,
							"domain is %s, while expecting %s", req.exblock.domain, domain);
				else if (isblank(req.exblock.srcnode))
					throw new ExchangeException(ready, null,
							"req.exblock.srcnode is null. Requests to (Exp)Synodetier must present the souce identity.");
				else if (eq(req.exblock.srcnode, synid))
					throw new ExchangeException(ready, null,
							"req.exblock.srcnode is identical to this synode's.");
			}

			if (A.queryJservs.equals(a))
				rsp = onQueryJservs(req, usr);
			else if (A.reportJserv.equals(a))
				rsp = onReportJserv(req, usr);

			else if (A.initjoin.equals(a)) {
				if (!eq(usr.orgId(), domanager0.org))
					rsp = (SyncResp) deny(req.exblock).msg(f(
							"User's org id, %s from %s, is not matched for joining %s. Domain: %s",
							usr.orgId(), req.exblock.srcnode, domanager0.org, domanager0.domain()));
				else
					rsp = new SynssionServ(domanager0, req.exblock.srcnode, usr)
						.onjoin(req);
			}

			else if (A.closejoin.equals(a))
				rsp = usr.<SynssionServ>synssion().onclosejoin(req, usr);

			else if (A.exinit.equals(a)) 
				rsp = new SynssionServ(domanager0, req.exblock.srcnode, usr)
					.onsyninit(req.exblock);

			else if (A.exrestore.equals(a))
				rsp = usr.<SynssionServ>synssion().onsynrestore(req.exblock);

			else if (A.exchange.equals(a))
				rsp = usr.<SynssionServ>synssion().onsyncdb(req.exblock);

			else if (A.exclose.equals(a))
				rsp = usr.<SynssionServ>synssion().onclosex(req, usr);

			else if (A.queryRef2me.equals(a))
				rsp = onQueryRef2Peer(req, usr);
			else if (A.startDocrefPush.equals(a))
				rsp = onDocRefPushStart(req, usr);
			else if (A.docRefBlockUp.equals(a))
				rsp = onDocRefUploadBlock(req, usr);
			else if (A.docRefBlockEnd.equals(a))
				rsp = onDocRefEndBlock(req, usr);
			else if (A.docRefBlockAbort.equals(a))
				rsp = onDocRefAbortBlock(req, usr);

			else 
				throw new SemanticException("Request.a, %s, can not be handled at port %s",
						jreq.a(), p.name());

			write(resp, ok(rsp.syndomain(domain)));
		} catch (ExchangeException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SQLException | TransException e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (Throwable e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exGeneral, "%s\n%s", e.getClass().getName(), e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	/**
	 * @param exblock
	 * @return response message
	 * @since 0.2.5
	 */
	private SyncResp deny(ExchangeBlock exblock) {
		return new SyncResp(domanager0.domain()).exblock(
				new ExchangeBlock(domanager0.domain(), domanager0.synode, exblock.srcnode, null,
				new ExessionAct(mode_server, deny)));
	}

	//////////////////////////////  worker    ///////////////////////////////////
	public static int maxSyncInSnds = (int) (60.0 * (5 + Math.random())); // 5-6 minutes

	final Runnable[] worker = new Runnable[] {null};

	float syncInSnds;

	private ScheduledExecutorService scheduler;
	private static ScheduledFuture<?> schedualed;

	@Override
	public void destroy() {
		super.destroy();
		stopScheduled(5);
	}

	boolean running;

	/**
	 * <h4>Debug Notes</h4>
	 * Multiple threads cannot be scheduled ad a single test running.
	 * 
	 * @param syncIns
	 * @return this
	 * @throws Exception 
	 * @since 0.7.0
	 */
	public ExpSynodetier syncIn(float syncIns, OnError err) throws Exception {
		this.syncInSnds = syncIns;
		if ((int)(this.syncInSnds) <= 0) {
			Utils.warn("Syn-worker is disabled (synching in = %s seconds). %s : %s [%s]",
					syncIns, domanager0.domain(), domanager0.synode, domanager0.synconn);
			return this;
		}

		DATranscxt syntb = new DATranscxt(domanager0.synconn);

		lights = new HashMap<String, Boolean>();
		worker[0] = () -> {
			if (running)
				return;
			running = true;

			if (debug)
				Utils.logi("[%s] : Checking Syndomain ...", synid);

			try {
				// if (domanager0.updJservs_byJson(domanager0.syngleton.syncfg, domanager0.syngleton.settings))
				if (domanager0.syngleton.settings.mergeLoadJservs(domanager0.syngleton.syncfg, domanager0.synm))
					domanager0.ipChangeHandler.onExpose(domanager0.syngleton.settings, domanager0);

				domanager0.loadSynclients(syntb);

				// 0.7.6 Solution:
				// Get peer jservs from hub, save into synconn.syn_node.jserv.
				// ISSUE: It's possible some nodes cannot access the hub but can only be told by a peer node.
				if (!domanager0.submitJservsPersist(domanager0.syngleton.settings.localIp))
					domanager0.updJservs_byHub(syntb);
			
				waitAll(domanager0.sessions);
				for (SynssionPeer p : domanager0.sessions.values())
					try {
						domanager0.synUpdateDomain(p,
							(dom, synode, peer, xp) -> {
								if (debug) Utils.logi("[%s] On update: %s [n0 %s : stamp %s]",
										synid, dom, domanager0.n0(), domanager0.stamp());
								turngreen(peer);
							});
					} catch (ExchangeException e) {
						// Something not done yet, e.g. breakpoints resuming
						if (debug) e.printStackTrace();
					} catch (IOException e) {
						// wait for network
						Utils.logi("[♻.◬ %s ] syn-worker has an IO(network) error: %s", synid, e.getMessage());
					}
				
				if (!anygreen())
					reschedule(30);

			// thread level catches, local errors
			} catch (Exception e1) {
				e1.printStackTrace();
				stopScheduled(2);
			} finally {
				this.domanager0.closession();
				running = false;
			}
		};
	

		scheduler = Executors.newSingleThreadScheduledExecutor(
				(r) -> new Thread(r, f("synworker-%s", synid)));
		reschedule(0);

        running = false;
		return this;
	}

	/**
	 * @since 0.2.5
	 */
	private void reschedule(int waitmore) {
		if (schedualed != null) {
			try {
				schedualed.cancel(true);
				Thread.sleep(500);
			} catch (InterruptedException e) { }
		}

		syncInSnds = Math.min(maxSyncInSnds, syncInSnds + waitmore);
		schedualed = scheduler.scheduleWithFixedDelay(
				worker[0], (int) (syncInSnds * 1000), (int) (syncInSnds * 1000),
				TimeUnit.MILLISECONDS);
	}

	/**
	 * @param sTimeout
	 * @since 0.2.5
	 */
	public void stopScheduled(int sTimeout) {
		Utils.logi("[ ♻.⛔ %s ] cancling sync-worker ... ", synid);

		if (schedualed != null)
			schedualed.cancel(true);

		scheduler.shutdown();
		try {
		    if (!scheduler.awaitTermination(sTimeout, TimeUnit.SECONDS)) {
		        scheduler.shutdownNow();
		    }
		} catch (InterruptedException e) {
		    scheduler.shutdownNow();
		}
		finally { running = false; }
	}

	/** @since 0.2.7 */
	HashMap<String, Boolean> lights;

	/** @since 0.2.7 */
	private void turngreen(String peer) {
		lights.put(peer, true);
	}

	/** @since 0.2.7 */
	private void waitAll(HashMap<String, SynssionPeer> sessions) {
		lights.clear();
		for (String p : sessions.keySet()) {
			SynssionPeer peer = sessions.get(p);
			musteqs(p, peer.peer);
			lights.put(p, false);
		}
	}
	
	/** @since 0.2.7 */
	private boolean anygreen() {
		for (boolean lit : lights.values())
			if (lit) return true;
		return false;
	}

	//////////////////////////////////// Doc-ref Service ///////////////////////////////
	/**
	 * Response to {@link SyncReq.A#queryRef2me}
	 * @param req
	 * @param usr
	 * @return uids according to syn_docref
	 * @throws SQLException 
	 * @throws TransException 
	 * @since 0.2.5
	 * @see SynssionPeer#nextRef(io.oz.syn.DBSyntableBuilder, io.odysz.semantic.meta.SynDocRefMeta, String, String)
	 */
	public SyncResp onQueryRef2Peer(SyncReq req, DocUser usr)
			throws SQLException, TransException {
		// let's brutally resolve refs table by table
		SynDocRefMeta refm = domanager0.refm;
		String conn = Connects.uri2conn(req.uri());
		AnResultset rs = (AnResultset) st
				.batchSelect(refm.tbl)
				.col(refm.syntabl)
				.distinct(true)
				.groupby(refm.syntabl)
				.whereEq(refm.fromPeer, req.exblock.srcnode)
				.limit(1)
				.rs(st.instancontxt(conn, usr))
				.rs(0);
		
		SyncResp resp = new SyncResp(domain);
		if (rs.next()) {
			String doctbl = rs.getString(refm.syntabl);
			ISemantext ctx = st.instancontxt(conn, usr);
			ExpDocTableMeta docm = (ExpDocTableMeta) Connects.getMeta(conn, doctbl);

			Query q = st.batchSelect(docm.tbl, "d")
				.cols(docm.pk, docm.uri, docm.io_oz_synuid)
				.je("d", refm.tbl, "rf", "d." + docm.io_oz_synuid, "rf." + refm.io_oz_synuid)
				.whereEq(refm.fromPeer, req.exblock.srcnode)
				.whereEq(refm.syntabl, doctbl)
				.limit(16);
			
			if (eq(doctbl, req.avoidTabl) && len(req.avoidUids) > 0)
				q.whereNotIn(refm.io_oz_synuid, req.avoidUids);

			HashMap<String, DocRef> refs = ((AnResultset) q
				.rs(ctx)
				.rs(0))
				.map(docm.io_oz_synuid, (r) -> {
					return ((DocRef) r
							.getAnson(docm.uri))
							.syntabl(docm.tbl)
							.uids(r.getString(docm.io_oz_synuid));
				}, (r) -> {
					return Regex.startsEvelope(r.getString(docm.uri));
				});

			return resp.docrefs(doctbl, refs);
		}

		return resp;
	}

	/**
	 * @since 0.2.6
	 * @param req
	 * @param usr
	 * @return resp
	 * @throws SQLException 
	 * @throws TransException 
	 */
	SyncResp onQueryJservs(SyncReq req, DocUser usr) throws TransException, SQLException {
		SynodeMeta m = domanager0.synm;
		String jserv = (String)req.data(m.jserv);
		return (SyncResp) new SyncResp(domain)
				.jservs(AppSettings.loadJservss(st, domanager0.syngleton.syncfg, domanager0.synm))
				.data(m.jserv, jserv)
				.data(m.remarks, mode.name());
	}
	
	/**
	 * @since 0.2.6
	 * @param req
	 * @param usr
	 * @return response [data = update results]
	 * @throws TransException
	 * @throws SQLException
	 */
	SyncResp onReportJserv(SyncReq req, DocUser usr) throws TransException, SQLException {
		musteqs(req.exblock.domain, domain);
		musteqs(req.exblock.peer, synid);
		mustnonull(req.exblock.srcnode);
		
		SynodeMeta m = domanager0.synm;
		String jserv = (String)req.data(m.jserv);
		if (!JServUrl.valid(jserv))
			throw new ExchangeException(ExessionAct.ready, null, "Invalid Jserv: %s", jserv);

		String optim = (String)req.data(m.jserv_utc);
		domanager0.updateJserv(st, req.exblock.srcnode, jserv, optim);

		return onQueryJservs(req, usr);
	}

	/** @since 0.2.5 */
	private HashMap<String, BlockChain> blockChains;

	/**
	 * Accept Doc pushing to doc-refs.
	 * @param req
	 * @param usr
	 * @return response / reply
	 * @throws SAXException 
	 * @throws SQLException 
	 * @throws TransException 
	 * @throws IOException 
	 * @since 0.2.5
	 */
	public SyncResp onDocRefPushStart(SyncReq req, DocUser usr)
			throws IOException, TransException, SQLException, SAXException {
		String conn = Connects.uri2conn(req.uri());
		String tbl  = req.docref.syntabl;

		// source node == peer node,  uids is exists, timestamps match
		checkBlock0(st, conn, req, (DocUser) usr);

		if (blockChains == null)
			blockChains = new HashMap<String, BlockChain>();

		String tempDir = ((DocUser)usr).touchTempDir(conn, tbl);

		BlockChain chain = new BlockChain(tbl, tempDir, req.exblock.srcnode, req.doc);

		String id = ExpDoctier.chainId(usr, req.docref.uids);

		if (blockChains.containsKey(id))
			throw new SemanticException("Why started again?");

		blockChains.put(id, chain);
		return new SyncResp().doc(chain.doc)
				.blockSeq(-1);
	}

	/**
	 * 
	 * @param st
	 * @param conn
	 * @param req
	 * @param usr
	 * @since 0.2.5
	 */
	private void checkBlock0(DATranscxt st, String conn, SyncReq req, DocUser usr) {
		mustnonull(req.docref);
		mustnonull(req.docref.syntabl);
		mustnonull(req.docref.uids);
		musteqs(req.docref.uids, req.doc.uids);
		mustnonull(req.exblock);
		mustnonull(req.exblock.srcnode);
	}

	/**
	 * @param req
	 * @param usr
	 * @return
	 * @throws Exception
	 * @since 0.2.5
	 */
	public SyncResp onDocRefUploadBlock(SyncReq req, DocUser usr)
			throws Exception {
		String id = ExpDoctier.chainId(usr, req.docref.uids);
		if (!blockChains.containsKey(id))
			throw new SemanticException("Uploading blocks must be accessed after starting chain is confirmed.");

		BlockChain chain = blockChains.get(id);
		chain.appendBlock(req);
		
		if (chain.falshedOut().blockSeq() >= req.blockSeq)
			stepBreakpoint(chain.doc, ((SyncReq)chain.falshedOut()), usr);

		return new SyncResp()
				.blockSeq(req.blockSeq())
				.doc(chain.doc);
	}

	/**
	 * 
	 * @param req
	 * @param usr
	 * @return reply
	 * @throws SemanticException
	 * @throws SQLException
	 * @throws IOException
	 * @throws Exception
	 * @since 0.2.5
	 */
	public SyncResp onDocRefEndBlock(SyncReq req, DocUser usr)
			throws SemanticException, SQLException, IOException, Exception {

		String chaid = ExpDoctier.chainId(usr, req.docref.uids);
		BlockChain chain = null;
		if (blockChains.containsKey(chaid)) {
			blockChains.get(chaid).closeChain();
			chain = blockChains.remove(chaid);
		} else
			throw new SemanticException("Ending a block chain which is not exists.");

		verifyBlock9(chain, req);
		
		String conn = Connects.uri2conn(req.uri());

		String targetPath = ref2physical(conn, req.docref, usr, req.exblock.srcnode);

		// move file
		Utils.touchDir(targetPath);
		Files.move(Paths.get(chain.outputPath), Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);

		chain.doc.recId(req.docref.docId);
		
		if (debug)
			Utils.logT(new Object() {}, " %s\n-> %s", chain.outputPath, targetPath);

		return new SyncResp()
				.blockSeq(req.blockSeq())
				.doc(chain.doc);
	}

	/**
	 * 
	 * @param chain
	 * @param req
	 * @since 0.2.5
	 */
	private void verifyBlock9(BlockChain chain, SyncReq req) {
		 musteqs(req.docref.syntabl, chain.docTabl);
		 musteqs(req.docref.uids, chain.doc.uids);
		 musteqs(req.exblock.peer, domanager0.synode);
		 musteqs(req.exblock.srcnode, chain.doc.device());
	}

	/**
	 * 
	 * @param doc
	 * @param reqBlk
	 * @param usr
	 * @throws Exception
	 * @since 0.2.5
	 */
	private void stepBreakpoint(ExpSyncDoc doc, IBlock reqBlk, IUser usr)
			throws Exception {

		if (reqBlk instanceof SyncReq) {
			SyncReq req = (SyncReq) reqBlk;
			// update range into doc-ref

			String conn = Connects.uri2conn(req.uri());
			String doctbl = req.docref.syntabl;
			ExpDocTableMeta docm = (ExpDocTableMeta) Connects.getMeta(conn, doctbl);
			SynDocRefMeta rfm = domanager0.refm;
			String peer = req.exblock.srcnode;

			musteqi((int)req.range[0], (int)req.blockSeq * AESHelper.blockSize());
			mustge((long)(req.blockSeq + 1) * AESHelper.blockSize(), (long)req.range[1]);
			DocRef docref = req.docref.breakpoint(req.range[1]);
			
			DBSyntableBuilder st = new DBSyntableBuilder(domanager0);
			st.update(docm.tbl, usr)
			  .nv(docm.uri, docref.toBlock())
			  .whereEq(docm.io_oz_synuid, docref.uids)
			  .whereEq(docm.io_oz_synuid, st
					.select(rfm.tbl)
					.col(rfm.io_oz_synuid)
					.whereEq(rfm.syntabl, doctbl)
					.whereEq(rfm.fromPeer, peer)
					.whereEq(rfm.io_oz_synuid, docref.uids))
			  .u(st.nonsemantext());
		}
	}

	/**
	 * Update uri without triggering semantics, because the ext-file
	 * handler doesn't allow update a uri field.
	 * 
	 * The value generator is the same of the ext-file semantics handler.
	 * 
	 * @param conn
	 * @param docref
	 * @param usr
	 * @return target path 
	 * @throws Exception 
	 */
	private String ref2physical(String conn, DocRef docref, DocUser usr, String ref2peer)
			throws Exception {
		ExpDocTableMeta meta = (ExpDocTableMeta) Connects.getMeta(conn, docref.syntabl);

		ExtFilePaths extpths = DocRef.createExtPaths(conn, docref.syntabl, docref);
		String targetpth = extpths.decodeUriPath();
		
		DBSyntableBuilder st = new DBSyntableBuilder(domanager0);
		SynDocRefMeta refm = domanager0.refm;

		SemanticObject res = st
			.update(meta.tbl, usr)
			.nv(meta.uri, extpths.dburi(true))
			.whereEq(meta.io_oz_synuid, docref.uids)
			.post(st.delete(refm.tbl)
				.whereEq(refm.io_oz_synuid, docref.uids)
				.whereEq(refm.fromPeer, ref2peer)
				.whereEq(refm.syntabl, meta.tbl))
			.u(st.nonsemantext());
		
		if(1 != res.total()) {
			Utils.warnT(new Object() {},
					"Failed to remove/resovle doc-ref. pid: %s, syn-uids: %s",
					docref.docId, docref.uids);
			return null;
		}
		
		return targetpth;
	}

	/**
	 * 
	 * @param req
	 * @param usr
	 * @return acknowledge
	 * @throws IOException
	 * @throws TransException
	 * @since 0.2.5
	 */
	public SyncResp onDocRefAbortBlock(SyncReq req, DocUser usr)
			throws IOException, TransException {
		String id = ExpDoctier.chainId(usr, req.docref.uids);
		SyncResp ack = new SyncResp();

		if (blockChains.containsKey(id)) {
			blockChains.get(id).abortChain();
			blockChains.remove(id);
			ack.blockSeq(req.blockSeq());
		} else
			ack.blockSeq(-1);

		return ack;
	}

}
