package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.semantic.syn.ExessionAct.close;
import static io.odysz.semantic.syn.ExessionAct.deny;
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
import io.oz.jserv.docs.syn.SyncReq.A;

/**
 * TODO rename to Synssion, for this is used in both sides?
 */
public class SynssionClientier {

	static String uri_syn = "/syn";

	/** {@link #uri_syn}/[peer] */
	final String clienturi;

	final String mynid;
	final String conn;
	final String peer;
	public final String peerjserv;

	String domain() {
		// return xp != null && xp.trb != null ? xp.trb.domain() : null;
		return domanager.domain;
	}

	SynodeMode mymode;

	SynDomanager domanager;
	DBSyntableBuilder b0; 

	/** Exclusive lock for avoiding Synssions initiated from both side */
	final Object peerlock;

	/**
	 * Exclusive lock for starting threads of differnt tasks for both
	 * joining and Synssions type.
	 */
	final ReentrantLock tasklock;

	OnError errHandler;
	public SynssionClientier onErr(OnError err) {
		errHandler = err;
		return this;
	}

	protected SessionClient client;

	public SynssionClientier(SynDomanager domanager, String peer, String jserv) {
		this.conn      = domanager.myconn;
		this.mynid     = domanager.synode;
		this.domanager = domanager;
		this.peer      = peer;
		this.mymode    = domanager.synmod;
		this.peerjserv = jserv;
		
		this.tasklock  = new ReentrantLock();
		this.peerlock  = new Object();
		
		this.clienturi = uri_syn + "/" + peer;
	}

	/**
	 * [Synchronous]<br>
	 * Start a domain updating process (handshaking) with this.peer, in this.domain.
	 * @return this
	 * @throws ExchangeException not ready yet.
	 * @since 0.2.0
	 */
	public SynssionClientier update2peer() throws ExchangeException {
		if (client == null || isblank(peer) || isblank(domain()))
			throw new ExchangeException(ready, null,
					"Synchronizing information is not ready, or not logged in. peer %s, domain %s%s.",
					peer, domain(), client == null ? ", client is null" : "");

//		new Thread(() -> { 
			SyncResp rep = null;
			try {
				// start session
				tasklock.lock();
				ExchangeBlock reqb = exesinit();
				rep = exespush(peer, A.exinit, reqb);

				if (rep != null) {
					if (rep.exblock != null && rep.exblock.synact() != deny) {
						// on start reply
						onsyninit(rep.exblock, rep.domain);
						while (rep.synact() != close) {
							ExchangeBlock exb = syncdb(rep.exblock);
							rep = exespush(peer, A.exchange, exb);
							if (rep == null)
								throw new ExchangeException(exb.synact(), xp,
										"Got null reply for exchange session. %s : %s -> %s",
										domain(), domanager.synode, peer);
						}
						
						// close
						reqb = synclose(rep.exblock);
						// rep  = exesclose(peer, reqb);
						rep = exespush(peer, A.exclose, reqb);
					}
					
//					if (onup != null)
//						onup.ok(domain(), mynid, peer, rep == null ? null : rep.exblock, xp);
				}
			} catch (IOException e) {
				Utils.warn(e.getMessage());
			} catch (ExchangeException e) {
				e.printStackTrace();
				// exesclose(peer, rep == null ? null : rep.exblock);
				try {
					ExchangeBlock reqb = synclose(rep.exblock);
					rep = exespush(peer, A.exclose, reqb);
				} catch (TransException | SQLException e1) {
					e1.printStackTrace();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			finally {
				tasklock.unlock();
			}
//		}, f("%1$s [%2$s <- %1$s]", domanager.synode, peer)) .start();
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

	/**
	 * Handle syn-init request.
	 * 
	 * @param ini request's exchange block
	 * @param domain
	 * @return respond
	 * @throws ExchangeException peer id from {@code ini} doesn't match with mine.
	 * @throws Exception
	 */
	SyncResp onsyninit(ExchangeBlock ini, String domain) throws ExchangeException, Exception {
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

		try {
			AnsonMsg<SyncReq> q = client.<SyncReq>userReq(clienturi, Port.syntier, req)
								.header(header);

			return client.commit(q, errHandler);
		} catch (AnsonException | SemanticException e) {
			errHandler.err(MsgCode.exSemantic,
					e.getMessage() + " " + (e.getCause() == null
					? "" : e.getCause().getMessage()));
		} catch (IOException e) {
			errHandler.err(MsgCode.exIo,
					e.getMessage() + " " + (e.getCause() == null
					? "" : e.getCause().getMessage()));
		}
		return null;
	}

	/**
	 * Close the session, in either failed or succeed.
	 * @param peer
	 * @param req can be null
	 * @return
	SyncResp exesclose(String peer, ExchangeBlock req) {
		return null;
	}
	 */

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
	public void joindomain(String admid, String myuid, String mypswd, OnOk ok) {
//		new Thread(() -> { 
			try {
				tasklock.lock();
				SyncReq  req = signup(admid);
				SyncResp rep = exespush(admid, (SyncReq)req.a(A.initjoin));

				req = closejoin(admid, rep);
				rep = exespush(admid, (SyncReq)req.a(A.closejoin));

				if (!isNull(ok))
					ok.ok(rep);
			} catch (TransException | SQLException | AnsonException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			} finally { tasklock.unlock(); }
//		}, f("Join domain %s <- %s", admid, myuid)).start();
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
		if (!eq(rep.domain, domanager.domain))
			throw new ExchangeException(close, xp,
				"Close joining session for different ids? Rep.domain: %s, Domanager.domain: %s",
				rep.domain, domanager.domain);

		xp.trb.domainitMe(xp, admin, peerjserv, domanager.domain, rep.exblock);

		ExchangeBlock req = xp.trb.domainCloseJoin(xp, rep.exblock);
		return new SyncReq(null, xp.trb.domain())
				.exblock(req);
	}
}
