package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.HashMap;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.syn.DBSyntableBuilder;
import io.odysz.semantic.syn.SyncRobot;
import io.odysz.semantic.syn.Synode;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.ExpSynodetier;
import io.oz.jserv.docs.syn.SynDomanager;
import io.oz.jserv.docs.syn.SynDomanager.OnDomainUpdate;
import io.oz.synode.jclient.SynodeConfig;

/**
 * @since 0.2.0
 */
public class Syngleton extends JSingleton {

	/**
	 * Load configurations, setup connections and semantics, setup session module.
	 * 
	 * @param cfgxml name of config.xml, to be optimized
	 * @param conn0 default connection accept updatings from doclients
	 * @param runtimeRoot
	 * @param configFolder, folder of connects.xml, config.xml and semnatics.xml
	 * @param rootKey, e.g. context.xml/parameter=root-key
	 * @return synode id (configured in @{code cfgxml})
	 * @throws Exception 
	 */
	public static String initSynodetier(SynodeConfig cfg, String cfgxml, String runtimeRoot,
			String configFolder, String rootKey) throws Exception {

		Utils.logi("Initializing synode with configuration file %s\n"
				+ "runtime root: %s\n"
				+ "configure folder: %s\n"
				+ "root-key length: %s",
				cfgxml, runtimeRoot, configFolder, len(rootKey));

		Configs.init(configFolder, cfgxml);
		Connects.init(configFolder);

		DATranscxt.configRoot(configFolder, runtimeRoot);
		DATranscxt.key("user-pswd", rootKey);
		
		DatasetCfg.init(configFolder);
		// String synode = Configs.getCfg(Configs.keys.synode);

		DATranscxt.initConfigs(cfg.synconn, DATranscxt.loadSemantics(cfg.synconn),
			(c) -> new DBSyntableBuilder.SynmanticsMap(cfg.synode(), c));
			
		defltScxt = new DATranscxt(cfg.sysconn);
			
		Utils.logi("Initializing session with default jdbc connection %s ...", Connects.defltConn());

		AnSession.init(defltScxt);
		
		// YellowPages.load(FilenameUtils.concat(configFolder, EnvPath.replaceEnv("$VOLUME_HOME")));
		
		return cfg.synode();
	}

	/**
	 * Create a Jetty instance at local host, jserv-root
	 * for accessing online is in field {@link #jserv}.
	 * 
	 * Tip: list all local tcp listening ports:
	 * sudo netstat -ntlp
	 * see https://askubuntu.com/a/328293
	 * 
	 * @param configPath
	 * @param cfg
	 * @param configxml
	 * @param bindIp
	 * @param port
	 * @param robotInf information for creating robot, i. e. the user identity for login to peer synodes.
	 * @return Jetty App
	 * @throws Exception
	 */
	public static SynotierJettyApp instanserver(String configPath, SynodeConfig cfg, String configxml,
			String bindIp, int port, SyncRobot robt) throws Exception {
	
	    AnsonMsg.understandPorts(Port.syntier);
	
		String synid = initSynodetier(cfg, configxml, ".", configPath, "ABCDEF0123456789");
	
	    SynotierJettyApp synapp = new SynotierJettyApp(synid);
	    
	    // Syngleton single = getInstance();
	
		syst = new DATranscxt(cfg.sysconn);

	    synapp.syngleton.synconn = cfg.synconn;
		synapp.syngleton.robot = robt;
	
	    if (isblank(bindIp) || eq("*", bindIp)) {
	    	synapp.server = new Server();
	    	ServerConnector httpConnector = new ServerConnector(synapp.server);
	        httpConnector.setHost("0.0.0.0");
	        httpConnector.setPort(port);
	        httpConnector.setIdleTimeout(5000);
	        synapp.server.addConnector(httpConnector);
	    }
	    else
	    	synapp.server = new Server(new InetSocketAddress(bindIp, port));
	
	    InetAddress inet = InetAddress.getLocalHost();
	    String addrhost  = inet.getHostAddress();
		synapp.syngleton.jserv = String.format("http://%s:%s", bindIp == null ? addrhost : bindIp, port);
	
	    synapp.syngleton.synodetiers = new HashMap<String, HashMap<String, SynDomanager>>();
	    
	    return synapp;
	}

//	private static Syngleton syngleton; 
//	public static Syngleton getInstance() {
//		if (syngleton == null)
//			syngleton = new Syngleton();
//		return syngleton;
//	}

	String jserv;

	String synconn;
	DBSyntableBuilder synb;

	String sysconn;
	static DATranscxt syst;

	String synode;
	// DATranscxt t0; 
	SyncRobot robot;

	public void updateJservs(SynodeMeta synm, SynodeConfig cfg, String domain) throws TransException, SQLException {
		if (!eq(domain, cfg.domain))
			throw new SemanticException("Updating domain %s, but got configuration of %s.", domain, cfg.domain);

		for (Synode sn : cfg.peers())
			syst.update(synm.tbl, robot)
				.nv(synm.jserv, jserv)
				.whereEq(synm.pk, sn.jserv)
				.whereEq(synm.domain, cfg.domain)
				.u(syst.instancontxt(cfg.sysconn, robot));
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

		AnResultset rs = (AnResultset) syst
				.select(synm.tbl)
				.groupby(synm.domain)
				.groupby(synm.synoder)
				.whereEq(synm.pk, synode)
				.rs(syst.instancontxt(synconn, robot))
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
			for (SynDomanager dmgr : synodetiers.get(syntier_url).values()) {
				dmgr.loadSynclients(synb, robot)
					.openUpdateSynssions(robot,
						(domain, mynid, peer, repb, xp) -> {
							if (!isNull(onok))
								onok[0].ok(domain, mynid, peer, repb, xp);
						});
			}
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
	 * Initialize syn_* tables' records, must be called after {@link SynodetierJoinTest#initSysRecords()}.
	 * 
	 * @param conn
	 * @throws TransException 
	 * @throws SQLException 
	 */
	public static void initSynodeRecs(SynodeConfig cfg, Synode[] peers) throws TransException, SQLException {
		IUser usr = DATranscxt.dummyUser();
	
		/*
		ArrayList<String> sqls = new ArrayList<String>();
		try {
			for (String tbl : new String[] {
					"syn_synode_all_ready.sqlite.sql"}) {
	
				sqls.add(loadTxt(DoclientierTest.class, tbl));
				Connects.commit(conn, usr, sqls);
				sqls.clear();
			}
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		*/
		
		if (peers != null && peers.length > 0) {
			Insert inst = syst.insert("", usr);
			SynodeMeta synm = new SynodeMeta(cfg.synconn);
			for (Synode sn : peers)
				sn.insertRow(synm, inst);
			inst.ins(syst.instancontxt(cfg.synconn, usr));
		}
	}

}
