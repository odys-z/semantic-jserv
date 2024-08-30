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
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.util.DAHelper;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.ExchangeException;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

/**
 * Syn-domain's sessions manager.
 * @see #sessions
 */
public class SynDomanager implements OnError {
	static final String dom_unknown = null;
	
	final String synode;
	final String myconn;
	final String domain;
	final String org;
	final SynodeMode mod;
	
	/** {peer: session-persist} */
	HashMap<String, SynssionClientier> sessions;
	
	/** Expired, only for tests. */
	public SynssionClientier expiredClientier;

	public Nyquence lastn0(String peer) {
		return expiredClientier == null || expiredClientier.xp == null ?
				null : expiredClientier.xp.n0();
	}

	SynssionClientier synssion(String peer) {
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

	public SynDomanager(String org, String dom, String myid, String conn, SynodeMode mod) {
		synode   = myid;
		myconn   = conn;
		domain   = dom;
		this.org = org;
		this.mod = mod;
	}
	
	public static SynDomanager clone(SynDomanager dm) {
		return new SynDomanager(dm.org, dm.domain, dm.synode, dm.myconn, dm.mod);
	}

	/**
	 * @param adminjserv jserv root path, must be null for testing
	 * locally without login to the service
	 * @param peeradmin
	 * @param passwd
	 * @return SynssionClient (already in been put into synssions),
	 * with xp for persisting and for req construction.
	 * @throws Exception
	 */
	public SynssionClientier joinpeer(String adminjserv, String peeradmin, String userId, String passwd) throws Exception {

		DBSyntableBuilder cltb = new DBSyntableBuilder(dom_unknown, myconn, synode, mod);

		// sign up as a new domain
		ExessionPersist cltp = new ExessionPersist(cltb, peeradmin);

		SynssionClientier c = new SynssionClientier(this, peeradmin)
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

	public SyncResp onjoin(SyncReq req) throws Exception {
		String peer = req.exblock.srcnode;
		DBSyntableBuilder admb = new DBSyntableBuilder(domain, myconn, synode, mod);

		ExessionPersist admp = new ExessionPersist(admb, peer);

		ExchangeBlock resp = admb.domainOnAdd(admp, req.exblock, org);

		synssion(peer, new SynssionClientier(this, peer).xp(admp.exstate(ready)).domain(domain));
	
		return new SyncResp(domain).exblock(resp);
	}

	public SyncReq closejoin(SyncResp rep) throws TransException, SQLException {
		if (!eq(domain, rep.domain))
			throw new ExchangeException(close, null, "So what?");

		String admin = rep.exblock.srcnode;
		try {
			return synssion(admin).domain(domain).closejoin(admin, rep);
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
	 */
	public SyncReq syninit(String peer, String domain) throws Exception {
		DBSyntableBuilder b0 = new DBSyntableBuilder(domain, myconn, synode, mod);

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
		SynssionClientier c = new SynssionClientier(this, peer);
		synssion(peer, c);
		return c.onsyninit(ini, domain);
	}

	public SyncResp onclosex(SyncReq req) throws TransException, SQLException {
		SynssionClientier c = synssion(req.exblock.srcnode);
		return c.onsynclose(req.exblock);
	}

	/**
	 * Insert synode record with n0 and stamp.
	 * @param handlers syn handlers  
	 * @param n0 accept as start nyquence if no records exists
	 * @param stamp accept as start stamp if no records exists
	 * @return this
	 * @throws Exception 
	 */
	public SynDomanager born(List<SemanticHandler> handlers, long n0, long stamp0)
			throws Exception {
		SynodeMeta snm = new SynodeMeta(myconn);
		DATranscxt b0 = new DATranscxt(null);
		IUser robot = new JRobot();

		if (DAHelper.count(b0, myconn, snm.tbl, snm.synuid, synode) > 0)
			Utils.warnT(new Object() {}, "What's it when reached here?");
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
			if (h instanceof ShSynChange)
			DBSyntableBuilder.registerEntity(myconn, ((ShSynChange)h).entm);

		return this;
	}

	///////////////////////////////////////////////////////////////////////////
	// ArrayList<String> knownpeers;
	/**
	 * Update domain, and start all possible sessions, each in a new thread.
	 * Can be called by request handler and timer.
	 * 
	 * <p>Updating event is ignored if the clientier is running.</p>
	 * 
	 * @return this
	 * @throws IOException 
	 * @throws SsException 
	 * @throws AnsonException 
	 * @throws SemanticException 
	 */
	public SynDomanager updomains() throws SemanticException, AnsonException, SsException, IOException {
		if (sessions != null)
		for (String peer : sessions.keySet())
			if (sessions.containsKey(peer)
				&& sessions.get(peer).xp != null && sessions.get(peer).xp.exstate() == ready)
				sessions.get(peer).update2peer();
			// else sessions.put(peer, new SynssionClientier(this, peer, domain).updateWith());
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
	 */
	public void joinDomain(String dom, String admid, String admserv,
			String myuid, String mypswd, OnOk... ok) throws Exception {

		if (sessions != null && sessions.containsKey(admid))
			throw new ExchangeException(close, null,
				"SynssionClientier already exists. Duplicated singup?");
		
		SynssionClientier c = joinpeer(admserv, admid, myuid, mypswd);

		c.joindomain(admid, myuid, mypswd, ok);
	}

	@Override
	public void err(MsgCode code, String msg, String... args) {
		Utils.warn("Error Code: %s", code.name());
		Utils.warn(msg, (Object[])args);
	}
}
