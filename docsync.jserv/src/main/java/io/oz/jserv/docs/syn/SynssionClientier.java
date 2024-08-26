package io.oz.jserv.docs.syn;

import static io.odysz.semantic.syn.ExessionAct.close;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.locks.ReentrantLock;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.jclient.SessionClient;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.JProtocol.OnError;
import io.odysz.semantic.syn.DBSyntableBuilder;
import io.odysz.semantic.syn.ExchangeBlock;
import io.odysz.semantic.syn.ExessionPersist;
import io.odysz.semantic.syn.SynodeMode;
import io.oz.jserv.docs.syn.SyncReq.A;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

public class SynssionClientier {

	String mynid;
	String conn;
	String peer;
	String domain;

	SynodeMode mymode;
	SynDomanager domanager;
	DBSyntableBuilder b0; 
	OnError errHandler;

	protected SessionClient client;

	final ReentrantLock lock;

	public SynssionClientier(SynDomanager domanager, String peer, String domain) {
		this.conn = domanager.myconn;
		this.mynid = domanager.synode;
		this.domanager = domanager;
		this.peer = peer;
		this.domain = domain;
		this.mymode = domanager.mod;
		
		lock = new ReentrantLock();
	}

	public SynssionClientier update() {
		new Thread(() -> { 
			try {
				// start session
				lock.lock();
				ExchangeBlock reqb = exesinit();
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
			} catch (IOException e) {
				Utils.warn(e.getMessage());
			} catch (Exception e) {
				e.printStackTrace();
			}
			finally {
				lock.unlock();
			}
		}).start();
		return this;
	}

	/**
	 * Initiate a synchronization exchange session using ExessionPersist provided by manager.
	 * @param peer
	 * @return initiate request
	 * @throws Exception 
	 */
	ExchangeBlock exesinit() throws Exception {
		return xp.trb.initExchange(xp);
	}

	SyncResp onsyninit(ExchangeBlock ini)
			throws Exception {
		DBSyntableBuilder b0 = new DBSyntableBuilder(domain, conn, mynid, mymode);

		ExessionPersist xp = new ExessionPersist(b0, peer, ini)
								.loadNyquvect(conn);

		ExchangeBlock b = b0.onInit(xp, ini);

		domanager.synssion(peer, this);
		return new SyncResp()
				.exblock(b);
	}

	ExchangeBlock syncdb(ExchangeBlock rep)
			throws SQLException, TransException {
		return xp.nextExchange(rep);
	}
	
	public ExchangeBlock onsyncdb(ExchangeBlock reqb)
			throws SQLException, TransException {
		ExchangeBlock repb = xp.nextExchange(reqb);
		return repb;
	}

	ExchangeBlock synclose(ExchangeBlock rep)
			throws TransException, SQLException {
		return xp.trb.closexchange(xp, rep);
	}

	SyncResp onsynclose(ExchangeBlock reqb)
			throws TransException, SQLException {
		ExchangeBlock b = xp.trb.onclosexchange(xp, reqb);
		return new SyncResp().exblock(b);
	}

//	/**
//	 * Initialize an exchange session.
//	 * @param ini
//	 * @return initializing request
//	 * @throws Exception 
//	 */
//	SyncResp exesOninit(SyncResp ini) throws Exception {
//		b0 = new DBSyntableBuilder(domain, conn, mynid, mymode);
//
//		xp = new ExessionPersist(b0, peer, ini.exblock)
//								.loadNyquvect(conn);
//
//		ExchangeBlock b = b0.onInit(xp, ini.exblock);
//
//		return new SyncResp().exblock(b);
//	}

	SyncResp exespush(String peer, ExchangeBlock reqb) {
		SyncReq req = new SyncReq(null, peer).exblock(reqb);
		return exespush(peer, req);
	}

	SyncResp exespush(String peer, SyncReq req) {
		String[] act = AnsonHeader.usrAct(getClass().getName(), "push", A.syncent, "by " + mynid);
		AnsonHeader header = client.header().act(act);

		req.a(A.syncent);
		// req.org = org;

		SyncResp resp = null;
		try {
			AnsonMsg<SyncReq> q = client.<SyncReq>userReq("/syn/" + domain, Port.docsync, req)
								.header(header);

			resp = client.commit(q, errHandler);
		} catch (AnsonException | SemanticException e) {
			errHandler.err(MsgCode.exSemantic,
					e.getMessage() + " " + (e.getCause() == null
					? "" : e.getCause().getMessage()));
		} catch (IOException e) {
			errHandler.err(MsgCode.exIo,
					e.getMessage() + " " + (e.getCause() == null
					? "" : e.getCause().getMessage()));
		}
		return resp;
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

	public void joindomain(String admid, String myuid, String mypswd) {
		new Thread(() -> { 
			try {
				lock.lock();
				SyncReq  req = signup(admid);
				SyncResp rep = exespush(admid, req);

				req = closejoin(admid, rep);
				rep = exespush(admid, req);

				// rep = x.onclosejoin(req);
			} catch (TransException | SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally { lock.unlock(); }
		}).start();
	}

	SyncReq signup(String admid) throws TransException, SQLException {
		ExchangeBlock xb  = xp.trb.domainSignup(xp, admid);
		return new SyncReq(null, admid).exblock(xb);
	}

	public SyncReq closejoin(String admin, SyncResp rep) throws TransException, SQLException {
		// try {
			// ExessionPersist xp = synssion(admin).xp;
			xp.trb.domainitMe(xp, admin, rep.exblock);

			ExchangeBlock req = xp.trb.domainCloseJoin(xp, rep.exblock);
			return new SyncReq(null, domain)
					.exblock(req);
		// } finally { expiredClientier = delession(admin); }
	}
}
