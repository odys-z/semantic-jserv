package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.semantic.syn.ExessionAct.close;
import static io.odysz.semantic.syn.ExessionAct.init;
import static io.odysz.semantic.syn.ExessionAct.ready;

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
import io.odysz.semantic.jprotocol.JProtocol.OnOk;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.syn.DBSyntableBuilder;
import io.odysz.semantic.syn.ExchangeBlock;
import io.odysz.semantic.syn.ExessionPersist;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.x.ExchangeException;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.SynDomanager.OnDomainUpdate;
import io.oz.jserv.docs.syn.SyncReq.A;

public class SynssionClientier {

	static String uri_syn = "/syn";

	/** {@link #uri_syn}/[peer] */
	final String clienturi;

	String mynid;
	String conn;
	String peer;

	String domain() {
		return xp != null && xp.trb != null ? xp.trb.domain() : null;
	}

	SynssionClientier domain(String domain) {
		this.xp.trb.domain(domain);
		return this;
	}

	SynodeMode mymode;
	SynDomanager domanager;
	DBSyntableBuilder b0; 

	OnError errHandler;
	public SynssionClientier onErr(OnError err) {
		errHandler = err;
		return this;
	}

	protected SessionClient client;

	final ReentrantLock lock;

	public SynssionClientier(SynDomanager domanager, String peer) {
		this.conn      = domanager.myconn;
		this.mynid     = domanager.synode;
		this.domanager = domanager;
		this.peer      = peer;
		this.mymode    = domanager.synmod;
		
		lock = new ReentrantLock();
		
		clienturi = uri_syn + "/" + peer;
	}

	/**
	 * Start a domain updating process (handshaking) with this.peer, in this.domain.
	 * @param object 
	 * @return this
	 * @throws ExchangeException not ready yet.
	 */
	public SynssionClientier asynUpdate2peer(OnDomainUpdate onup) throws ExchangeException {
		if (client == null || isblank(peer) || isblank(domain()))
			throw new ExchangeException(ready, null, "Synchronizing information is not ready, or not logged in. peer %s, domain %s%s.",
					peer, domain(), client == null ? ", client is null" : "");

		new Thread(() -> { 
			try {
				// start session
				lock.lock();
				ExchangeBlock reqb = exesinit();
				SyncResp rep = exespush(peer, A.exinit, reqb);

				if (rep != null) {
					// on start reply
					onsyninit(rep.exblock, rep.domain);
					while (rep.synact() != close) {
						// See SynoderTest
						// req = syncdb(peer, rep);
						// rep = srv.onsyncdb(clt.synode, req);
						ExchangeBlock exb = syncdb(rep.exblock);
						rep = exespush(peer, A.exchange, exb);
					}
					
					// close
					reqb = synclose(rep.exblock);
					rep = exesclose(peer, reqb);
					
					if (onup != null)
						onup.ok(domain(), mynid, peer, xp);
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
		DBSyntableBuilder b0 = new DBSyntableBuilder(domain(), conn, mynid, mymode);
		xp = new ExessionPersist(b0, peer, null)
						.loadNyquvect(conn);

		return b0.initExchange(xp);
	}

	SyncResp onsyninit(ExchangeBlock ini, String domain) throws Exception {
		if (!eq(ini.srcnode, peer))
			throw new ExchangeException(init, null, "Request.srcnode(%s) != peer (%s)", ini.srcnode, peer);

		DBSyntableBuilder b0 = new DBSyntableBuilder(domain, conn, mynid, mymode);

		xp = new ExessionPersist(b0, peer, ini)
						.loadNyquvect(conn);

		ExchangeBlock b = b0.onInit(xp, ini);

		return new SyncResp(domain()).exblock(b);
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
		return new SyncResp(domain()).exblock(b);
	}

	SyncResp exespush(String peer, String a, ExchangeBlock reqb) {
		SyncReq req = (SyncReq) new SyncReq(null, peer)
					.exblock(reqb)
					.a(a);

		return exespush(peer, req);
	}

	SyncResp exespush(String peer, SyncReq req) {
		String[] act = AnsonHeader.usrAct(getClass().getName(), "push", A.exchange, "by " + mynid);
		AnsonHeader header = client.header().act(act);

		// req.a(A.exchange);
		// req.org = org;

		SyncResp resp = null;
		try {
			AnsonMsg<SyncReq> q = client.<SyncReq>userReq(clienturi, Port.syntier, req)
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

	/**
	 * Go through the handshaking process of sing up to a domain. 
	 * 
	 * @param admid
	 * @param myuid
	 * @param mypswd
	 * @param ok
	 * @since 0.2.0
	 */
	public void asynJoindomain(String admid, String myuid, String mypswd, OnOk ok) {
		new Thread(() -> { 
			try {
				lock.lock();
				SyncReq  req = signup(admid);
				SyncResp rep = exespush(admid, (SyncReq)req.a(A.initjoin));

				if (rep != null)
					xp.trb.domain(rep.domain);

				req = closejoin(admid, rep);
				rep = exespush(admid, (SyncReq)req.a(A.closejoin));

				if (!isNull(ok))
					ok.ok(rep);
			} catch (TransException | SQLException | AnsonException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			} finally { lock.unlock(); }
		}).start();
	}

	SessionClient loginWithUri(String jservroot, String myuid, String pswd, String device)
			throws SemanticException, AnsonException, SsException, IOException {
		client = new SessionClient(jservroot, null)
				.loginWithUri(clienturi, myuid, pswd, device);
		return client;
	}

	/**
	 * Step n-stamp, create a request package.
	 * @param admid
	 * @return the request
	 * @throws TransException
	 * @throws SQLException
	 */
	SyncReq signup(String admid) throws TransException, SQLException {
		ExchangeBlock xb  = xp.trb.domainSignup(xp, admid);
		return new SyncReq(null, admid).exblock(xb);
	}

	public SyncReq closejoin(String admin, SyncResp rep) throws TransException, SQLException {
		xp.trb.domainitMe(xp, admin, rep.exblock);

		ExchangeBlock req = xp.trb.domainCloseJoin(xp, rep.exblock);
		return new SyncReq(null, xp.trb.domain())
				.exblock(req);
	}
}
