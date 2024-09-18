package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.semantic.syn.ExessionAct.close;
import static io.odysz.semantic.syn.ExessionAct.ready;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;

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
import io.odysz.semantic.syn.ExessionPersist;
import io.odysz.semantic.syn.Nyquence;
import io.odysz.semantic.syn.SyncRobot;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.util.DAHelper;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.ExchangeException;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.parts.Logic.op;
import io.odysz.transact.x.TransException;

/**
 * Syn-domain's sessions manager.
 * @see #sessions
 * @since 0.2.0
 */
public class SynDomanager implements OnError {
	/**
	 * @since 0.2.0
	 */
	@FunctionalInterface
	public interface OnDomainUpdate {
		public void ok(String domain, String mynid, String peer, ExessionPersist... xp);
	}

	static final String dom_unknown = null;
	
	public final String synode;
	final String myconn;
	final String domain;
	final String org;
	final SynodeMode synmod;
	final SynodeMeta synm;
	
	boolean dbg;
	
	/**
	 * {peer: session-persist}
	 * @since 0.2.0
	 */
	HashMap<String, SynssionClientier> sessions;
	
	/**
	 * Expired, only for tests.
	 * @since 0.2.0
	 */
	public SynssionClientier expiredClientier;

	OnError errHandler;

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

	public SynDomanager(SynodeMeta synm, String org, String dom, String myid, String conn, SynodeMode mod, boolean debug) {
		synode   = myid;
		myconn   = conn;
		domain   = dom;
		synmod   = mod;
		this.org = org;
		this.dbg = debug;
		this.synm= synm;
		
		errHandler = (e, r, a) -> {
			Utils.warn("Error code: %s,\n%s", e.name(), String.format(r, (Object[])a));
		};
	}
	
	public static SynDomanager clone(SynDomanager dm) {
		return new SynDomanager(dm.synm, dm.org, dm.domain, dm.synode, dm.myconn, dm.synmod, dm.dbg);
	}

	/**
	 * Sing up, then start a synssion to the peer, peeradmin.
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

		DBSyntableBuilder cltb = new DBSyntableBuilder(dom_unknown, myconn, synode, synmod);

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
	 * @return response
	 * @throws Exception
	 * @since 0.2.0
	 */
	public SyncResp onjoin(SyncReq req) throws Exception {
		String peer = req.exblock.srcnode;
		DBSyntableBuilder admb = new DBSyntableBuilder(domain, myconn, synode, synmod);

		ExessionPersist admp = new ExessionPersist(admb, peer);

		ExchangeBlock resp = admb.domainOnAdd(admp, req.exblock, org);

		// FIXME why need a Synssion here?
		synssion(peer, new SynssionClientier(this, peer, null)
				.xp(admp.exstate(ready))
				// .domain(domain)
				);
	
		return new SyncResp(domain).exblock(resp);
	}

	public SyncReq closejoin(SyncResp rep) throws TransException, SQLException {
		if (!eq(domain, rep.domain))
			throw new ExchangeException(close, null, "So what?");

		String admin = rep.exblock.srcnode;
		try {
			return synssion(admin).closejoin(admin, rep);
		/*
			ExessionPersist xp = synssion(admin).xp;
			xp.trb.domainitMe(xp, admin, rep.exblock);

			ExchangeBlock req = xp.trb.domainCloseJoin(xp, rep.exblock);
			return new SyncReq(null, domain)
					.exblock(req);
		*/
		} finally { expiredClientier = delession(admin); }
	}

	public SyncResp onclosejoin(SyncReq req) throws TransException, SQLException {
		String apply = req.exblock.srcnode;
		try {
			ExessionPersist sp = synssion(apply).xp;
			ExchangeBlock ack  = sp.trb.domainCloseJoin(sp, req.exblock);
			return new SyncResp(domain).exblock(ack);
		} finally { expiredClientier = delession(apply); }
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
	 */
	public SyncReq syninit(String peer, String domain) throws Exception {
		DBSyntableBuilder b0 = new DBSyntableBuilder(domain, myconn, synode, synmod);

		ExessionPersist xp = new ExessionPersist(b0, peer)
								.loadNyquvect(myconn);

		b0 = xp.trb;
		ExchangeBlock b = b0.initExchange(xp);

		return new SyncReq(null, domain)
					.exblock(b);
	}

	/**
	 * @param peer
	 * @param ini initial request
	 * @return exchange block
	 * @throws Exception
	 * @since 0.2.0
	 */
	public SyncResp onsyninit(String peer, ExchangeBlock ini) throws Exception {
		/*
		DBSyntableBuilder b0 = new DBSyntableBuilder(domain, myconn, synode, mod);

		ExessionPersist xp = new ExessionPersist(b0, peer, ini)
								.loadNyquvect(myconn);

		ExchangeBlock b = b0.onInit(xp, ini);

		synssion(peer, new SynssionClientier(this, peer, domain).xp(xp));

		return new SyncResp().exblock(b);
		*/
		SynssionClientier c = new SynssionClientier(this, peer, null);
		synssion(peer, c);
		return c.onsyninit(ini, domain);
	}

	public SyncResp onsyninit(SyncReq req) throws Exception {
		return onsyninit(req.exblock.srcnode, req.exblock);
	}

	public SyncResp onclosex(SyncReq req) throws TransException, SQLException {
		SynssionClientier c = synssion(req.exblock.srcnode);
		return c.onsynclose(req.exblock);
	}

	/**
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
		SynodeMeta snm = new SynodeMeta(myconn);
		DATranscxt b0 = new DATranscxt(null);
		IUser robot = new JRobot();

		if (DAHelper.count(b0, myconn, snm.tbl, snm.synoder, synode, snm.domain, domain) > 0)
			Utils.warnT(new Object() {}, "\n[ ♻.✩ ] Syn-domain manager restart upon domain '%s' ...", domain);
		else
			DAHelper.insert(robot, b0, myconn, snm,
					snm.synuid, synode,
					snm.pk, synode,
					snm.domain, domain,
					snm.nyquence, n0,
					snm.nstamp, stamp0,
					snm.org, org,
					snm.device, "#" + synode
					);
		
		if (handlers != null)
		for (SemanticHandler h : handlers)
			if (h instanceof ShSynChange) {
				DBSyntableBuilder.registerEntity(myconn, ((ShSynChange)h).entm);
				Utils.logi("SynEntity registed: %s - %s : %s", myconn, domain, ((ShSynChange)h).entm.tbl);
			}

		return this;
	}

	///////////////////////////////////////////////////////////////////////////
	/**
	 * Update (synchronize) this domain, each peer in a new thread.
	 * Can be called by request handler and timer.
	 * 
	 * <p>Updating event is ignored if the clientier is running.</p>
	 * 
	 * @return this
	 * @throws IOException 
	 * @throws SsException 
	 * @throws AnsonException 
	 * @throws SemanticException 
	 * @since 0.2.0
	 */
	public SynDomanager updomains(OnDomainUpdate onUpdate) throws SemanticException, AnsonException, SsException, IOException {
		if (sessions == null || sessions.size() == 0)
			throw new ExchangeException(ready, null,
						"Session pool is null at %s", synode);

		for (String peer : sessions.keySet())
			if (sessions.containsKey(peer)
				&& sessions.get(peer).xp != null && sessions.get(peer).xp.exstate() == ready)
				sessions.get(peer).asynUpdate2peer(onUpdate);
			else if (!sessions.containsKey(peer))
				Utils.warnT(new Object() {}, "Updating domain should be done after logged into %s, by %s",
						peer, synode);
			else if (sessions.get(peer).xp != null && sessions.get(peer).xp.exstate() == ready)
				continue;
			else
				Utils.warnT(new Object() {}, "TODO updating %s <- %s",
						peer, synode);

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

		c.asynJoindomain(admid, myuid, mypswd, (resp) -> {
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
				// .groupby(synm.domain)
				.groupby(synm.synoder)
				.where_(op.ne, synm.synoder, synode)
				.whereEq(synm.domain, domain)
				.rs(t0.instancontxt(myconn, robot))
				.rs(0);
		
		if (sessions == null)
			sessions = new HashMap<String, SynssionClientier>();
		
		while (rs.next()) {
			// String domain = rs.getString(synm.domain);
			SynssionClientier c = new SynssionClientier(this, rs.getString("peer"), rs.getString(synm.jserv))
								.onErr(errHandler);
			String peer = rs.getString("peer");

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
			
			Utils.logi("[ ♻.✩ %s ] SynssionClienter created: {clienturi: %s, conn: %s, mode: %s, peer: %s, peer-jserv: %s}",
					synode, c.clienturi, c.conn, c.mymode.name(), c.peer, c.peerjserv);
		}

		return this;
	}

	public SynDomanager openSynssions(SyncRobot dbrobot, OnDomainUpdate onEachOpen)
			throws AnsonException, SsException, IOException, TransException {

		for (SynssionClientier c : sessions.values()) {
			c.loginWithUri(c.peerjserv, dbrobot.uid(), dbrobot.pswd(), dbrobot.deviceId());
			c.asynUpdate2peer(onEachOpen);
		}
		return this;
	}
}
