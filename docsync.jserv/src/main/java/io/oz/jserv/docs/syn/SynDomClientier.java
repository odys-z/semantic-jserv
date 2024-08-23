package io.oz.jserv.docs.syn;

import static io.odysz.semantic.syn.ExessionAct.close;

import java.sql.SQLException;

import io.odysz.semantic.syn.DBSyntableBuilder;
import io.odysz.semantic.syn.ExchangeBlock;
import io.odysz.semantic.syn.ExessionPersist;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.transact.x.TransException;

public class SynDomClientier {

	String mynid;
	String conn;
	String peer;
	String domain;

	SynodeMode mode;
	SynDomanager domanager;

	public SynDomClientier(SynDomanager domanager, String peer, String domain) {
		this.conn = domanager.myconn;
		this.mynid = domanager.synode;
		this.domanager = domanager;
		this.peer = peer;
		this.domain = domain;
		this.mode = domanager.mod;
	}

	public void start() {
		new Thread(() -> { 
			// HashMap<String, ExessionPersist> ss = domanager.sessions;
			try {
				// start session
				SyncReq req  = syninit();
				SyncResp rep = exestart(req);

				if (rep != null) {
					// on start reply
					onsyninit(rep.exblock);
					while (rep.synact() != close || req.synact() != close) {
						// req = syncdb(peer, rep);
						// 
						// rep = srv.onsyncdb(clt.synode, req);
						req = syncdb(rep);
						rep = exespush(peer, req);
					}
					
					// close
					req = synclose(rep);
					rep = exesclose(peer, req);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}).start();
	}

	/**
	 * Initiate a synchronization exchange session using ExessionPersist provided by manager.
	 * @param peer
	 * @return initiate request
	 * @throws Exception 
	 */
	private SyncReq syninit() throws Exception {
		// DBSyntableBuilder b0 = new DBSyntableBuilder(domain, conn, mynid, mode);

		// ExessionPersist xp = new ExessionPersist(b0, peer)
								// .loadNyquvect(conn);

		// b0 = xp.trb;
		ExchangeBlock b = xp.trb.initExchange(xp);

		return new SyncReq(null, domain)
					.exblock(b);
	}

	public SyncResp onsyninit(ExchangeBlock ini)
			throws Exception {
		DBSyntableBuilder b0 = new DBSyntableBuilder(domain, conn, mynid, mode);

		ExessionPersist xp = new ExessionPersist(b0, peer, ini)
								.loadNyquvect(conn);

		ExchangeBlock b = b0.onInit(xp, ini);

		domanager.synssion(peer, this);
		return new SyncResp()
				.exblock(b);
	}

	public SyncReq syncdb(SyncResp rep)
			throws SQLException, TransException {

		ExchangeBlock reqb = xp // domanager.synssion(peer)
						.nextExchange(rep.exblock);

		SyncReq req = new SyncReq(null, domain)
						.exblock(reqb);
		return req;
	}
	
	public SyncResp onsyncdb(SyncReq req)
			throws SQLException, TransException {
		ExchangeBlock repb = xp //synssion(peer)
				.nextExchange(req.exblock);

		return new SyncResp().exblock(repb);
	}

	public SyncReq synclose(SyncResp rep)
			throws TransException, SQLException {
		// try {
		// ExessionPersist xp = synssion(peer);
			ExchangeBlock b = xp.trb.closexchange(xp, rep.exblock);
			return new SyncReq(null, domain).exblock(b);
		// } finally { expiredxp = delession(peer); }
	}

	public SyncResp onsynclose(SyncReq req)
			throws TransException, SQLException {
		// try {
		// ExessionPersist xp = synssion(peer);
		ExchangeBlock b = xp.trb.onclosexchange(xp, req.exblock);
		return new SyncResp().exblock(b);
		// } finally { expiredxp = delession(peer); }
	}

	SyncResp exestart(SyncReq req) {
		return null;
	}

	SyncResp exespush(String peer, SyncReq req) {
		return null;
	}

	SyncResp exesclose(String peer, SyncReq req) {
		return null;
	}


	ExessionPersist xp;

//	public SynDomClientier exstate(int s) {
//		xp.exstate(s);
//		return this;
//	}

	public SynDomClientier xp(ExessionPersist xp) {
		this.xp = xp;
		return this;
	}
}
