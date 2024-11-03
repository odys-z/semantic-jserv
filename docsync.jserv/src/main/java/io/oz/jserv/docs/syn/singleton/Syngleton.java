package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.Utils.loadTxt;
import static io.odysz.common.LangExt.musteqs;
import static io.odysz.common.LangExt.shouldeq;
import static io.odysz.semantic.meta.SemanticTableMeta.setupSqliTables;
import static io.odysz.semantic.meta.SemanticTableMeta.setupSqlitables;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import io.odysz.anson.x.AnsonException;
import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantic.meta.PeersMeta;
import io.odysz.semantic.meta.SynChangeMeta;
import io.odysz.semantic.meta.SynSessionMeta;
import io.odysz.semantic.meta.SynSubsMeta;
import io.odysz.semantic.meta.SynchangeBuffMeta;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.meta.SyntityMeta;
import io.odysz.semantic.syn.DBSynTransBuilder;
import io.odysz.semantic.syn.SyncUser;
import io.odysz.semantic.syn.Synode;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Delete;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.parts.Logic.op;
import io.odysz.transact.sql.parts.condition.ExprPart;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.ExpSynodetier;
import io.oz.jserv.docs.syn.SynDomanager;
import io.oz.jserv.docs.syn.SynDomanager.OnDomainUpdate;
import io.oz.syn.SynodeConfig;

/**
 * @since 0.2.0
 */
public class Syngleton extends JSingleton {
	static DBSynTransBuilder.SynmanticsMap synmap;

	/** */
	static DATranscxt tb0;
	
	SynodeConfig syncfg;

	String jserv;

//	/** @deprecated TODO delete */
//	String synconn;
	final String sysconn;
//	/** @deprecated TODO delete */
//	String synode;

	/**
	 * Last (bug?) url pattern (key in {@link #syndomanagers}) of {@link ExpSynodetier}.
	String syntier_url;
	 */

	/**
	 * { servlet-url-pattern: { domain: domanager } }, only instance of {@link ExpSynodetier},<br>
	 * e. g. { docs.sync: { zsu: { new SnyDomanger(x, y) } }
	 */
	public HashMap<String, SynDomanager> syndomanagers;

	public SynDomanager domanager(String domain) {
		return (syndomanagers != null && syndomanagers.containsKey(domain)) ?
				syndomanagers.get(domain) : null;

	}

	public Syngleton domanagers(HashMap<String, SynDomanager> domains) {
		this.syndomanagers = domains;
		return this;
	}

	SynodeMeta synm;

	public Syngleton(SynodeConfig cfg) {
		sysconn = cfg.sysconn;
		syncfg = cfg;
		syndomanagers = new HashMap<String, SynDomanager>();
	}

	public void updatePeerJservs(SynodeMeta synm, SynodeConfig cfg, String domain)
			throws TransException, SQLException {
		if (!eq(domain, cfg.domain))
			throw new SemanticException(
				"Updating domain %s, but got configuration of %s.",
				domain, cfg.domain);
		
		IUser robot = DATranscxt.dummyUser();

		for (Synode sn : cfg.peers())
			tb0.update(synm.tbl, robot)
				.nv(synm.jserv, sn.jserv)
				.whereEq(synm.pk, sn.synid)
				.whereEq(synm.domain, cfg.domain)
				.u(tb0.instancontxt(cfg.synconn, robot));
	}

	/**
	 * Load domains from syn_synode, create {@link SynDomanager} for each domain.
	 * 
	 * @param synmod synode mode, peer, hub, etc.
	 * @param cfg 
	 * @return singleton
	 * @throws Exception
	 * @since 0.2.0
	 */
	public HashMap<String,SynDomanager> loadDomains(SynodeConfig cfg) throws Exception {
		// notNull(syntier_url);
		shouldeq(new Object() {}, cfg.mode, SynodeMode.peer);
		
		if (syndomanagers == null)
			syndomanagers = new HashMap<String, SynDomanager>();

		synm = new SynodeMeta(cfg.synconn); 

		AnResultset rs = (AnResultset) defltScxt
				.select(synm.tbl)
				.groupby(synm.domain)
				.groupby(synm.synoder)
				.whereEq(synm.pk, cfg.synode())
				.rs(defltScxt.instancontxt(cfg.synconn, DATranscxt.dummyUser()))
				.rs(0);
		
		while (rs.next()) {
			String domain = rs.getString(synm.domain);
			SynDomanager domanger = new SynDomanager(cfg)
					.loadomainx();

			syndomanagers.put(domain, (SynDomanager) domanger
						.loadNvstamp(defltScxt, domanger.robot)
						.synrobot(domanger.robot));
		}

		return syndomanagers;
	}

	/**
	 * Try join (login) known domains
	 * 
	 * @return this
	 * @throws IOException 
	 * @throws SsException 
	 * @throws AnsonException 
	 * @throws SQLException 
	 * @throws TransException 
	 */
	public Syngleton openDomains(OnDomainUpdate ... onok)
			throws AnsonException, SsException, IOException, TransException, SQLException {
		if (syndomanagers != null) {
			new Thread(()->{
				for (SynDomanager dmgr : syndomanagers.values()) {
					try {
						musteqs(syncfg.domain, dmgr.domain());

						SyncUser usr = ((SyncUser)AnSession
							.loadUser(syncfg.admin, sysconn))
							.deviceId(dmgr.synode);

						dmgr.loadSynclients(tb0)
							.openUpdateSynssions(usr, onok);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				if (!isNull(onok))
					onok[0].ok(null, syncfg.synode(), null);
			}, f("[%s] Open Domain", syncfg.synode()))
			.start();
		}

		return (Syngleton) this;
	}

	/**
	 * Synode id for the default domain upon which the {@link ExpSynodetier} works.
	 * @return synode id
	 */
	public String synode() {
		if (syndomanagers != null)
			for (SynDomanager domanager : syndomanagers.values())
				return domanager.synode;
		return null;
	}

	/**
	 * Issue 2d58a13eadc2ed2ee865e0609fe1dff33bf26da7:
	 * Syn-change handlers cannot be created without syntity tables have beeb created.
	 * 
	 * @param cfg
	 * @param configFolder
	 * @param cfgxml
	 * @param runtimeRoot
	 * @param rootKey
	 * @param peers 
	 * @throws Exception
	 */
	public static void setupSyntables(SynodeConfig cfg, Iterable<SyntityMeta> entms,
			String configFolder, String cfgxml, String runtimeRoot, String rootKey) throws Exception {

		// 1. connection
		Utils.logi("Initializing synode singleton with configuration file %s\n"
				+ "runtime root: %s\n"
				+ "configure folder: %s\n"
				+ "root-key length: %s",
				cfgxml, runtimeRoot, configFolder, len(rootKey));

		Configs.init(configFolder, cfgxml);
		Connects.init(configFolder);

		DATranscxt.configRoot(configFolder, runtimeRoot);
		DATranscxt.key("user-pswd", rootKey);
		
		Utils.logi("Initializing session with default jdbc connection %s ...", Connects.defltConn());

		AnSession.init(defltScxt);
		
		// 2 syn-tables
		SynChangeMeta chm;
		SynSubsMeta sbm;
		SynchangeBuffMeta xbm;
		SynSessionMeta ssm;
		PeersMeta prm;
		SynodeMeta synm;
	
		chm  = new SynChangeMeta();
		sbm  = new SynSubsMeta(chm);
		xbm  = new SynchangeBuffMeta(chm);
		ssm  = new SynSessionMeta();
		prm  = new PeersMeta();
		synm = new SynodeMeta(cfg.synconn);
	
		setupSqliTables(cfg.synconn, false, synm, chm, sbm, xbm, prm, ssm);

		setupSqlitables(cfg.synconn, false, entms);

		// 3 symantics and entities 
		synmap = DATranscxt.initConfigs(cfg.synconn, DATranscxt.loadSemanticsXml(cfg.synconn),
			(c) -> new DBSynTransBuilder.SynmanticsMap(cfg.synode(), c));

		DatasetCfg.init(configFolder);

		// 4. synodes
		initSynodeRecs(cfg, cfg.peers);
	}

	/**
	 * Setup sqlite manage database tables, oz_autoseq, a_users with sql script files,
	 * i. e., oz_autoseq.ddl, oz_autoseq.sql, a_users.sqlite.sql.
	 * 
	 * Should be called on for installation.
	 * 
	 * Triggering semantics handler parsing.
	 * 
	 * @since 0.2.0
	 * 
	 * @param conn
	 * @throws Exception 
	 */
	public static void setupSysRecords(SynodeConfig cfg, Iterable<SyncUser> tieradmins) throws Exception {
	
		ArrayList<String> sqls = new ArrayList<String>();
		IUser usr = DATranscxt.dummyUser();
		defltScxt = new DATranscxt(cfg.sysconn);
	
		for (String tbl : new String[] {"oz_autoseq", "a_users", "a_roles", "a_orgs"}) {
			sqls.add("drop table if exists " + tbl);
			Connects.commit(cfg.sysconn, usr, sqls);
			sqls.clear();
		}
	
		for (String tbl : new String[] {
					"oz_autoseq.ddl",
					"oz_autoseq.sql",
					"a_users.sqlite.ddl",
					"a_roles.sqlite.ddl",
					"a_orgs.sqlite.ddl",}) {
	
			sqls.add(loadTxt(SynotierJettyApp.class, tbl));
			Connects.commit(cfg.sysconn, usr, sqls);
			sqls.clear();
		}
		
		if (tieradmins != null) {
			JUserMeta usrm = new JUserMeta(cfg.sysconn);
			JUserMeta um = new JUserMeta();
			Insert ins = null;
			for (SyncUser admin : tieradmins) {
				Insert i = defltScxt.insert(um.tbl, usr)
						.nv(usrm.org, admin.orgId())
						.nv(usrm.pk, admin.uid())
						.nv(usrm.pswd, admin.pswd())
						.nv(usrm.uname, admin.userName())
						;
	
				if (ins == null)
					ins = i;
				else ins.post(i);
			}
			if (ins != null)
				ins.ins(defltScxt.instancontxt(cfg.sysconn, usr));
		}
	}

	/**
	 * Initialize syn_* tables' records, must be called after {@link SynodetierJoinTest#initSysRecords()}.
	 * 
	 * @param conn
	 * @throws Exception 
	 */
	static void initSynodeRecs(SynodeConfig cfg, Synode[] peers) throws Exception {
		IUser usr = DATranscxt.dummyUser();
		
		if (peers != null && peers.length > 0) {
			tb0 = new DATranscxt(cfg.synconn);
			SynodeMeta synm = new SynodeMeta(cfg.synconn);
			Delete del = tb0.delete(synm.tbl, usr)
						.whereEq(synm.domain, cfg.domain);
			for (Synode sn : peers) {
				del.post(sn.insertRow(synm, 
						tb0.insert(synm.tbl, usr)));
			}
			del.d(tb0.instancontxt(cfg.synconn, usr));
		}
	}

	public static void cleanDomain(SynodeConfig cfg)
			throws Exception {
		IUser usr = DATranscxt.dummyUser();

		SynodeMeta    synm = new SynodeMeta(cfg.synconn);
		SynChangeMeta chgm = new SynChangeMeta (cfg.synconn);
		SynSubsMeta   subm = new SynSubsMeta (chgm, cfg.synconn);
		SynchangeBuffMeta xbfm = new SynchangeBuffMeta(chgm, cfg.synconn);

		if (tb0 == null)
			tb0 = new DATranscxt(cfg.synconn);
		
		tb0.delete(synm.tbl, usr)
			.whereEq(synm.domain, cfg.domain)
			.post(tb0.delete(chgm.tbl)
					.whereEq(chgm.domain, cfg.domain))
			.post(tb0.delete(subm.tbl)
					.where(op.isNotnull, subm.changeId, new ExprPart()))
			.post(tb0.delete(xbfm.tbl)
					.where(op.isNotnull, xbfm.changeId, new ExprPart()))
			.d(tb0.instancontxt(cfg.synconn, usr));
	}

	public static void cleanSynssions(SynodeConfig cfg) throws Exception {
		IUser usr = DATranscxt.dummyUser();

		SynChangeMeta chgm = new SynChangeMeta (cfg.synconn);
		SynSubsMeta   subm = new SynSubsMeta (chgm, cfg.synconn);
		SynchangeBuffMeta xbfm = new SynchangeBuffMeta(chgm, cfg.synconn);

		if (tb0 == null)
			tb0 = new DATranscxt(cfg.synconn);

		tb0.delete(chgm.tbl, usr)
			.whereEq(chgm.domain, cfg.domain)
			.post(tb0.delete(subm.tbl)
					.where(op.isNotnull, subm.changeId, new ExprPart()))
			.post(tb0.delete(xbfm.tbl)
					.where(op.isNotnull, xbfm.changeId, new ExprPart()))
			.d(tb0.instancontxt(cfg.synconn, usr));
	}
}
