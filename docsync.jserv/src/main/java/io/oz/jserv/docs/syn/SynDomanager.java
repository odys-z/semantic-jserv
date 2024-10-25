package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.musteq;
import static io.odysz.common.LangExt.notNull;
import static io.odysz.semantic.syn.ExessionAct.close;
import static io.odysz.semantic.syn.ExessionAct.deny;
import static io.odysz.semantic.syn.ExessionAct.init;
import static io.odysz.semantic.syn.ExessionAct.mode_client;
import static io.odysz.semantic.syn.ExessionAct.mode_server;
import static io.odysz.semantic.syn.ExessionAct.ready;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.SemanticHandler;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.JProtocol.OnError;
import io.odysz.semantic.jprotocol.JProtocol.OnOk;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.syn.DBSynmantics.ShSynChange;
import io.odysz.semantic.syn.DBSyntableBuilder;
import io.odysz.semantic.syn.ExchangeBlock;
import io.odysz.semantic.syn.ExessionAct;
import io.odysz.semantic.syn.ExessionPersist;
import io.odysz.semantic.syn.Nyquence;
import io.odysz.semantic.syn.SyncRobot;
import io.odysz.semantic.syn.SyndomContext;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.util.DAHelper;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.ExchangeException;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.parts.Logic.op;
import io.odysz.transact.x.TransException;
import io.oz.syn.SynodeConfig;

/**
 * Syn-domain's sessions manager.
 * 
 * <pre>Avoid dead lock ring
 *        A
 *       / \
 *      B - C
 * 
 * A is waiting B, B is waiting C, C is waiting A</pre>
 * 
 * To avoide this, a client will quite once the request is denied.
 * 
 * @see #sessions
 * @since 0.2.0
 */
public class SynDomanager extends SyndomContext implements OnError {
	/**
	 * @since 0.2.0
	 */
	@FunctionalInterface
	public interface OnDomainUpdate {
		/**
		 * On domain update event, for each peer. Additional calling for all peers cleared (peer == null).
		 * @param domain
		 * @param mynid
		 * @param peer
		 * @param xp
		 */
		public void ok(String domain, String mynid, String peer, ExessionPersist... xp);
	}

	/**
	 * @since 0.2.0
	 */
	@FunctionalInterface
	public interface OnBlocked {
		public int blockms(SyncJUser synlocker);
	 }

	static final String dom_unknown = null;

//	static SyndomContext loadomx(String dom, DATranscxt tb0) {
//		return null;
//	}

//	public final String synode;

	/**
	 * Privately managed syn-domain context
	final SyndomContext syndomx;
	 */
	
//	final String myconn; // TODO delete
//	final String domain;
	final String org;
//	final SynodeMode synmod;

//	final SynodeMeta synm;
	
	boolean dbg;
	
	/**
	 * {peer: session-persist}
	 * @since 0.2.0
	 */
	HashMap<String, SynssionClientier> sessions;
	
	/**
	 * Expired synssion, only for tests.
	 * @since 0.2.0
	 */
	public SynssionClientier expiredClientier;

	OnError errHandler;
	
	final DATranscxt tb0;

	public Nyquence lastn0(String peer) {
		return expiredClientier == null || expiredClientier.xp == null ?
				null : expiredClientier.xp.n0();
	}

	public SynssionClientier synssion(String peer) {
		return sessions != null
				? sessions.get(peer)
				: null;
	}

	SynDomanager synssion(String peer, SynssionClientier client) throws ExchangeException {
		if (sessions == null)
			sessions = new HashMap<String, SynssionClientier>();

		if (synssion(peer) != null && synssion(peer).xp.exstate() != ready)
			throw new ExchangeException(ready, synssion(peer).xp,
				"Session for synching to %s already exists at %s",
				peer, synode);

		sessions.put(peer, client);
		return this;
	}

	private SynssionClientier delession(String peer) {
		if (sessions != null && sessions.containsKey(peer))
			return sessions.remove(peer);
		return null;
	}

	public SynDomanager(SynodeMeta synm, String org, String dom, String myid,
			String conn, SynodeMode mod, boolean debug) throws Exception {

		super(mod, dom, myid, conn);

//		synode   = myid;
//		myconn   = conn;
//		domain   = dom;
//		synmod   = mod;

		this.org = org;
		this.dbg = debug;
//		this.synm= synm;
		
		errHandler = (e, r, a) -> {
			Utils.warn("Error code: %s,\n%s", e.name(), String.format(r, (Object[])a));
		};
		
		tb0 = new DATranscxt(conn);
		
		// syndomx = loadomx(dom, tb0);
	}
	
//	public static SynDomanager clone(SynDomanager dm) throws Exception {
//		return new SynDomanager(dm.synm, dm.org, dm.domain, dm.synode, dm.myconn, dm.synmod, dm.dbg);
//	}

	/**
	 * Sing up, then start a synssion to the peer, {@code peeradmin}.
	 * 
	 * @param adminjserv jserv root path, must be null for testing
	 * locally without login to the service
	 * @param peeradmin
	 * @param passwd
	 * @return SynssionClient (alneed to be put into synssions),
	 * with xp for persisting and for req construction.
	 * @throws Exception
	 * @since 0.2.0
	 */
	public SynssionClientier join2peer(String adminjserv, String peeradmin, String userId, String passwd) throws Exception {

		DBSyntableBuilder cltb = new DBSyntableBuilder(this);

		// sign up as a new domain
		ExessionPersist cltp = new ExessionPersist(cltb, peeradmin);

		SynssionClientier c = new SynssionClientier(this, peeradmin, adminjserv)
							.xp(cltp)
							.onErr(this);

		if (isblank(adminjserv))
			Utils.warnT(new Object() {},
				"The root jserv path is empty. This should only happens when testing.\npeer: %s, user id: %s",
				peeradmin, userId);
		else {
			c.loginWithUri(adminjserv, userId, passwd, synode);
			Utils.logi("%s logged into %s", synode, peeradmin);
		}

		synssion(peeradmin, c);
		return c;
	}

	/**
	 * @see #join2peer(String, String, String, String)
	 * @param req
	 * @param usr 
	 * @return response
	 * @throws Exception
	 * @since 0.2.0
	 */
	public SyncResp onjoin(SyncReq req, SyncJUser usr) throws Exception {

		String peer = req.exblock.srcnode;

		try {
			if (lockx(usr))  {
//			if (synlock.tryLock())  {
//				synlocker = peer;
//				synlockid = usr.sessionId();

				// DBSyntableBuilder admb = new DBSyntableBuilder(domain, myconn, synode, synmod);
				DBSyntableBuilder admb = new DBSyntableBuilder(this);

				ExessionPersist admp = new ExessionPersist(admb, peer);

				ExchangeBlock resp = admb.domainOnAdd(admp, req.exblock, org);

				// FIXME why need a Synssion here?
				synssion(peer, new SynssionClientier(this, peer, null)
						.xp(admp.exstate(ready)));
			
				return new SyncResp(domain()).exblock(resp);
			}
			else return trylater(peer);
		} catch (Exception e) {
			synlock.unlock();
			throw e;
		}
	}

	private SyncResp trylater(String peer) {
		return new SyncResp(domain()).exblock(
				new ExchangeBlock(domain(), synode, peer, null,
				new ExessionAct(mode_server, ExessionAct.trylater)));

	}
	
	private SyncResp lockerr(String peer) {
		return new SyncResp(domain()).exblock(
				new ExchangeBlock(domain(), synode, peer, null,
				new ExessionAct(mode_server, ExessionAct.lockerr)));
	}

	public SyncResp onclosejoin(SyncReq req) throws TransException, SQLException {
		String apply = req.exblock.srcnode;
		try {
			ExessionPersist sp = synssion(apply).xp;
			ExchangeBlock ack  = sp.trb.domainCloseJoin(sp, req.exblock);
			return new SyncResp(domain()).exblock(ack);
		} finally {
			try { expiredClientier = delession(apply); }
			catch (Throwable t) { t.printStackTrace(); }
			synlock.unlock();
		}
	}

	/**
	 * Get n0 of the session with the {@link peer} synode.
	 * 
	 * @param peer to which peer the session's n0 to be retrieved
	 * @return n0 N0 in all sessions should be the same.
	 * @since 0.2.0
	 */
	public Nyquence n0(String peer) {
		return synssion(peer).xp.n0();
	}

	/**
	 * Initiate a synchronization exchange session using my connection.
	 * @deprecated only for tests. Call {@link SynssionClientier#exesinit(SyncReq)} instead.
	 * @param peer
	 * @param jserv
	 * @param domain
	 * @return initiate request
	 * @throws Exception 
	 * @since 0.2.0
	public SyncReq syninit(String peer, String domain) throws Exception {
		DBSyntableBuilder b0 = new DBSyntableBuilder(domain, myconn, synode, synmod);

		ExessionPersist xp = new ExessionPersist(b0, peer);

		b0 = xp.trb;
		ExchangeBlock b = b0.initExchange(xp);

		return new SyncReq(null, domain)
					.exblock(b);
	}
	 */

	/**
	 * @param req
	 * @param usr initial request
	 * @return exchange block
	 * @throws Exception
	 * @since 0.2.0
	 */
	private SyncResp onsyninit(ExchangeBlock req, SyncJUser usr) throws Exception {
		if (DAHelper.count(tb0, synconn, synm.tbl, synm.synoder, req, synm.domain, domain()) == 0)
			throw new ExchangeException(init, null,
					"This synode, %s, cannot respond to exchange initiation without knowledge of %s.",
					synode, req);

		String peer = req.srcnode;
		musteq(peer, usr.deviceId());

		if (!synlock.tryLock())
			return trylater(peer);
		
		synlocker = usr;

		SynssionClientier c = new SynssionClientier(this, peer, null);
		synssion(peer, c); // rename clientier to worker?
		return c.onsyninit(req, domain());
	}

	public SyncResp onsyninit(SyncReq req, SyncJUser usr) throws Exception {
		if (synssion(req.exblock.srcnode) != null) {
			ExessionPersist xp = synssion(req.exblock.srcnode).xp;
			
			if (!eq(xp.peer(), req.exblock.srcnode))
				throw new ExchangeException(null, xp,
						"A session persisting context's peers are not matching, %s : %s",
						req.exblock.srcnode, xp.peer());

			if (xp.exstate() != ready && xp.exstate() != close)
				return new SyncResp(domain())
						.exblock(new ExchangeBlock(domain(), synode,
									xp.peer(), xp.session(),
									new ExessionAct(xp.exstat().exmode() == mode_server
										? mode_client : mode_server, deny)));
		}
		
		return onsyninit(req.exblock, usr);
	}

	public SyncResp onclosex(SyncReq req, SyncJUser usr) throws TransException, SQLException {
		SynssionClientier c = synssion(req.exblock.srcnode);
		
		if (!eq(synlocker.sessionId(), usr.sessionId()))
			return lockerr(c.peer);
		else {
			try { return c.onsynclose(req.exblock); }
			finally { synlock.unlock(); }
		}
	}
	
	public SynDomanager loadomain() {
		
		return this;
	}

	/**
	 * @deprecated replaced by {@link #loadb()}
	 * Born or reborn, with synode's n0 and stamp created, then load all configured
	 * tables of {@link ShSynChange}.
	 * 
	 * @param handlers syn handlers  
	 * @param n0 accept as start nyquence if no records exists
	 * @param stamp accept as start stamp if no records exists
	 * @return this
	 * @throws Exception 
	 * @since 0.2.0
	 */
	public SynDomanager born(List<SemanticHandler> handlers, long n0, long stamp0)
			throws Exception {
		// SynodeMeta snm = new SynodeMeta(synconn);
		DATranscxt b0 = new DATranscxt(null);
		IUser robot = new JRobot();

		if (DAHelper.count(b0, synconn, synm.tbl, synm.synoder, synode, synm.domain, domain()) > 0)
			Utils.warnT(new Object() {}, "\n[ ♻.✩ ] Syn-domain manager restart upon domain '%s' ...", domain());
		else
			DAHelper.insert(robot, b0, synconn, synm,
					synm.synuid, synode,
					synm.pk, synode,
					synm.domain, domain(),
					synm.nyquence, n0,
					synm.nstamp, stamp0,
					synm.org, org,
					synm.device, "#" + synode
					);
		
		if (handlers != null)
		for (SemanticHandler h : handlers)
			if (h instanceof ShSynChange) {
				Utils.logi("SynEntity registed: %s - %s : %s", synconn, domain(), ((ShSynChange)h).entm.tbl);
			}

		return this;
	}

	/**
	 * Update (synchronize) this domain, each peer in a new thread.
	 * Can be called by request handler and timer.
	 * 
	 * <p>Updating event is ignored if the clientier is running.</p>
	 * @param onUpdate callback 
	 * 
	 * @return this
	 * @throws IOException 
	 * @throws SsException 
	 * @throws AnsonException 
	 * @throws SemanticException 
	 * @throws InterruptedException 
	 * @since 0.2.0
	 */
	public SynDomanager updomains(OnDomainUpdate onUpdate, OnBlocked block)
			throws SemanticException, AnsonException, SsException, IOException, InterruptedException {
		if (sessions == null || sessions.size() == 0)
			throw new ExchangeException(ready, null,
						"Session pool is null at %s", synode);
		
		while (!synlock.tryLock()) {
			int wait = block.blockms(synlocker);
			if (wait < 0)
				return this;
			Thread.sleep(wait * 1000);
		}

		new Thread(() -> { 
		for (String peer : sessions.keySet()) {
			ExessionPersist xp = sessions.get(peer).xp;
			if (xp != null && xp.exstate() == ready)
				try {
					sessions.get(peer).update2peer();
				} catch (ExchangeException e) {
					e.printStackTrace();
				}
			else if (xp != null && xp.exstate() != ready)
				continue;
			else
				Utils.warnT(new Object() {}, "TODO updating %s <- %s",
						peer, synode);

			if (onUpdate != null)
				onUpdate.ok(domain(), synode, peer, xp);
		}

		if (onUpdate != null)
			onUpdate.ok(domain(), synode, null);
		}, f("%1$s [%2$s]", synode, domain()))
		.start();

		return this;
	}

	/**
	 * Background sign up.
	 * @param dom
	 * @param admid
	 * @param admserv
	 * @param myuid
	 * @param mypswd
	 * @param ok 
	 * @throws Exception
	 * @since 0.2.0
	 */
	public void joinDomain(String dom, String admid, String admserv,
			String myuid, String mypswd, OnOk ok) throws Exception {

		if (sessions != null && sessions.containsKey(admid))
			throw new ExchangeException(close, null,
				"SynssionClientier already exists. Duplicated singup?");
		
		SynssionClientier c = join2peer(admserv, admid, myuid, mypswd);

		c.joindomain(admid, myuid, mypswd, (resp) -> {
			sessions.put(admid, c);
			ok.ok(resp);
		});
	}

	@Override
	public void err(MsgCode code, String msg, String... args) {
		Utils.warn("Error Code: %s", code.name());
		Utils.warn(msg, (Object[])args);
	}

	public SynDomanager loadSynclients(DATranscxt t0, IUser robot)
			throws TransException, SQLException {
		
		AnResultset rs = (AnResultset) t0
				.select(synm.tbl)
				.col(synm.synoder, "peer").col(synm.domain).col(synm.jserv)
				.groupby(synm.synoder)
				.where_(op.ne, synm.synoder, synode)
				.whereEq(synm.domain, domain())
				.rs(t0.instancontxt(synconn, robot))
				.rs(0);
		
		if (sessions == null)
			sessions = new HashMap<String, SynssionClientier>();
		
		while (rs.next()) {
			String peer = rs.getString("peer");
			SynssionClientier c = new SynssionClientier(this, peer, rs.getString(synm.jserv))
								.onErr(errHandler);

			if (dbg && sessions.containsKey(peer)) {
				SynssionClientier target = sessions.get(peer);
				if ( !eq(c.domain(), target.domain())
				  || !eq(c.conn, target.conn)
				  || c.mymode != target.mymode
				  || c.peer != target.peer
				  || target.xp != null)
					throw new ExchangeException(ready, target.xp, "Forced verification failed.");
			}

			sessions.put(peer, c);
			
			Utils.logi("[ ♻.✩ %s ] SynssionClienter created: {conn: %s, mode: %s, peer: %s, peer-jserv: %s}",
					synode, c.conn, c.mymode.name(), c.peer, c.peerjserv);
		}

		return this;
	}

	/**
	 * Login to peers and synchronize.
	 * 
	 * @param dbrobot
	 * @param onok 
	 * @return this
	 * @throws AnsonException
	 * @throws SsException
	 * @throws IOException
	 * @throws TransException
	 */
	public SynDomanager openUpdateSynssions(SyncRobot dbrobot, OnDomainUpdate... onok)
			throws AnsonException, SsException, IOException, TransException {

		for (SynssionClientier c : sessions.values()) {
			c.loginWithUri(c.peerjserv, dbrobot.uid(), dbrobot.pswd(), dbrobot.deviceId());
			c.update2peer();
		}

		if (!isNull(onok))
				onok[0].ok(domain(), synode, null);

		return this;
	}


	////////////////////////////////////////////////////////////////////////////
	final ReentrantLock synlock = new ReentrantLock(); 
	SyncJUser synlocker;
//	int locktouchms = Integer.MIN_VALUE;
//	int lockexpire  = Integer.MAX_VALUE;
	
	public void unlockx(SyncJUser usr) {
		if (synlocker != null && eq(synlocker.sessionId(), usr.sessionId())) {
			synlock.unlock();
			synlocker = null;
		}
	}

	private boolean lockx(SyncJUser usr) {
		if (synlock.tryLock())
			synlocker = usr;
		return true;
	}

	public DBSyntableBuilder createSyntabuilder(SynodeConfig cfg) throws Exception {
		notNull(cfg);
		musteq(domain(), cfg.domain);
		musteq(synode, cfg.synode());
		musteq(synconn, cfg.synconn);

		return new DBSyntableBuilder(this);
	}
}
