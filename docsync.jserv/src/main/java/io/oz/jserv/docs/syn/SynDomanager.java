package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt._0;
import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.musteqs;
import static io.odysz.common.LangExt.notNull;
import static io.odysz.common.Utils.warnT;
import static io.oz.syn.ExessionAct.close;
import static io.oz.syn.ExessionAct.ready;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;

import io.odysz.anson.AnsonException;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.JServUrl;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.JProtocol.OnError;
import io.odysz.semantic.jprotocol.JProtocol.OnOk;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.ExchangeException;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.parts.Logic.op;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.singleton.AppSettings;
import io.oz.jserv.docs.syn.singleton.Syngleton.OnNetworkChange;
import io.oz.syn.DBSyntableBuilder;
import io.oz.syn.ExessionPersist;
import io.oz.syn.Nyquence;
import io.oz.syn.SyncUser;
import io.oz.syn.SyndomContext;
import io.oz.syn.registry.SynodeConfig;

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

	public SynDomanager(SynodeConfig c, AppSettings s) throws Exception {
		super(c.mode, c.chsize, c.domain, c.synode(), c.synconn, c.debug);

		this.org = c.org.orgId;
		
		errHandler = (e, r, a) -> {
			Utils.warn("Error code: %s,\n%s", e.name(), String.format(r, (Object[])a));
		};
		
		admin = new SyncUser();
		
		this.jservComposer = s.getJservUrl(c.https);
	}
	
	/**
	 * Sing up, then start a synssion to {@code peeradmin}, the admin peer.
	 * TODO rename as sing2peer
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

	/**
	 * Update (synchronize) this domain, peer by peer.
	 * Can be called by request handler and timer.
	 * 
	 * <p>Updating event is ignored if the clientier is running.</p>
	 * @param onUpdate callback 
	 * 
	 * @return this
	 * @throws ExchangeException 
	 * @throws IOException 
	 * @throws SsException 
	 * @throws AnsonException 
	 * @throws SemanticException 
	 * @throws InterruptedException 
	 * @since 0.2.0
	 */
	public SynDomanager updomains(OnDomainUpdate onUpdate) throws ExchangeException {
		if (sessions == null || sessions.size() == 0)
			throw new ExchangeException(ready, null,
						"Session pool is null at %s", synode);
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
	 * Download domain jservs.
	 * Work without concurrency lock: load jservs from hub.
	 * <p>NOTE 2025-08-12
	 * This method is supposed to be called by sync-worker, and won't check the synode modes.</p>
	 * <p>ISSUE for the future
	 * It's supposed that some synodes will never have a chance to visit the hub node,
	 * then an asynchronous try and delay is expected.</p>
	 * </p>
	 * @see #submitJservsPersist(String, String...)
	 * @param syntb
	 * @return this
	 * @since 0.7.6
	 */
	public SynDomanager updateJservs(DATranscxt syntb) {
		if (sessions != null)
		for (SynssionPeer peer : sessions.values()) {
			if (eq(peer.peer, synode))
					continue;
			
			if (isblank(peer.peerjserv)) {
				warnT(new Object() {}, "Cannot log into %s as no jservs available.", peer.peer);
				continue;
			}

			try {
				peer.checkLogin("Updating jservs", admin);
					
				HashMap<String, Object> jservs = peer.queryJservs();
				for (String n : jservs.keySet()) {
					if (!eq(synode, n)) {
						AppSettings.updatePeerJservs(synconn, domain, synm, n, (String) jservs.get(n));
						peer.peerjserv = (String) jservs.get(n);
					}
				}
				break;
			} catch (IOException e) {
				Utils.logT("[%s] Updating jservs from %s failed. Details:\n%s",
						domain, peer, e.getMessage());
			} catch (TransException | AnsonException | SsException | SQLException e) {
				e.printStackTrace();
			}
		}
	
		return this;
	}

	/**
	 * @since 0.2.6
	 * @param syntb
	 * @param peer
	 * @param jserv
	 * @return
	 * @throws TransException
	 * @throws SQLException
	 */
	public SynDomanager updateJserv(DATranscxt syntb, String peer, String jserv) throws TransException, SQLException {
		AppSettings.updatePeerJservs(synconn, domain, synm, peer, jserv);
		if (synssion(peer) != null) // in case of on a passive server
			synssion(peer).peerjserv = jserv;
		return this;
	}

	/**
	 * <p>Submit then persist with reply, if the peer is a hub (0.2.6).</p> 
	 * Work without concurrency lock: load jservs from hub.
	 * <p>NOTE 2025-08-12
	 * This method is supposed to be called by sync-worker, and won't check the synode modes.</p>
	 * <p>ISSUE for the future
	 * It's supposed that some synodes will never have a chance to visit the hub node,
	 * then an asynchronous try and delay is expected.</p>
	 * @see #updateJserv(DATranscxt, String, String)
	 * @see SynssionPeer#submitJserv(String)
	 * @param nextip 
	 * @param cfg 
	 * @return this
	 * @since 0.7.6
	 */
	public String submitJservsPersist(String currentIp, String... nextip) {
		String ip = isNull(nextip) ? AppSettings.getLocalIp(2) : _0(nextip);
		if (!eq(currentIp, ip)) {
			try {
				if (sessions == null)
					loadSynclients(new DATranscxt(synconn));
				for (SynssionPeer peer : sessions.values()) {
					if (eq(peer.peer, synode))
							continue;

					if (isblank(peer.peerjserv)) {
						warnT(new Object() {},
							"Cannot log into %s <- %s, for submitting my jserv, as no jservs available.",
							peer.peer, synode);
						continue;
					}

					try {
						peer.checkLogin("Submitting jservs", admin);
							
						String myjserv = this.jservComposer.ip(ip).jserv();
						AppSettings.updatePeerJservs(synconn, domain, synm, synode, myjserv);
						
						HashMap<String, String> jservs = peer.submitJserv(myjserv);
						if (jservs != null) {
							for (String synid : jservs.keySet())
								if (!eq(synid, synode) && !eq(synid, peer.peer))
									AppSettings.updatePeerJservs(synconn, domain, synm, synid, jservs.get(synid));
						}
						
						if (this.ipChangeHandler != null)
							ipChangeHandler.on(this.jservComposer);
						break;
					} catch (IOException e) {
						Utils.logT(new Object() {}, "[%s:%s] Submitting jservs to %s failed. Error: %s, Details:\n%s",
								domain, synode, peer.peer, e.getClass().getName(), e.getMessage());
					} catch (TransException | AnsonException | SsException | SQLException e) {
						e.printStackTrace();
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return ip;
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
			
			if (!JServUrl.valid(rs.getString(synm.jserv)))
				continue;

			SynssionPeer c = new SynssionPeer(this, peer, rs.getString(synm.jserv), dbg)
								.onErr(errHandler);

			if (dbg && sessions.containsKey(peer)) {
				SynssionPeer target = sessions.get(peer);
				if ( !eq(c.domain(), target.domain())
				  || !eq(c.conn, target.conn)
				  || c.mymode != target.mymode
				  || !eq(c.peer, target.peer)
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
	 * 
	 * @param docuser
	 * @param onok
	 * @return this
	 * @throws AnsonException
	 * @throws SsException
	 * @throws IOException
	 * @throws TransException 
	 */
	public SynDomanager openSynssions(OnDomainUpdate... onok)
			throws AnsonException, SsException, IOException, TransException {
		if (sessions != null)
		for (SynssionPeer peer : sessions.values()) {
			if (eq(peer.peer, synode))
					continue;

			peer.checkLogin("Opening domain", admin);
		}

		if (!isNull(onok))
			onok[0].ok(domain(), synode, null);

		return this;
	}

	/**
	 * @deprecated only for test
	 * Start an exchange / synchronize session.
	 * @param docuser
	 * @param onok
	 * @return this
	 * @throws ExchangeException
	 */
	public SynDomanager updateSynssions(OnDomainUpdate... onok)
			throws ExchangeException {

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

	public void closession() {
		if (sessions != null)
		for (SynssionPeer peer : sessions.values()) {
			if (peer.client == null || !peer.client.isSessionValid())
				try { peer.client.logout(); } catch (Throwable e) {}
			peer.client = null;
		}
	}

	/** @since 0.7.6 */
	public final JServUrl jservComposer;

	/** @since 0.7.6 */
	OnNetworkChange ipChangeHandler;
	/** @since 0.7.6 */
	public SynDomanager ipChangeHandler(OnNetworkChange handler) {
		this.ipChangeHandler = handler;
		return this;
	}

	public HashMap<String, String> loadJservs(DATranscxt tb) throws SQLException, TransException {
		IUser robot = DATranscxt.dummyUser();

		return ((AnResultset) tb.select(synm.tbl)
		  .cols(synm.jserv, synm.synoder)
		  .whereEq(synm.domain, domain())
		  .rs(tb.instancontxt(synconn, robot))
		  .rs(0))
		  .map(synm.synoder,
			  (rs) -> rs.getString(synm.jserv),
			  (rs) -> rs.getString(synm.jserv) != null);
	}
}
