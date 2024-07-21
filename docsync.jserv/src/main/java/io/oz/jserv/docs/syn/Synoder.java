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
import io.odysz.semantic.meta.SyntityMeta;
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
	
	HashMap<String, HashMap<String, ExessionPersist>> domains;
	
	/**
	 * Get my syn-transact-builder for the domain. 
	 * @param domain
	 * @return builder
	 */
	DBSyntableBuilder trb(String domain) {
		return synssion(domain, synode).trb;
	}

	HashMap<String, ExessionPersist> synssions(String domain) {
		return domains != null ? domains.get(domain) : null;
	}

	ExessionPersist synssion(String domain, String jserv) {
		return domains != null && domains.containsKey(domain)
			? domains.get(domain).get(jserv)
			: null;
	}

	void synssion(String domain, String peer, ExessionPersist cp) throws ExchangeException {
		if (domains == null)
			domains = new HashMap<String, HashMap<String, ExessionPersist>>();
		if (!domains.containsKey(domain))
			domains.put(domain, new HashMap<String, ExessionPersist>());

		if (synssion(domain, peer) != null || synssion(domain, peer).exstate() != ready)
			throw new ExchangeException(ready, synssion(domain, peer),
				"Session for synching to %s already exists at %s",
				peer, synode);

		domains.get(domain).put(peer, cp);
	}

	private ExessionPersist delession(String dom, String peer) {
		if (domains != null && domains.containsKey(dom))
			return domains.get(dom).remove(peer);
		return null;
	}

	public Synoder(String myid, SyntityMeta ... meta) {
		synode = myid;
	}

	public SyncReq joinpeer(String domain, String peerserv, String myconn, String admin, String passwd)
			throws SQLException, TransException, SAXException, IOException {

		DBSyntableBuilder cltb = new DBSyntableBuilder(myconn, synode, SynodeMode.peer)
				.loadNyquvect0(myconn);

		// sign up as a new domain
		ExessionPersist cltp = new ExessionPersist(cltb, admin);

		ExchangeBlock req  = cltb.domainSignup(cltp, admin);

		synssion(domain, admin, cltp);
		return new SyncReq(null, null).exblock(req);
	}

	public SyncResp onjoin(SyncReq req, String myconn)
			throws SQLException, TransException, SAXException, IOException {
		DBSyntableBuilder admb = new DBSyntableBuilder(myconn, synode, SynodeMode.peer)
				.loadNyquvect0(myconn);

		ExessionPersist admp = new ExessionPersist(admb, req.exblock.srcnode);
		ExchangeBlock resp = admb.addMyChild(admp, req.exblock, "TODO org");

		return new SyncResp().exblock(resp);
	}

	public SyncResp closejoin(String domain, SyncResp rep) throws TransException, SQLException {
		String admin = rep.exblock.srcnode;
		ExessionPersist cltp = synssion(domain, admin);
		ExchangeBlock ack  = cltp.trb.initDomain(cltp, admin, rep.exblock);
		delession(domain, admin);
		return new SyncResp().exblock(ack);
	}

	public Nyquence nyquence(String domain, String node) {
		Map<String, Nyquence> nv = nyquvect(domain);
		return nv == null ? null
			: nv.get(node);
	}

	public Map<String, Nyquence> nyquvect(String domain) {
		return synssion(domain, synode).trb.nyquvect;
	}

	/**
	 * N0 in all domain should be the same.
	 * 
	 * @param domain
	 * @return n0
	 */
	public Nyquence n0(String domain) {
		return synssion(domain, synode).trb.n0();
	}

	/**
	 * Start this node running on {@code domain}.
	 * @param mod
	 * @return
	 */
	public Synoder start(String domain, SynodeMode mod) {
		return this;
	}

	public SyncReq syninit(String peer, String jserv, String myconn, String domain)
			throws SQLException, TransException, SAXException, IOException {
		DBSyntableBuilder b0 = new DBSyntableBuilder(myconn, synode, SynodeMode.peer)
				.loadNyquvect0(myconn);

		ExessionPersist xp = new ExessionPersist(b0, peer);
		synssion(domain, peer, xp);
		ExchangeBlock b = b0.initExchange(xp, peer);

		return new SyncReq(null, domain)
				.exblock(b);
	}

	public SyncResp onsyninit(String peer, String myconn, SyncReq ini)
			throws SQLException, TransException, SAXException, IOException {
		DBSyntableBuilder b0 = new DBSyntableBuilder(myconn, synode, SynodeMode.peer)
				.loadNyquvect0(myconn);

		ExessionPersist sp = new ExessionPersist(b0, peer, ini.exblock);
		ExchangeBlock b = b0.onInit(sp, ini.exblock);

		return new SyncResp()
				.exblock(b);
	}

	public SyncReq syncdb(String domain, String peer, SyncResp rep) throws SQLException, TransException {
		ExchangeBlock reqb = synssion(domain, peer)
				.nextExchange(rep.exblock);

		SyncReq req = new SyncReq(null, domain)
				.exblock(reqb);
		return req;
	}
	
	public SyncResp onsyncdb(String domain, String peer, SyncReq req)
			throws SQLException, TransException {
		ExchangeBlock repb = synssion(domain, peer)
				.nextExchange(req.exblock);

		return new SyncResp().exblock(repb);
	}

	public SyncReq synclose(String domain, String peer, SyncResp rep)
			throws TransException, SQLException {
		ExessionPersist xp = synssion(domain, peer);
		ExchangeBlock b = xp.trb.closexchange(xp, rep.exblock);
		return new SyncReq(null, domain).exblock(b);
	}

	public SyncResp onsynclose(String domain, String peer, SyncReq req)
			throws TransException, SQLException {
		ExessionPersist xp = synssion(domain, peer);
		ExchangeBlock b = xp.trb.onclosexchange(xp, req.exblock);
		return new SyncResp().exblock(b);
	}

	/**
	 * Initialize n0 and samp.
	 * @param n0
	 * @param stamp
	 * @return this
	 * @throws TransException 
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws SAXException 
	 */
	public Synoder born(String conn, long n0, long stamp0, String org)
			throws SQLException, TransException, SAXException, IOException {
		SynodeMeta snm = new SynodeMeta(conn);
		DATranscxt t0 = new DATranscxt(conn);
		IUser robot = new JRobot();

		if (DAHelper.count(t0, conn, snm.tbl, snm.synuid, synode) > 0)
			DAHelper.updateFieldsByPk(robot, t0, conn, snm, synode, snm.nyquence, n0, snm.nstamp, stamp0);
		else
			DAHelper.insert(robot, t0, conn, snm,
					snm.synuid, synode,
					snm.nyquence, n0,
					snm.nstamp, stamp0,
					snm.org, org,
					snm.mac, "#"
					);

		return this;
	}

}
