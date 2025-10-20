package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.musteqs;
import static io.odysz.common.LangExt.mustnonull;
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
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.JProtocol.OnError;
import io.odysz.semantic.jprotocol.JProtocol.OnOk;
import io.odysz.semantic.jprotocol.JServUrl;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.x.ExchangeException;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.parts.Logic.op;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.singleton.AppSettings;
import io.oz.jserv.docs.syn.singleton.ISynodeLocalExposer;
import io.oz.jserv.docs.syn.singleton.Syngleton;
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

	public final String org;
	
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

	public final Syngleton syngleton;
	
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

	public SynDomanager(Syngleton syngleton, SynodeConfig c, AppSettings s) throws Exception {
		super(c.mode, c.chsize, c.org.orgId, c.domain, c.synode(), c.synconn, c.debug);

		this.syngleton = syngleton;
		this.org = c.org.orgId;
		
		errHandler = (e, r, a) -> {
			Utils.warn("Error code: %s,\n%s", e.name(), String.format(r, (Object[])a));
		};
		
		admin = new SyncUser();
	}

	/**
	 * Sign up, then start a synssion to {@code peeradmin}, the admin peer.
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

		SynssionPeer c = new SynssionPeer(this, peeradmin, dbg)
							.xp(cltp)
							.onErr(this);

		if (isblank(adminjserv))
			throw new AnsonException(0,
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
	 * @throws TransException 
	 * @throws IOException 
	 * @throws SsException 
	 * @throws AnsonException 
	 * @since 0.2.0
	 * @deprecated since 0.2.6, this loop is done in a worker's
	 * try-catch block, to avoid stop the worker.
	 */
	public SynDomanager updomain(OnDomainUpdate onUpdate)
			throws AnsonException, SsException, IOException, TransException {
		if (sessions == null || sessions.size() == 0)
			throw new ExchangeException(ready, null,
						"Session pool is null at %s", synode);
		for (String peer : sessions.keySet()) {
			ExessionPersist xp = sessions.get(peer).xp;
			if (xp == null || xp.exstate() == ready) {
				sessions.get(peer).checkLogin(admin);
				sessions.get(peer).update2peer((lockby) -> Math.random() + 0.1);
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
	 * Push all my jservs, accept reply's newer versions.
	 * @param s
	 * @return accepted or not
	 * @see AppSettings#merge_ip_json2db(SynodeConfig, io.odysz.semantic.meta.SynodeMeta, SyncUser, OnError)
	 */
	public boolean synodeNetworking(AppSettings s) {
		// TODO ignored the optional step, on IP changed.

		boolean dirty = false;
		try {
			if (sessions == null)
				loadSynclients(synb);
			for (SynssionPeer peer : sessions.values()) {
				if (eq(peer.peer, synode))
					continue;

				if (isblank(peer.peerjserv())) {
					warnT(new Object() {},
						"Cannot log into %s <- %s, for submitting my jserv, as no jservs available.",
						peer.peer, synode);
					continue;
				}

				try {
					Utils.logT(new Object(){},
							"Submitting jservs in %s, check login state to: %s -> %s, jserv: %s",
							domain(), peer.mynid, peer.peer, peer.peerjserv());
					peer.checkLogin(admin);
						
					HashMap<String, String[]> jservs = peer.exchangeDBservs(loadDBservss());

					if (jservs != null) {
						dirty = this.onexchangeDBservs(jservs);
						// ISSUE should update settings.json?
						// s.loadDBservs(c, synm, jour0);
						// s.save();
					}

				} catch (IOException e) {
					Utils.logT(new Object() {},
							"[%s:%s] Submitting jservs to %s failed. Error: %s, Details:\n%s",
							domain, synode, peer.peer, e.getClass().getName(), e.getMessage());
				} catch (TransException | AnsonException | SsException | SQLException e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return dirty;
	}
			
	public HashMap<String, String[]> loadDBservss()
			throws SQLException, TransException {
		return synm.loadJservs(synb, org, domain, rs -> JServUrl.valid(rs.getString(synm.jserv)));
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
				.whereEq(synm.org, org)
				.whereEq(synm.domain, domain())
				.rs(t0.instancontxt(synconn, admin))
				.rs(0);
		
		if (sessions == null)
			sessions = new HashMap<String, SynssionPeer>();
		
		while (rs.next()) {
			String peer = rs.getString("peer");
			
			if (!JServUrl.valid(rs.getString(synm.jserv))) {
				String err = f("WARN [%s : %s] -> %s: Invalid jserv: %s",
						domain, synode, rs.getString("peer"), rs.getString(synm.jserv));
				warnT(new Object() {}, err);
				
				continue;
			}

			SynssionPeer c = new SynssionPeer(this, peer, dbg)
								.onErr(errHandler);

			if (dbg && sessions.containsKey(peer)) {
				SynssionPeer target = sessions.get(peer);
				if ( !eq(c.domain(), target.domain())
				  || !eq(c.conn, target.conn)
				  || c.mymode != target.mymode
				  || !eq(c.peer, target.peer)
				  || target.xp != null)
					throw new ExchangeException(ready, target.xp,
							"Replacing existing syssion peer is not allowed. [%1$s->%2$s] := [%1$s->%3$s]",
							synode, target.peer, c.peer);
			}

			sessions.put(peer, c);
			
			Utils.logi("[ ♻.✩ %s ] Synssion Opened (clientier created): {conn: %s, mode: %s, peer: %s, peer-jserv: %s}",
					synode, c.conn, c.mymode.name(), c.peer, c.peerjserv());
		}

		return this;
	}

	/**
	 * @deprecated For tests only. 
	 * Start an exchange / synchronize session.
	 * @param docuser
	 * @param onok
	 * @return this
	 * @throws TransException 
	 * @throws IOException 
	 * @throws SsException 
	 * @throws AnsonException 
	 * @throws SemanticException 
	 */
	public SynDomanager updateSynssions(OnDomainUpdate... onok)
			throws SemanticException, AnsonException, SsException, IOException, TransException {

		for (SynssionPeer peer : sessions.values()) {
			if (eq(peer.peer, synode))
					continue;
			peer.checkLogin(admin);
			peer.update2peer((lockby) -> Math.random());
		}

		if (!isNull(onok))
			onok[0].ok(domain(), synode, null);

		return this;
	}
	
	public SynDomanager synUpdateDomain(SynssionPeer peer, OnDomainUpdate... onok)
			throws AnsonException, SsException, IOException, TransException  {

		if (!eq(peer.peer, synode)) {
			peer.checkLogin(admin);
			peer.update2peer((lockby) -> Math.random());

			if (!isNull(onok))
				onok[0].ok(domain(), synode, peer.peer);

		}
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
		if (sessions != null) {
		for (SynssionPeer peer : sessions.values()) {
			if (peer.client == null || !peer.client.isSessionValid())
				try { peer.client.logout(); } catch (Throwable e) {}
			peer.client = null;
		}
		sessions.clear();
		}
	}

	/** @since 0.7.6 */
	ISynodeLocalExposer ipChangeHandler;

	/** @since 0.7.6 */
	public SynDomanager ipChangeHandler(ISynodeLocalExposer handler) {
		this.ipChangeHandler = handler;
		return this;
	}

	boolean onexchangeDBservs(HashMap<String, String[]> jservs)
			throws TransException, SQLException {
		mustnonull(jservs);
		boolean merged = false;
		for (String sid : jservs.keySet()) {
			if (eq(sid, this.synode) || !JServUrl.valid(jservs.get(sid)[0]))
				continue;
			
			String[] jserv_utc_oper = jservs.get(sid);
			merged |= AppSettings.inst_updateLaterDBserv(
					synconn, org, domain, synm,
					sid, jserv_utc_oper[0], jserv_utc_oper[1], jserv_utc_oper[2]);
		}
		return merged;
	}
}
