package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.musteqs;
import static io.odysz.common.LangExt.notNull;
import static io.odysz.semantic.syn.ExessionAct.close;
import static io.odysz.semantic.syn.ExessionAct.ready;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import io.odysz.anson.AnsonException;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.JProtocol.OnError;
import io.odysz.semantic.jprotocol.JProtocol.OnOk;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.syn.DBSyntableBuilder;
import io.odysz.semantic.syn.ExessionPersist;
import io.odysz.semantic.syn.Nyquence;
import io.odysz.semantic.syn.SyncUser;
import io.odysz.semantic.syn.SyndomContext;
import io.odysz.semantics.x.ExchangeException;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.parts.Logic.op;
import io.odysz.transact.x.TransException;
import io.oz.syn.SynodeConfig;

/**
 * Syn-domain's sessions manager.
 * 
 * <pre>Avoid dead lock ring
 *        A
 *       / \
 *      B - C
 * 
 * A is waiting B, B is waiting C, C is waiting A</pre>
 * 
 * To avoide this, a client will quite once the request is denied.
 * 
 * @see #sessions
 * @since 0.2.0
 */
public class SynDomanager extends SyndomContext implements OnError {
	/**
	 * @since 0.2.0
	 */
	@FunctionalInterface
	public interface OnDomainUpdate {
		/**
		 * On domain update event, for each peer. Additional calling for all peers cleared (peer == null).
		 * @param domain
		 * @param mynid
		 * @param peer if null, all peers in the domain has finished updating
		 * @param xp
		 */
		public void ok(String domain, String mynid, String peer, ExessionPersist... xp);
	}

	static final String dom_unknown = null;

	final String org;
	
	/**
	 * {peer: session-persist}
	 * @since 0.2.0
	 */
	HashMap<String, SynssionPeer> sessions;
	
	/**
	 * Expired synssion, only for tests.
	 * @since 0.2.0
	 */
	public SynssionPeer expiredClientier;
	public SynssionServ closedServ;

	OnError errHandler;
	
	final DATranscxt tb0;

	public Nyquence lastn0(String peer) {
		return expiredClientier == null || expiredClientier.xp == null ?
				null : expiredClientier.xp.n0();
	}

	public SynssionPeer synssion(String peer) {
		return sessions != null
				? sessions.get(peer)
				: null;
	}

	SynDomanager synssion(String peer, SynssionPeer client) throws ExchangeException {
		if (sessions == null)
			sessions = new HashMap<String, SynssionPeer>();

		if (synssion(peer) != null && synssion(peer).xp.exstate() != ready)
			throw new ExchangeException(ready, synssion(peer).xp,
				"Session for synching to %s already exists at %s",
				peer, synode);

		sessions.put(peer, client);
		return this;
	}

	public SynDomanager(SynodeConfig c) throws Exception {
		super(c.mode, c.chsize, c.domain, c.synode(), c.synconn, c.debug);

		this.org = c.org.orgId;
		
		errHandler = (e, r, a) -> {
			Utils.warn("Error code: %s,\n%s", e.name(), String.format(r, (Object[])a));
		};
		
		tb0 = new DATranscxt(c.synconn);

		admin = new SyncUser();
	}
	
	/**
	 * Sing up, then start a synssion to the peer, {@code peeradmin}.
	 * 
	 * @param adminjserv jserv root path, must be null for testing
	 * locally without login to the service
	 * @param peeradmin
	 * @param passwd
	 * @return SynssionClient (alneed to be put into synssions),
	 * with xp for persisting and for req construction.
	 * @throws Exception
	 * @since 0.2.0
	 */
	public SynssionPeer join2peer(String adminjserv, String peeradmin, String userId, String passwd) throws Exception {

		DBSyntableBuilder cltb = new DBSyntableBuilder(this);

		// sign up as a new domain
		ExessionPersist cltp = new ExessionPersist(cltb, peeradmin);

		SynssionPeer c = new SynssionPeer(this, peeradmin, adminjserv, dbg)
							.xp(cltp)
							.onErr(this);

		if (isblank(adminjserv))
			Utils.warnT(new Object() {},
				"The root jserv path is empty. This should only happens when testing.\npeer: %s, user id: %s",
				peeradmin, userId);
		else {
			c.loginWithUri(adminjserv, userId, passwd, synode);
			Utils.logi("%s logged into %s", synode, peeradmin);
		}

		synssion(peeradmin, c);
		return c;
	}

	/**
	 * Get n0 of the session with the {@link peer} synode.
	 * 
	 * @param peer to which peer the session's n0 to be retrieved
	 * @return n0 N0 in all sessions should be the same.
	 * @since 0.2.0
	 */
	public Nyquence n0(String peer) {
		return synssion(peer).xp.n0();
	}

	public SynDomanager loadomainx() throws TransException, SQLException {
		Utils.logi("\n[ ♻.%s ] loading domain %s ...", synode, domain());
		
		SyncUser robot = new SyncUser(synode, "pswd: local null", synode)
				.deviceId(synode);

		loadNvstamp(tb0, robot);
		
		return this;
	}
	
	/**
	 * Update (synchronize) this domain, peer by peer.
	 * Can be called by request handler and timer.
	 * 
	 * <p>Updating event is ignored if the clientier is running.</p>
	 * @param onUpdate callback 
	 * 
	 * @return this
	 * @throws IOException 
	 * @throws SsException 
	 * @throws AnsonException 
	 * @throws SemanticException 
	 * @throws InterruptedException 
	 * @since 0.2.0
	 */
	public SynDomanager asyUpdomains(OnDomainUpdate onUpdate, OnMutexLock block)
			throws SemanticException, AnsonException, SsException, IOException {
		if (sessions == null || sessions.size() == 0)
			throw new ExchangeException(ready, null,
						"Session pool is null at %s", synode);
		new Thread(() -> { 
			// tasks looping until finished
			for (String peer : sessions.keySet()) {
				ExessionPersist xp = sessions.get(peer).xp;
				if (xp == null || xp.exstate() == ready)
					try {
						sessions.get(peer).update2peer((lockby) -> 0.31f);
					} catch (ExchangeException e) {
						e.printStackTrace();
					}
				else if (xp != null && xp.exstate() != ready)
					continue;
				else
					Utils.warnT(new Object() {}, "TODO updating %s <- %s",
							peer, synode);

				if (onUpdate != null)
					onUpdate.ok(domain(), synode, peer, xp);
			}

			if (onUpdate != null)
				onUpdate.ok(domain(), synode, null);
		}, f("%1$s update [%2$s]", synode, domain()))
		.start();

		return this;
	}

	/**
	 * Background sign up.
	 * @param dom
	 * @param admid
	 * @param admserv
	 * @param myuid
	 * @param mypswd
	 * @param ok 
	 * @throws Exception
	 * @since 0.2.0
	 */
	public void joinDomain(String org, String dom, String admid, String admserv,
			String myuid, String mypswd, OnOk ok) throws Exception {

		if (sessions != null && sessions.containsKey(admid))
			throw new ExchangeException(close, null,
				"SynssionClientier already exists. Duplicated singup?");
		
		SynssionPeer c = join2peer(admserv, admid, myuid, mypswd);

		c.joindomain(admid, myuid, mypswd, (resp) -> {
			sessions.put(admid, c);
			ok.ok(resp);
		});
	}

	@Override
	public void err(MsgCode code, String msg, String... args) {
		Utils.warn("Error Code: %s", code.name());
		Utils.warn(msg, (Object[])args);
	}

	/**
	 * Load {@link SyndomContext#synm}.tbl and build all SynssionPeers to every peers.
	 * 
	 * SynssionPeer.xp should be null.
	 * 
	 * @param t0
	 * @return this
	 * @throws TransException
	 * @throws SQLException
	 */
	public SynDomanager loadSynclients(DATranscxt t0)
			throws TransException, SQLException {
		
		AnResultset rs = (AnResultset) t0
				.select(synm.tbl)
				.col(synm.synoder, "peer").col(synm.domain).col(synm.jserv)
				.groupby(synm.synoder)
				.where_(op.ne, synm.synoder, synode)
				.whereEq(synm.domain, domain())
				.rs(t0.instancontxt(synconn, admin))
				.rs(0);
		
		if (sessions == null)
			sessions = new HashMap<String, SynssionPeer>();
		
		while (rs.next()) {
			String peer = rs.getString("peer");
			SynssionPeer c = new SynssionPeer(this, peer, rs.getString(synm.jserv), dbg)
								.onErr(errHandler);

			if (dbg && sessions.containsKey(peer)) {
				SynssionPeer target = sessions.get(peer);
				if ( !eq(c.domain(), target.domain())
				  || !eq(c.conn, target.conn)
				  || c.mymode != target.mymode
				  || c.peer != target.peer
				  || target.xp != null)
					throw new ExchangeException(ready, target.xp, "Forced verification failed.");
			}

			sessions.put(peer, c);
			
			Utils.logi("[ ♻.✩ %s ] Synssion Opened (clientier created): {conn: %s, mode: %s, peer: %s, peer-jserv: %s}",
					synode, c.conn, c.mymode.name(), c.peer, c.peerjserv);
		}

		return this;
	}

	/**
	 * Login to peers and synchronize.
	 * @param docuser
	 * @param onok 
	 * @return this
	 * @throws AnsonException
	 * @throws SsException
	 * @throws IOException
	 * @throws TransException
	 * @throws InterruptedException 
	public SynDomanager openUpdateSynssions(SyncUser docuser, OnDomainUpdate... onok) {

		for (SynssionPeer peer : sessions.values()) {
			try {
				if (eq(peer.peer, synode))
						continue;
				peer.loginWithUri(peer.peerjserv, docuser.uid(), docuser.pswd(), docuser.deviceId());
				peer.update2peer((lockby) -> Math.random());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		if (!isNull(onok))
			onok[0].ok(domain(), synode, null);

		return this;
	}
	 */

	/**
	 * 
	 * @param docuser
	 * @param onok
	 * @return this
	 * @throws SemanticException
	 * @throws AnsonException
	 * @throws SsException
	 * @throws IOException
	 */
	public SynDomanager openSynssions(SyncUser docuser, OnDomainUpdate... onok)
			throws SemanticException, AnsonException, SsException, IOException {
		if (sessions != null)
		for (SynssionPeer peer : sessions.values()) {
			if (eq(peer.peer, synode) || peer.client != null)
					continue;

			if (peer.client == null || !peer.client.isSessionValid()) {
				Utils.logT(new Object(){},
						"Opening domain %s, logging into: %s, jserv: %s",
						domain, peer.peer, peer.peerjserv);
				peer.loginWithUri(peer.peerjserv, docuser.uid(), docuser.pswd(), docuser.deviceId());
			}
		}

		if (!isNull(onok))
			onok[0].ok(domain(), synode, null);

		return this;
	}

	public SynDomanager updateSynssions(SyncUser docuser, OnDomainUpdate... onok) throws ExchangeException {

		for (SynssionPeer peer : sessions.values()) {
			if (eq(peer.peer, synode))
					continue;
			peer.update2peer((lockby) -> Math.random());
		}

		if (!isNull(onok))
			onok[0].ok(domain(), synode, null);

		return this;
	}


	public DBSyntableBuilder createSyntabuilder(SynodeConfig cfg) throws Exception {
		notNull(cfg);
		musteqs(domain(), cfg.domain);
		musteqs(synode, cfg.synode());
		musteqs(synconn, cfg.synconn);

		return new DBSyntableBuilder(this);
	}

	public String lockSession() {
		return synlocker == null ? null : synlocker.sessionId();
	}

}
