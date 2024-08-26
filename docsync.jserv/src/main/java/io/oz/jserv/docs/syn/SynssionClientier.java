package io.oz.jserv.docs.syn;

import static io.odysz.semantic.syn.ExessionAct.close;

import java.sql.SQLException;

import io.odysz.semantic.syn.DBSyntableBuilder;
import io.odysz.semantic.syn.ExchangeBlock;
import io.odysz.semantic.syn.ExessionPersist;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.transact.x.TransException;

public class SynssionClientier {

	String mynid;
	String conn;
	String peer;
	String domain;

	SynodeMode mode;
	SynDomanager domanager;

	public SynssionClientier(SynDomanager domanager, String peer, String domain) {
		this.conn = domanager.myconn;
		this.mynid = domanager.synode;
		this.domanager = domanager;
		this.peer = peer;
		this.domain = domain;
		this.mode = domanager.mod;
	}

	public void start() {
		new Thread(() -> { 
			try {
				// start session
				ExchangeBlock reqb  = exesinit();
				SyncResp rep = exespush(peer, reqb);

				if (rep != null) {
					// on start reply
					exesOninit(rep);
					while (rep.synact() != close || reqb.synact() != close) {
						// See SynoderTest
						// req = syncdb(peer, rep);
						// rep = srv.onsyncdb(clt.synode, req);
						ExchangeBlock exb = syncdb(rep.exblock);
						rep = exespush(peer, exb);
					}
					
					// close
					reqb = synclose(rep.exblock);
					rep = exesclose(peer, reqb);
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
	ExchangeBlock exesinit() throws Exception {
		// DBSyntableBuilder b0 = new DBSyntableBuilder(domain, conn, mynid, mode);

		// ExessionPersist xp = new ExessionPersist(b0, peer)
								// .loadNyquvect(conn);

		// b0 = xp.trb;
		ExchangeBlock b = xp.trb.initExchange(xp);

//		return new SyncReq(null, domain)
//					.exblock(b);
		return b;
	}

	SyncResp onsyninit(ExchangeBlock ini)
			throws Exception {
		DBSyntableBuilder b0 = new DBSyntableBuilder(domain, conn, mynid, mode);

		ExessionPersist xp = new ExessionPersist(b0, peer, ini)
								.loadNyquvect(conn);

		ExchangeBlock b = b0.onInit(xp, ini);

		domanager.synssion(peer, this);
		return new SyncResp()
				.exblock(b);
	}

	ExchangeBlock syncdb(ExchangeBlock rep)
			throws SQLException, TransException {

		ExchangeBlock reqb = xp // domanager.synssion(peer)
						.nextExchange(rep);

//		SyncReq req = new SyncReq(null, domain)
//						.exblock(reqb);
//		return req;
		return reqb;
	}
	
	ExchangeBlock onsyncdb(SyncReq req)
			throws SQLException, TransException {
		return onsyncdb(req.exblock);
	}

	public ExchangeBlock onsyncdb(ExchangeBlock reqb)
			throws SQLException, TransException {
		ExchangeBlock repb = xp.nextExchange(reqb);
		return repb;
	}

	ExchangeBlock synclose(ExchangeBlock rep)
			throws TransException, SQLException {
		// try {
		// ExessionPersist xp = synssion(peer);
			ExchangeBlock b = xp.trb.closexchange(xp, rep);
			// return new SyncReq(null, domain).exblock(b);
			return b;
		// } finally { expiredxp = delession(peer); }
	}

	SyncResp onsynclose(ExchangeBlock reqb)
			throws TransException, SQLException {
		// try {
		// ExessionPersist xp = synssion(peer);
		ExchangeBlock b = xp.trb.onclosexchange(xp, reqb);
		return new SyncResp().exblock(b);
		// } finally { expiredxp = delession(peer); }
	}

	/**
	 * Initialize an exchange session.
	 * @param ini
	 * @return initializing request
	 * @throws Exception 
	 */
	SyncResp exesOninit(SyncResp ini) throws Exception {
		DBSyntableBuilder b0 = new DBSyntableBuilder(domain, conn, mynid, mode);

		xp = new ExessionPersist(b0, peer, ini.exblock)
								.loadNyquvect(conn);

		ExchangeBlock b = b0.onInit(xp, ini.exblock);

		// synssion(peer, new SynssionClientier(this, peer, domain).xp(xp));

		return new SyncResp().exblock(b);
	}

	SyncResp exespush(String peer, ExchangeBlock req) {
		return null;
	}

	SyncResp exesclose(String peer, ExchangeBlock req) {
		return null;
	}


	ExessionPersist xp;

	public SynssionClientier xp(ExessionPersist xp) {
		this.xp = xp;
		return this;
	}

	public void pingPeers() {
	}
}
