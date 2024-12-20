package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.notNull;
import static io.odysz.semantic.syn.ExessionAct.*;

import java.io.IOException;
import java.sql.SQLException;

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
import io.odysz.semantic.syn.SyndomContext.OnMutexLock;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.x.ExchangeException;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.SyncReq.A;

/**
 */
public class SynssionPeer {

	static String uri_syn = "/syn";
	static String uri_sys = "/sys";

	/** {@link #uri_syn}/[peer] */
	final String clienturi;

	final String mynid;
	final String conn;
	final String peer;
	public final String peerjserv;

	String domain() {
		return domanager.domain();
	}

	SynodeMode mymode;

	SynDomanager domanager;
	DBSyntableBuilder b0; 

	ExessionPersist xp;

	OnError errHandler;
	public SynssionPeer onErr(OnError err) {
		errHandler = err;
		return this;
	}

	protected SessionClient client;
	private boolean debug;

	public SynssionPeer(SynDomanager domanager, String peer, String peerjserv, boolean debug) {
		this.conn      = domanager.synconn;
		this.mynid     = domanager.synode;
		this.domanager = domanager;
		this.peer      = peer;
		this.mymode    = domanager.mode;
		this.peerjserv = peerjserv;
		this.clienturi = uri_sys;
		this.debug     = debug;
	}

	/**
	 * [Synchronous]<br>
	 * Start a domain updating process (handshaking) with this.peer, in this.domain.
	 * @param onMutext 
	 * @return this
	 * @throws ExchangeException not ready yet.
	 * @since 0.2.0
	 */
	public SynssionPeer update2peer(OnMutexLock onMutext) throws ExchangeException {
		if (client == null || isblank(peer) || isblank(domain()))
			throw new ExchangeException(ready, null,
					"Synchronizing information is not ready, or not logged in. peer %s, domain %s%s.",
					peer, domain(), client == null ? ", client is null" : "");

		SyncResp rep = null;
		try {
			// start session

			if (debug)
				Utils.logi("Locking and starting thread on domain updating: %s : %s -> %s"
						+ "\n=============================================================\n",
						domain(), mynid, peer);

			/// lock and wait local syndomx
			domanager.lockme(onMutext);

			ExchangeBlock reqb = exesinit();
			rep = exespush(peer, A.exinit, reqb);

			if (rep != null) {
				// lock remote
				while (rep.synact() == trylater) {
					if (debug)
						Utils.logT(new Object() {},
								"%s: %s is locked, waiting...",
								mynid, peer);
						
					domanager.unlockme();

					double sleep = rep.exblock.sleeps;
					Thread.sleep((long) (sleep * 1000)); // wait for next try
					domanager.lockme(onMutext);

					rep = exespush(peer, A.exinit, reqb);
				}

				if (rep.exblock != null && rep.exblock.synact() != deny) {
					// on start reply
					onsyninitRep(rep.exblock, rep.domain);
						
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
					rep = exespush(peer, A.exclose, reqb);
				}
			}
		} catch (IOException e) {
			Utils.warn(e.getMessage());
		} catch (ExchangeException e) {
			e.printStackTrace();
			try {
				ExchangeBlock reqb = synclose(rep.exblock);
				rep = exespush(peer, A.exclose, reqb);
			} catch (TransException | SQLException e1) {
				e1.printStackTrace();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally { domanager.unlockme(); }
		return this;
	}

	/**
	 * Initiate a synchronization exchange session using ExessionPersist provided by manager.
	 * @param peer
	 * @return initiate request
	 * @throws Exception 
	 */
	ExchangeBlock exesinit() throws Exception {
		DBSyntableBuilder b0 = new DBSyntableBuilder(domanager);
		xp = new ExessionPersist(b0, peer, null);
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
	SyncResp onsyninitRep(ExchangeBlock ini, String domain) throws ExchangeException, Exception {
		if (!eq(ini.srcnode, peer))
			throw new ExchangeException(init, null, "Request.srcnode(%s) != peer (%s)", ini.srcnode, peer);

		DBSyntableBuilder b0 = new DBSyntableBuilder(domanager);
		xp = new ExessionPersist(b0, peer, ini);
		ExchangeBlock b = b0.onInit(xp, ini);

		return new SyncResp(domain()).exblock(b);
	}

	ExchangeBlock syncdb(ExchangeBlock rep)
			throws SQLException, TransException {
		return xp.nextExchange(rep);
	}

	ExchangeBlock synclose(ExchangeBlock rep)
			throws TransException, SQLException {
		return xp.trb.closexchange(xp, rep);
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
			AnsonMsg<SyncReq> q = client.<SyncReq>userReq(uri_syn, Port.syntier, req)
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

	public SynssionPeer xp(ExessionPersist xp) {
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
	 * @throws TransException 
	 * @throws IOException 
	 * @throws AnsonException 
	 * @throws SQLException 
	 * @since 0.2.0
	 */
	public void joindomain(String admid, String myuid, String mypswd, OnOk ok) throws AnsonException, IOException, TransException, SQLException {
//		try {
			SyncReq  req = signup(admid);
			SyncResp rep = exespush(admid, (SyncReq)req.a(A.initjoin));

			req = closejoin(admid, rep);
			rep = exespush(admid, (SyncReq)req.a(A.closejoin));

			if (!isNull(ok))
				ok.ok(rep);
//		} catch (TransException | SQLException | AnsonException | IOException e) {
//			e.printStackTrace();
//		}
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
		return new SyncReq(null, admid)
				.exblock(xb);
	}

	public SyncReq closejoin(String admin, SyncResp rep) throws TransException, SQLException {
		if (!eq(notNull(rep.domain), domanager.domain()))
			throw new ExchangeException(close, xp,
				"Close joining session for different ids? Rep.domain: %s, Domanager.domain: %s",
				rep.domain, domanager.domain());

		xp.trb.domainitMe(xp, admin, peerjserv, rep.domain, rep.exblock);

		ExchangeBlock req = xp.trb.domainCloseJoin(xp, rep.exblock);
		return new SyncReq(null, domanager.domain())
				.exblock(req);
	}
}
