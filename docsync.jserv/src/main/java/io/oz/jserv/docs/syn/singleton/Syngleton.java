package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.is;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.Utils.loadTxt;
import static io.odysz.common.LangExt.musteqs;
import static io.odysz.common.LangExt.shouldeq;
import static io.odysz.semantic.meta.SemanticTableMeta.setupSqliTables;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.odysz.anson.AnsonException;
import io.odysz.common.Configs;
import io.odysz.common.DateFormat;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantic.meta.AutoSeqMeta;
import io.odysz.semantic.meta.PeersMeta;
import io.odysz.semantic.meta.SynChangeMeta;
import io.odysz.semantic.meta.SynDocRefMeta;
import io.odysz.semantic.meta.SynSessionMeta;
import io.odysz.semantic.meta.SynSubsMeta;
import io.odysz.semantic.meta.SynchangeBuffMeta;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.meta.SyntityMeta;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.ExchangeException;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Delete;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.meta.DocOrgMeta;
import io.oz.jserv.docs.syn.DocUser;
import io.oz.jserv.docs.syn.ExpSynodetier;
import io.oz.jserv.docs.syn.SynDomanager;
import io.oz.jserv.docs.syn.SynDomanager.OnDomainUpdate;
import io.oz.syn.DBSynTransBuilder;
import io.oz.syn.ExessionAct;
import io.oz.syn.SyncUser;
import io.oz.syn.SyndomContext;
import io.oz.syn.Synode;
import io.oz.syn.SynodeMode;
import io.oz.syn.DBSynTransBuilder.SynmanticsMap;
import io.oz.syn.registry.SynodeConfig;

/**
 * @since 0.2.0
 */
public class Syngleton extends JSingleton {
	/**
	 * Call
	 * <pre>DATranscxt.initConfigs(cfg.synconn, DATranscxt.loadSemanticsXml(cfg.synconn),
	 * 	(c) -> new DBSynTransBuilder.SynmanticsMap(cfg.synode(), c));
	 * </pre>to load.
	 */
	SynmanticsMap synmap;

	/** */
	final DATranscxt tb0;
	
	public final SynodeConfig syncfg;

	public final AppSettings settings;

	final String sysconn;

	/**
	 * { domain: domanager },<br>
	 * e. g. { zsu: new SnyDomanger(x, y) }
	 */
	HashMap<String, SynDomanager> syndomanagers;

	/**
	 * Get domain manager
	 * @param domain
	 * @return the manager
	 */
	public SynDomanager domanager(String domain) {
		return (syndomanagers != null && syndomanagers.containsKey(domain)) ?
				syndomanagers.get(domain) : null;

	}

	public Syngleton domanagers(HashMap<String, SynDomanager> domains) {
		this.syndomanagers = domains;
		return this;
	}

	public SynodeMeta synm;

	public SyncUser synuser;

	public Syngleton(SynodeConfig cfg, SyncUser admin, AppSettings settings) throws Exception {
		sysconn = cfg.sysconn;
		syncfg = cfg;
		this.synuser  = admin;
		this.settings = settings;
		syndomanagers = new HashMap<String, SynDomanager>();
		

		tb0 = new DATranscxt(cfg.synconn);
	}

	/**
	 * Load domains from syn_synode, create {@link SynDomanager} for each domain.
	 * 
	 * @param synmod synode mode, peer, hub, etc.
	 * @param cfg 
	 * @param admin 
	 * @return singleton
	 * @throws Exception
	 * @since 0.2.0
	 */
	public HashMap<String,SynDomanager> loadomains(SynodeConfig cfg, DocUser admin) throws Exception {
		if (cfg.syncIns > 0)
			shouldeq(new Object() {}, cfg.mode, SynodeMode.peer);
		else
			shouldeq(new Object() {}, cfg.mode, SynodeMode.hub);
		
		if (syndomanagers == null)
			syndomanagers = new HashMap<String, SynDomanager>();

		synm = new SynodeMeta(cfg.synconn); 

		AnResultset rs = (AnResultset) defltScxt
				.select(synm.tbl)
				.groupby(synm.synoder)
				.whereEq(synm.pk, cfg.synode())
				.whereEq(synm.domain, cfg.domain)
				.rs(defltScxt.instancontxt(cfg.synconn, DATranscxt.dummyUser()))
				.rs(0);
		
		if (rs.getRowCount() != 1)
			throw new ExchangeException(ExessionAct.ready, null,
					"Docsync.jserv 0.2 can support one and only one domain, but found %s domain(s) in %s",
					rs.getRowCount(), cfg.synconn);
		
		if (rs.next()) {
			String domain = rs.getString(synm.domain);
			SynDomanager domanger = (SynDomanager) new SynDomanager(this, cfg, settings)
					.admin(admin.deviceId(cfg.synode()))
					.loadomainx();

			syndomanagers.put(domain, (SynDomanager) domanger
						.loadNvstamp(defltScxt, domanger.admin)
						); 
		}

		return syndomanagers;
	}

	/**
	 * Try join (login) known domains.
	 * 
	 * @deprecated only for tests
	 * 
	 * @return this
	 * @throws IOException 
	 * @throws SsException 
	 * @throws AnsonException 
	 * @throws SQLException 
	 * @throws TransException 
	 */
	public Syngleton asyOpenDomains(OnDomainUpdate ... onok) {
		if (syndomanagers != null) {
			new Thread(()->{
				for (SynDomanager dmgr : syndomanagers.values()) {
					try {
						musteqs(syncfg.domain, dmgr.domain());

						((SyncUser)AnSession
								.loadUser(syncfg.admin, sysconn))
								.deviceId(dmgr.synode);

						dmgr.loadSynclients(tb0)
							.updateSynssions(onok);
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
	 * <p>Setup syntables, can be called both while installation and reboot.</p>
	 * 1. init connects<br>
	 * 2. create sqlite syn-tables<br>
	 * 2.1 inject synmantics after syn-tables have been set.<br>
	 * 3. load symantics and entities<br>
	 * 4. create synodes, by inserting peers from registry.config.peers<br>
	 * 5. step n0 & n-stamp for clearing uncertainty<br>
	 * 
	 * <p>Resolved Issue 2d58a13eadc2ed2ee865e0609fe1dff33bf26da7:<br>
	 * Syn-change handlers cannot be created without syntity tables have been created.</p>
	 * 
	 * @param cfg
	 * @param configFolder
	 * @param cfgxml e. g. config.xml
	 * @param runtimeRoot
	 * @param rootKey
	 * @param peers 
	 * @param forcedrop optional, default false 
	 * @throws Exception
	 */
	public static void setupSyntables(SynodeConfig cfg, SyntityMeta[] entms,
			String configFolder, String cfgxml, String runtimeRoot, String rootKey, boolean ... forcedrop) throws Exception {

		// 1. connection
		Utils.logi("Initializing synode singleton with configuration file %s\n"
				+ "\truntime root: %s\n"
				+ "\tconfigure folder: %s\n"
				+ "\troot-key length: %s\n"
				+ "\tentities: [%s]",
				cfgxml, runtimeRoot, configFolder, len(rootKey),
				Stream.of(entms).map(e -> f(e.tbl)).collect(Collectors.joining(", ")));

		Configs.init(configFolder, cfgxml);
		Connects.init(configFolder);

		DATranscxt.clearSemanticsMaps();
		DATranscxt.configRoot(configFolder, runtimeRoot);
		DATranscxt.rootkey(rootKey);

		// 2 syn-tables
		AutoSeqMeta akm;
		SynChangeMeta chm;
		SynSubsMeta sbm;
		SynchangeBuffMeta xbm;
		SynDocRefMeta rfm;
		SynSessionMeta ssm;
		PeersMeta prm;
		SynodeMeta synm;
	
		akm  = new AutoSeqMeta();
		chm  = new SynChangeMeta();
		sbm  = new SynSubsMeta(chm);
		xbm  = new SynchangeBuffMeta(chm);
		rfm  = new SynDocRefMeta(cfg.synconn);
		ssm  = new SynSessionMeta();
		prm  = new PeersMeta();
		synm = new SynodeMeta(cfg.synconn);
	
		setupSqliTables(cfg.synconn, is(forcedrop), akm, synm, chm, sbm, xbm, rfm, prm, ssm);

		setupSqliTables(cfg.synconn, is(forcedrop), entms);
		
		// 2.1 inject synmantics after syn-tables have been set.
		for (SyntityMeta m : entms)
			m.replace();

		// 3 symantics and entities 
		DATranscxt.initConfigs(cfg.synconn,
			(c) -> new DBSynTransBuilder.SynmanticsMap(cfg.synode(), c));

		DatasetCfg.init(configFolder);

		// 4. synodes
		initSynodeRecs(cfg, cfg.peers);

		// 5. step n0 & n-stamp for clearing uncertainty
		SyndomContext.incN0Stamp(cfg.synconn, synm, cfg.synode(), cfg.domain);
	}

	/**
	 * Load Configures and Connections, and load Semantics' configurations.
	 * 
	 * @param cfg
	 * @param configFolder
	 * @param cfgxml
	 * @param runtimeRoot
	 * @param rootKey
	 * @throws Exception
	 * @since 0.2.0
	 */
	public static void bootSyntables(SynodeConfig cfg,
			String configFolder, String cfgxml, String runtimeRoot, String rootKey) throws Exception {
		Utils.logi("Booting synode singleton with configuration file %s\n"
				+ "runtime root: %s\n"
				+ "configure folder: %s\n"
				+ "root-key length: %s",
				cfgxml, runtimeRoot, configFolder, len(rootKey));

		Configs.init(configFolder, cfgxml);
		Connects.init(configFolder);

		DATranscxt.clearSemanticsMaps();
		DATranscxt.configRoot(configFolder, runtimeRoot);
		DATranscxt.rootkey(rootKey);
		
		DATranscxt.initConfigs(cfg.synconn,
			(c) -> new DBSynTransBuilder.SynmanticsMap(cfg.synode(), c));
		DatasetCfg.init(configFolder);
	}

	/**
	 * Setup system database tables, oz_autoseq, a_users with sql script files,
	 * i. e., oz_autoseq.ddl, oz_autoseq.sql, a_users.sqlite.sql.
	 * 
	 * Should only be called for installation.
	 * 
	 * Note: This method requires {@link #defltScxt} has been created already.
	 * 
	 * @since 0.2.0
	 * 
	 * @throws Exception 
	 */
	public static void setupSysRecords(SynodeConfig cfg, Iterable<SyncUser> synusers) throws Exception {
	
		ArrayList<String> sqls = new ArrayList<String>();
		IUser usr = DATranscxt.dummyUser();
	
		for (String tbl : new String[] {"oz_autoseq", "a_users", "a_roles", "a_orgs"}) {
			sqls.add("drop table if exists " + tbl);
			Connects.commit(cfg.sysconn, usr, sqls);
			sqls.clear();
		}
	
		for (String tbl : new String[] {
					"oz_autoseq.ddl", // FIXME inconsistent with setupSyntables()
					"a_users.sqlite.ddl",
					"a_roles.sqlite.ddl",
					"a_orgs.sqlite.ddl",}) {
	
			sqls.add(loadTxt(Syngleton.class, tbl));
			Connects.commit(cfg.sysconn, usr, sqls);
			sqls.clear();
		}
		Connects.clearMeta(cfg.sysconn);
		DATranscxt.clearSemanticsMaps();
		defltScxt = new DATranscxt(cfg.sysconn);
		
		if (synusers != null) {
			JUserMeta usrm = new JUserMeta(cfg.sysconn);
			JUserMeta um = new JUserMeta();
			Insert ins = null;
			for (SyncUser admin : synusers) {
				// TODO password in json should be cipher
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
		
		if (cfg.org != null) {
			DocOrgMeta orgm = buildOrgMeta(cfg); 
			if (orgm.generecord(cfg) != 1)
				throw new SemanticException("Initializing market/org with configuration failed:\n%s", cfg.org.toBlock());
		}
	}
	
	static DocOrgMeta buildOrgMeta(SynodeConfig cfg) throws SemanticException {
		if (!isblank(cfg.org.meta) && !eq(cfg.org.meta, DocOrgMeta.class.getName()))
			throw new SemanticException("todo");
		return new DocOrgMeta(cfg.sysconn);
	}

	/**
	 * Initialize syn_* tables' records, must be called after {@link SynodetierJoinTest#initSysRecords()}.
	 * 
	 * @param conn
	 * @throws Exception 
	 */
	static void initSynodeRecs(SynodeConfig cfg, Synode[] peers) throws Exception {
		IUser usr = DATranscxt.dummyUser();

		DATranscxt tb0 = new DATranscxt(cfg.synconn);

		if (peers != null && peers.length > 0) {
			SynodeMeta synm = new SynodeMeta(cfg.synconn);
			Delete del = tb0
						.delete(synm.tbl, usr)
						.whereEq(synm.domain, cfg.domain);

			for (Synode sn : peers) {
				musteqs(cfg.domain, sn.domain(),
						"cfg.domain, %s != peer.domain, %s", cfg.domain, sn.domain());

				del.post(sn.insertRow(cfg.domain,
						synm, tb0.insert(synm.tbl, usr)));
			}

			del.d(tb0.instancontxt(cfg.synconn, usr));
		}
	}

	public Set<String> domains() {
		return syndomanagers.keySet();
	}

//	/**
//	 * Prepare loca ip, submit, asynchronously, to hub.
//	 * @param currentIp current local ip.
//	 * @return this
//	 * @since 0.7.6
//	 * @deprecated shouldn't call this in afterboot(), which makes competing conditions with worker-0.
//	 */
//	public void asybmitJserv(ISynodeLocalExposer ipExposer) {
//		new Thread(()-> {
//			String currentIp = settings.localIp; 
//			String nextip = JServUrl.getLocalIp(2);
//			if (!eq(currentIp, nextip)) {
//				settings.localIp = nextip;
//				for (SynDomanager mngr : syndomanagers.values()) {
//					try {
//						if (mngr.mode != SynodeMode.hub) {
//							mngr.submitJservsPersist(currentIp, nextip);
//						}
//						else {
//							// Hub nodes can still changing IP often.
//							// JServUrl jsv = mngr.loadJservUrl().ip(nextip);
//							// settings.persistNewJserv(syncfg, synm, mngr.synode, jsv, DateFormat.now());
//
//							settings.mergeLoadJservs(syncfg, synm);
//							settings.save();
//						}
//
//						mngr.ipChangeHandler(ipExposer);
//						if (ipExposer != null)
//							ipExposer.onExpose(settings, mngr);
//					} catch (SQLException | TransException e) {
//						e.printStackTrace();
//					}
//				}
//			}
//		}, f("[%s] Asy-submit Jserv", synode()))
//		.start();
//	}

	public String myjserv() throws SQLException, TransException {
		settings.loadDBservs(syncfg, synm, DateFormat.jour0);
		return settings.jserv(syncfg.synid);
	}
}
