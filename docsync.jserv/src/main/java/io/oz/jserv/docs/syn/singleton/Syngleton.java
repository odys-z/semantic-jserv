package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.Utils.loadTxt;
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
import io.odysz.semantic.syn.DBSyntableBuilder;
import io.odysz.semantic.syn.DBSyntableBuilder.SynmanticsMap;
import io.odysz.semantic.syn.SyncRobot;
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
	static SynmanticsMap synmap;

	static DBSyntableBuilder synb;

	String jserv;

	String synconn;

	String sysconn;
	String synode;
	SyncRobot robot;

	public void updatePeerJservs(SynodeMeta synm, SynodeConfig cfg, String domain)
			throws TransException, SQLException {
		if (!eq(domain, cfg.domain))
			throw new SemanticException(
				"Updating domain %s, but got configuration of %s.",
				domain, cfg.domain);

		for (Synode sn : cfg.peers())
			synb.update(synm.tbl, robot)
				.nv(synm.jserv, sn.jserv)
				.whereEq(synm.pk, sn.synid)
				.whereEq(synm.domain, cfg.domain)
				.u(synb.instancontxt(cfg.synconn, robot));
	}

	/**
	 * Last (bug?) url pattern (key in {@link #synodetiers}) of {@link ExpSynodetier}.
	 */
	String syntier_url;
	/**
	 * { servlet-url-pattern: { domain: domanager } }, only instance of {@link ExpSynodetier},<br>
	 * e. g. { docs.sync: { zsu: { new SnyDomanger(x, y) } }
	 */
	public HashMap<String, HashMap<String, SynDomanager>> synodetiers;

	SynodeMeta synm; 

	/**
	 * Load domains from syn_synode, create {@link SynDomanager} for each domain.
	 * 
	 * @param synmod synode mode, peer, hub, etc.
	 * @return singleton
	 * @throws Exception
	 * @since 0.2.0
	 */
	public Syngleton loadDomains(SynodeMode synmod) throws Exception {
		if (synodetiers == null)
			synodetiers = new HashMap<String, HashMap<String, SynDomanager>>();

		synm = new SynodeMeta(synconn); 

		AnResultset rs = (AnResultset) defltScxt
				.select(synm.tbl)
				.groupby(synm.domain)
				.groupby(synm.synoder)
				.whereEq(synm.pk, synode)
				.rs(defltScxt.instancontxt(synconn, robot))
				.rs(0);
		
		while (rs.next()) {
			String domain = rs.getString(synm.domain);
			SynDomanager domanger = new SynDomanager(
					synm, rs.getString(synm.org),
					domain, synode,
					synconn, synmod, Connects.getDebug(synconn));
			synodetiers.get(syntier_url).put(domain, domanger);
		}

		return this;
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
		if (synodetiers != null && synodetiers.containsKey(syntier_url)) {
			new Thread(()->{
				for (SynDomanager dmgr : synodetiers.get(syntier_url).values()) {
					try {
						dmgr.loadSynclients(synb, robot)
							.openUpdateSynssions(robot);
					} catch (AnsonException | SsException | IOException | TransException | SQLException e) {
						e.printStackTrace();
					}
				}
				if (!isNull(onok))
					onok[0].ok(null, synode, null, null);
			}, f("[%s] Open Domain", synode))
			.start();
		}

		return (Syngleton) this;
	}

	/**
	 * Synode id for the default domain upon which the {@link ExpSynodetier} works.
	 * @return synode id
	 */
	public String synode() {
		if (synodetiers != null && synodetiers.containsKey(syntier_url))
			for (SynDomanager domanager : synodetiers.get(syntier_url).values())
				return domanager.synode;
		return null;
	}

	/**
	 * Issue 2d58a13eadc2ed2ee865e0609fe1dff33bf26da7:
	 * Syn-change handlers cannot be created without syntity tables are created.
	 * 
	 * @param cfg
	 * @param entms Syntity metas
	 * @param configFolder
	 * @param cfgxml
	 * @param runtimeRoot
	 * @param rootKey
	 * @throws Exception
	 */
	public static void setupSyntables(SynodeConfig cfg, ArrayList<SyntityMeta> entms,
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
		
		// 2. syn-tables
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
		synmap = DATranscxt.initConfigs(cfg.synconn, DATranscxt.loadSemantics(cfg.synconn),
			(c) -> new DBSyntableBuilder.SynmanticsMap(cfg.synode(), c));

		DatasetCfg.init(configFolder);
			
		synb = new DBSyntableBuilder(cfg.domain, cfg.synconn, cfg.synode(), cfg.mode);
			

		// 4. synodes
		initSynodeRecs(cfg, cfg.peers());
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
	public static void setupSysRecords(SynodeConfig cfg, Iterable<SyncRobot> robots) throws Exception {
	
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
		
		if (robots != null) {
			JUserMeta usrm = new JUserMeta(cfg.sysconn);
			JUserMeta um = new JUserMeta();
			Insert ins = null;
			for (SyncRobot robot : robots) {
				Insert i = defltScxt.insert(um.tbl, usr)
						.nv(usrm.org, robot.orgId())
						.nv(usrm.pk, robot.uid())
						.nv(usrm.pswd, robot.pswd())
						.nv(usrm.uname, robot.userName())
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
	 * @throws TransException 
	 * @throws SQLException 
	 */
	static void initSynodeRecs(SynodeConfig cfg, Synode[] peers) throws TransException, SQLException {
		IUser usr = DATranscxt.dummyUser();
		
		if (peers != null && peers.length > 0) {
			SynodeMeta synm = new SynodeMeta(cfg.synconn);
			Delete del = synb.delete(synm.tbl, usr)
						.whereEq(synm.domain, cfg.domain);
			for (Synode sn : peers) {
				del.post(sn.insertRow(synm, 
						synb.insert(synm.tbl, usr)));
			}
			del.d(synb.instancontxt(cfg.synconn, usr));
		}
	}

	public static void cleanDomain(SynodeConfig cfg)
			throws TransException, SQLException {
		IUser usr = DATranscxt.dummyUser();

		SynodeMeta    synm = new SynodeMeta(cfg.synconn);
		SynChangeMeta chgm = new SynChangeMeta (cfg.synconn);
		SynSubsMeta   subm = new SynSubsMeta (chgm, cfg.synconn);
		SynchangeBuffMeta xbfm = new SynchangeBuffMeta(chgm, cfg.synconn);

		synb.delete(synm.tbl, usr)
			.whereEq(synm.domain, cfg.domain)
			.post(synb.delete(chgm.tbl)
					.whereEq(chgm.domain, cfg.domain))
			.post(synb.delete(subm.tbl)
					.where(op.isNotnull, subm.changeId, new ExprPart()))
			.post(synb.delete(xbfm.tbl)
					.where(op.isNotnull, xbfm.changeId, new ExprPart()))
			.d(synb.instancontxt(cfg.synconn, usr));
	}

	public static void cleanSynssions(SynodeConfig cfg)
			throws TransException, SQLException {
		IUser usr = DATranscxt.dummyUser();

		// SynodeMeta    synm = new SynodeMeta(cfg.synconn);
		SynChangeMeta chgm = new SynChangeMeta (cfg.synconn);
		SynSubsMeta   subm = new SynSubsMeta (chgm, cfg.synconn);
		SynchangeBuffMeta xbfm = new SynchangeBuffMeta(chgm, cfg.synconn);

		synb.delete(chgm.tbl, usr)
			.whereEq(chgm.domain, cfg.domain)
			.post(synb.delete(subm.tbl)
					.where(op.isNotnull, subm.changeId, new ExprPart()))
			.post(synb.delete(xbfm.tbl)
					.where(op.isNotnull, xbfm.changeId, new ExprPart()))
			.d(synb.instancontxt(cfg.synconn, usr));
	}
}
