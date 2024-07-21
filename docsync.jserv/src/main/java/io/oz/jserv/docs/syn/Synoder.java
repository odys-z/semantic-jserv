package io.oz.jserv.docs.syn;

import static io.odysz.semantic.syn.ExessionAct.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.xml.sax.SAXException;

import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantic.meta.SynodeMeta;
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

	// final DBSyntableBuilder st0;

	/**
	 * Get my syn-transact-builder for the domain. 
	 * @return builder
	 */
	DBSyntableBuilder trb() {
		ExessionPersist xp = synssion(synode);
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

		// st0 = new DBSyntableBuilder(domain, myconn, synode, mod);
	}

	public SyncReq joinpeer(String peerserv, String peeradmin, String passwd)
			throws SQLException, TransException, SAXException, IOException {

		DBSyntableBuilder cltb = new DBSyntableBuilder(domain, myconn, synode, mod)
				.loadNyquvect0(myconn);

		// sign up as a new domain
		ExessionPersist cltp = new ExessionPersist(cltb, peeradmin);

		ExchangeBlock req  = cltb.domainSignup(cltp, peeradmin);

		synssion(peeradmin, cltp);
		return new SyncReq(null, domain).exblock(req);
	}

	public SyncResp onjoin(SyncReq req)
			throws SQLException, TransException, SAXException, IOException {
		DBSyntableBuilder admb = new DBSyntableBuilder(domain, myconn, synode, mod)
				.loadNyquvect0(myconn);

		ExessionPersist admp = new ExessionPersist(admb, req.exblock.srcnode);
		ExchangeBlock resp = admb.addMyChild(admp, req.exblock, "TODO org");

		synssion(req.exblock.srcnode, admp.exstate(ready));
	
		return new SyncResp().exblock(resp);
	}

	public SyncResp closejoin(SyncResp rep) throws TransException, SQLException {
		String admin = rep.exblock.srcnode;
		ExessionPersist cltp = synssion(admin);
		ExchangeBlock ack  = cltp.trb.initDomain(cltp, admin, rep.exblock);
		delession(admin);
		return new SyncResp().exblock(ack);
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
	 * Start this node running on {@code domain}.
	 * @param mod
	 * @return
	public Synoder start(SynodeMode mod) {
		return this;
	}
	 */

	public SyncReq syninit(String peer, String jserv, String myconn, String domain)
			throws SQLException, TransException, SAXException, IOException {
		DBSyntableBuilder b0 = new DBSyntableBuilder(domain, myconn, synode, mod)
				.loadNyquvect0(myconn);

		ExessionPersist xp = new ExessionPersist(b0, peer);
		synssion(peer, xp);
		ExchangeBlock b = b0.initExchange(xp, peer);

		return new SyncReq(null, domain)
				.exblock(b);
	}

	public SyncResp onsyninit(String peer, String myconn, SyncReq ini)
			throws SQLException, TransException, SAXException, IOException {
		DBSyntableBuilder b0 = new DBSyntableBuilder(domain, myconn, synode, mod)
				.loadNyquvect0(myconn);

		ExessionPersist sp = new ExessionPersist(b0, peer, ini.exblock);
		ExchangeBlock b = b0.onInit(sp, ini.exblock);

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
	 * @param n0 accept as start nyquence if no records exists
	 * @param stamp accept as start stamp if no records exists
	 * @return this
	 * @throws TransException 
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws SAXException 
	 */
	public Synoder born(long n0, long stamp0)
			throws SQLException, TransException, SAXException, IOException {
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
		
		DBSyntableBuilder synb0 = new DBSyntableBuilder(domain, myconn, synode, mod);
		synb0.loadNyquvect0(myconn);

		return this;
	}

}
