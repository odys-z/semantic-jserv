package io.oz.jserv.docs.syn;

import static io.odysz.semantic.syn.ExessionAct.ready;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xml.sax.SAXException;

import io.odysz.semantic.DASemantics.SemanticHandler;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jserv.JRobot;
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
import io.odysz.transact.x.TransException;

public class Synoder {
	final String synode;
	final String myconn;
	final String domain;
	final String org;
	final SynodeMode mod;
	
	/** {synode: session-persist } */
	HashMap<String, ExessionPersist> sessions;
	DBSyntableBuilder mysynbuilder;
	public DBSyntableBuilder trb() { return mysynbuilder; }

	/**
	 * Get my syn-transact-builder for the session with the peer {@code withPeer}. 
	 * @param withPeer 
	 * @return builder
	 */
	DBSyntableBuilder trb(String withPeer) {
		ExessionPersist xp = synssion(withPeer);
		return xp == null ? null : xp.trb;
	}

	ExessionPersist synssion(String peer) {
		return sessions != null
				? sessions.get(peer)
				: null;
	}

	void synssion(String peer, ExessionPersist cp) throws ExchangeException {
		if (sessions == null)
			sessions = new HashMap<String, ExessionPersist>();

		if (synssion(peer) != null && synssion(peer).exstate() != ready)
			throw new ExchangeException(ready, synssion(peer),
				"Session for synching to %s already exists at %s",
				peer, synode);

		sessions.put(peer, cp);
	}

	private ExessionPersist delession(String peer) {
		if (sessions != null && sessions.containsKey(peer))
			return sessions.remove(peer);
		return null;
	}

	public Synoder(String org, String dom, String myid, String conn, SynodeMode mod)
			throws SQLException, SAXException, IOException, TransException {
		synode   = myid;
		myconn   = conn;
		domain   = dom;
		this.org = org;
		this.mod = mod;
	}

	public SyncReq joinpeer(String peeradmin, String passwd) throws Exception {

		DBSyntableBuilder cltb = new DBSyntableBuilder(domain, myconn, synode, mod)
				.loadNyquvect(myconn);

		// sign up as a new domain
		ExessionPersist cltp = new ExessionPersist(cltb, peeradmin);

		ExchangeBlock req  = cltb.domainSignup(cltp, peeradmin);

		synssion(peeradmin, cltp);
		return new SyncReq(null, domain).exblock(req);
	}

	public SyncResp onjoin(SyncReq req)
			throws Exception {
		DBSyntableBuilder admb = new DBSyntableBuilder(domain, myconn, synode, mod)
				.loadNyquvect(myconn);

		ExessionPersist admp = new ExessionPersist(admb, req.exblock.srcnode);
		ExchangeBlock resp = admb.domainOnAdd(admp, req.exblock, org);

		synssion(req.exblock.srcnode, admp.exstate(ready));
	
		return new SyncResp().exblock(resp);
	}

	public SyncReq closejoin(SyncResp rep) throws TransException, SQLException {
		String admin = rep.exblock.srcnode;
		try {
			ExessionPersist xp = synssion(admin);
			// ExchangeBlock ack  = 
			xp.trb.domainitMe(xp, admin, rep.exblock);

			ExchangeBlock req = xp.trb.domainCloseJoin(xp, rep.exblock);
			return new SyncReq(null, domain)
					.exblock(req);
		} finally { delession(admin); }
	}

	public SyncResp onclosejoin(SyncReq req) throws TransException, SQLException {
		String apply = req.exblock.srcnode;
		try {
			ExessionPersist sp = synssion(apply);
			ExchangeBlock ack  = sp.trb.domainCloseJoin(sp, req.exblock);
			return new SyncResp().exblock(ack);
		} finally { delession(apply); }
	}

	public Nyquence nyquence(String node) {
		Map<String, Nyquence> nv = nyquvect(node);
		return nv == null ? null
			: nv.get(node);
	}

	public Map<String, Nyquence> nyquvect(String peer) {
		return synssion(peer).trb.nyquvect;
	}

	/**
	 * Get n0 of the session with the {@link peer} synode.
	 * 
	 * @param peer to which peer the session's n0 to be retrieved
	 * @return n0 N0 in all sessions should be the same.
	 */
	public Nyquence n0(String peer) {
		return synssion(peer).trb.n0();
	}

	/**
	 * Initiate a synchronization exchange sesseion using my connection.
	 * @param peer
	 * @param jserv
	 * @param domain
	 * @return init request
	 * @throws Exception 
	 */
	public SyncReq syninit(String peer, String domain)
			throws Exception {
		DBSyntableBuilder b0 = new DBSyntableBuilder(domain, myconn, synode, mod)
				.loadNyquvect(myconn);

		ExessionPersist xp = new ExessionPersist(b0, peer);
		ExchangeBlock b = b0.initExchange(xp, peer);

		// synssion(peer, xp);
		return new SyncReq(null, domain)
				.exblock(b);
	}

	public SyncResp onsyninit(String peer, ExchangeBlock ini)
			throws Exception {
		DBSyntableBuilder b0 = new DBSyntableBuilder(domain, myconn, synode, mod)
				.loadNyquvect(myconn);

		ExessionPersist xp = new ExessionPersist(b0, peer, ini);
		ExchangeBlock b = b0.onInit(xp, ini);

		synssion(peer, xp);
		return new SyncResp()
				.exblock(b);
	}

	public SyncReq syncdb(String peer, SyncResp rep)
			throws SQLException, TransException {

		ExchangeBlock reqb = synssion(peer)
				.nextExchange(rep.exblock);

		SyncReq req = new SyncReq(null, domain)
				.exblock(reqb);
		return req;
	}
	
	public SyncResp onsyncdb(String peer, SyncReq req)
			throws SQLException, TransException {
		ExchangeBlock repb = synssion(peer)
				.nextExchange(req.exblock);

		return new SyncResp().exblock(repb);
	}

	public SyncReq synclose(String domain, String peer, SyncResp rep)
			throws TransException, SQLException {
		ExessionPersist xp = synssion(peer);
		ExchangeBlock b = xp.trb.closexchange(xp, rep.exblock);
		return new SyncReq(null, domain).exblock(b);
	}

	public SyncResp onsynclose(String domain, String peer, SyncReq req)
			throws TransException, SQLException {
		ExessionPersist xp = synssion(peer);
		ExchangeBlock b = xp.trb.onclosexchange(xp, req.exblock);
		return new SyncResp().exblock(b);
	}

	/**
	 * Initialize n0 and samp.
	 * @param handlers syn handlers  
	 * @param n0 accept as start nyquence if no records exists
	 * @param stamp accept as start stamp if no records exists
	 * @return this
	 * @throws Exception 
	 */
	public Synoder born(List<SemanticHandler> handlers, long n0, long stamp0)
			throws Exception {
		SynodeMeta snm = new SynodeMeta(myconn);
		DATranscxt b0 = new DATranscxt(null);
		IUser robot = new JRobot();

		if (DAHelper.count(b0, myconn, snm.tbl, snm.synuid, synode) > 0)
			; // DAHelper.updateFieldsByPk(robot, t0, myconn, snm, synode, snm.nyquence, n0, snm.nstamp, stamp0);
		else
			DAHelper.insert(robot, b0, myconn, snm,
					snm.synuid, synode,
					snm.pk, synode,
					snm.domain, domain,
					snm.nyquence, n0,
					snm.nstamp, stamp0,
					snm.org, org,
					snm.mac, "#"
					);
		
		mysynbuilder = new DBSyntableBuilder(domain, myconn, synode, mod)
							.loadNyquvect(myconn);
	
		if (handlers != null)
		for (SemanticHandler h : handlers)
			if (h instanceof ShSynChange)
			mysynbuilder.registerEntity(myconn, ((ShSynChange)h).entm);

		return this;
	}

}