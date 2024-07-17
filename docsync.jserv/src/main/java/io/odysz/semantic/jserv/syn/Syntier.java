package io.odysz.semantic.jserv.syn;

import static io.odysz.semantic.syn.ExessionAct.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.meta.SyntityMeta;
import io.odysz.semantic.syn.DBSyntableBuilder;
import io.odysz.semantic.syn.ExchangeBlock;
import io.odysz.semantic.syn.ExessionPersist;
import io.odysz.semantic.syn.Nyquence;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.x.ExchangeException;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

public class Syntier extends ServPort<SyncReq> {
	private static final long serialVersionUID = 1L;

	// String jserv;
	public static final int jservx = 0;
	public static final int myconx = 1;

	String synode;

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

	public Syntier(SyntityMeta ... meta) {
		super(Port.dbsyncer);
	}

	public Syntier joinpeer(String peerserv, String synode, String passwd) {

		return this;
	}

	public Syntier regist(SyntityMeta meta) {
		return this;
	}

	public Nyquence nyquence(String domain, Syntier y) {
		Map<String, Nyquence> nv = nyquence(domain);
		return nv == null ? null
			: nv.get(y.synode);
	}

	public Map<String, Nyquence> nyquence(String domain) {
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

	public Syntier start(SynodeMode peer) {
		return this;
	}

	public SyncReq syninit(String peer, String jserv, String myconn, String domain)
			throws SQLException, TransException, SAXException, IOException {
		DBSyntableBuilder b0 = new DBSyntableBuilder(myconn, synode, SynodeMode.peer)
				.loadNyquvect0(myconn);

		ExessionPersist xp = new ExessionPersist(b0, peer);
		synssion(domain, peer, xp);
		ExchangeBlock b = b0.initExchange(xp, peer);

		return new SyncReq(null, domain, synode)
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

		SyncReq req = new SyncReq(null, domain, synode)
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
		return new SyncReq(null, domain, synode).exblock(b);
	}

	public SyncResp onsynclose(String domain, String peer, SyncReq req)
			throws TransException, SQLException {
		ExessionPersist xp = synssion(domain, peer);
		ExchangeBlock b = xp.trb.onclosexchange(xp, req.exblock);
		return new SyncResp().exblock(b);
	}

	@Override
	protected void onGet(AnsonMsg<SyncReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		// TODO Auto-generated method stub
	}

	@Override
	protected void onPost(AnsonMsg<SyncReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		// TODO Auto-generated method stub
		
	}

}
