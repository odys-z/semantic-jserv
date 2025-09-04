package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.DateFormat.early;
import static io.odysz.common.LangExt._0;
import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.LangExt.shouldnull;
import static io.odysz.common.LangExt.mustnonull;
import static io.odysz.common.LangExt.ifnull;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.logT;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;
import java.util.HashMap;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonException;
import io.odysz.anson.AnsonField;
import io.odysz.anson.JsonOpt;
import io.odysz.common.Configs;
import io.odysz.common.DateFormat;
import io.odysz.common.EnvPath;
import io.odysz.common.FilenameUtils;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.JServUrl;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.meta.SyntityMeta;
import io.odysz.semantic.util.DAHelper;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.parts.Logic.op;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.meta.DocOrgMeta;
import io.oz.syn.DBSynTransBuilder;
import io.oz.syn.registry.SynodeConfig;
import io.oz.syn.registry.Syntities;
import io.oz.syn.registry.YellowPages;

/**
 * @since 0.7.0
 */
public class AppSettings extends Anson {
//	static final String day0 = "1911-10-10";

	/** 
	 * Configuration file name.
	 */
	static String serv_json = "settings.json";

	@AnsonField(ignoreFrom=true, ignoreTo=true)
	String webinf;
	
	/** <pre>
	 * !root-key && !install-key: error
	 * !root-key &&  install-key: install
	 * 
	 *  root-key && !install-key: boot
	 *  root-key &&  install-key: warn and clean install-key, boot
	 * </pre>
	 * @param config_xml
	 * @param cfg 
	 * @return
	 * @throws Exception
	 */
	public AppSettings setupdb(String url_path, String config_xml, SynodeConfig cfg, boolean forceTest) throws Exception {
		if (isblank(rootkey)) {
			mustnonull(installkey, "[AppSettings] Install-key cannot be null if root-key is empty.");
			if (len(jservs) == 0)
				Utils.warn("Jservs Shouldn't be empty, unless this node is setup for joining a domain. synode: %s", cfg.synode());

			Syngleton.defltScxt = new DATranscxt(cfg.sysconn);
			setupdb(cfg, url_path, webinf, config_xml, installkey, forceTest);

			rootkey = installkey;
			installkey = null;
		}
		else if (isblank(installkey)) {
			shouldnull(installkey, "[AppSettings] Install-key must be cleared after root-key has been set.");
			installkey = null;
		}
		return this;
	}
	
	/**
	 * 1. setup with {@link Syngleton#setupSyntables(SynodeConfig, SyntityMeta[], String, String, String, String, boolean...)}<br>
	 * 2. load with {@link DBSynTransBuilder#synSemantics(DATranscxt, String, String, Syntities)}<br>
	 * 3. {@link AppSettings#setupJserv(SynodeConfig, String)}<br>
	 * 
	 * @param cfg
	 * @param url_path
	 * @param webinf config root, path of cocnfig.xml, e. g. WEB-INF
	 * @param envolume volume home variable, e. g. $VOLUME_HOME
	 * @param config_xml config file name, e. g. config.xml
	 * @param rootkey
	 * @throws Exception
	 */
	public void setupdb(SynodeConfig cfg, String url_path, String webinf, String config_xml,
			String rootkey, boolean forceTest) throws Exception {
		
		Syngleton.setupSysRecords(cfg, YellowPages.robots());

		Syntities regists = Syntities.load(webinf, f("%s/syntity.json", "$" + vol_name), 
			(synreg) -> {
				throw new SemanticException("Configure meta as class name in syntity.json %s", synreg.table);
			});
		
		if (!isblank(regists.conn()) && !eq(cfg.synconn, regists.conn()))
			throw new SemanticException(
				"Synode configuration's syn-conn dosen't match regists' conn id (which can be null), %s != %s.",
				cfg.synconn, regists.conn());
		
		Syngleton.setupSyntables(cfg, regists.metas.values().toArray(new SyntityMeta[0]),
				webinf, config_xml, ".", rootkey, forceTest);

		DBSynTransBuilder.synSemantics(new DATranscxt(cfg.synconn), cfg.synconn, cfg.synode(), regists);

		setupJserv(cfg);
	}
	
	/**
	 * @param cfg
	 * @param webinf
	 * @param envolume
	 * @param config_xml
	 * @param rootkey
	 * @throws Exception
	 */
	public static void rebootdb(SynodeConfig cfg, String webinf, String envolume, String config_xml,
			String rootkey) throws Exception {
		
		Syntities regists = Syntities.load(webinf, f("%s/syntity.json", envolume), 
			(synreg) -> {
				throw new SemanticException("Configure meta as class name in syntity.json %s", synreg.table);
			});
		
		Syngleton.bootSyntables(cfg, webinf, config_xml, ".", rootkey);

		for (SyntityMeta m : regists.metas.values())
			m.replace();

		DBSynTransBuilder.synSemantics(new DATranscxt(cfg.synconn), cfg.synconn, cfg.synode(), regists);
	}

	/**
	 * Update syn_node.jserv = this.jservs[cfg.peers.id].
	 * @param cfg
	 * @return this
	 * @throws TransException
	 * @throws SQLException 
	 */
	public AppSettings setupJserv(SynodeConfig cfg) throws TransException, SQLException {

		SynodeMeta synm = new SynodeMeta(cfg.synconn);

		for (String peer : jservs.keySet()) {
			if (eq(cfg.synode(), peer)) 
				logT(new Object() {}, "Ignoring updating jserv to local node: %s", peer);
			else
				updateNewJserv(cfg.synconn, cfg.domain, synm, peer, jservs.get(peer), null, cfg.synode());
		}
		return this;
	}

	/**
	 * Thanks to https://stackoverflow.com/a/38342964/7362888
	 * @param retries default 11
	 * @return local ip, 127.0.0.1 if is offline (got 0:0:0:0:0:0:0:0:0).
	 */
	public static String getLocalIp(int ... retries) {
	    try(final DatagramSocket socket = new DatagramSocket()) {
	    	boolean succeed = false;
	    	int tried = 0;
	    	while (!succeed && tried++ < _0(retries, 11) + 1)
	    		try {
	    			socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
	    			succeed = true;
	    		} catch (IOException e) {
	    			// starting service at network interface not ready yet
	    			Utils.warn("Network interface is not ready yet? Try again ...");
	    			try {
						Thread.sleep(3000);
					} catch (InterruptedException e1) { }
	    		}

	    	if (socket.getLocalAddress() == null ||
	    		eq(socket.getLocalAddress().getHostAddress(), "0:0:0:0:0:0:0:0"))
	    		return "127.0.0.1";

	    	return socket.getLocalAddress().getHostAddress();
		} catch (SocketException e) {
			return "127.0.0.1";
		}
	}

	/**
	 * Persist jsev url into table syn_synode, if the time stamp is new.
	 * 
	 * @param synconn
	 * @param domain
	 * @param synm
	 * @param peer
	 * @param servurl
	 * @param timestamp their timestamp, must newer than mine to update db
	 * @param src_node where does this version come from
	 * @return true if the one in database is older and have been replaced, a.k.a. dirty 
	 * @return true if the newer version is accepted
	 * @throws TransException
	 * @throws SQLException
	 */
	public static boolean updateNewJserv(String synconn, String domain, SynodeMeta synm,
			String peer, String servurl, String timestamp_utc, String src_node)
			throws TransException, SQLException {

		DATranscxt tb = null;
		try { 
			tb = new DATranscxt(synconn);
			String optime = DAHelper.getValstr(tb, synconn, synm, synm.optime, synm.pk, peer, synm.domain, domain);
			if (early(timestamp_utc, optime));
				return false;
		} catch (ParseException e) {
			e.printStackTrace();
		}
			
		IUser robot = DATranscxt.dummyUser();

		String timestamp = ifnull(timestamp_utc, DateFormat.jour0);
		logi("[%s] Setting peer %s's jserv: %s [%s]", domain, peer, servurl, timestamp);

		tb.update(synm.tbl, robot)
			.nv(synm.jserv, servurl)
			.nv(synm.optime, timestamp)
			.nv(synm.oper,  src_node)
			.whereEq(synm.pk, peer)
			.whereEq(synm.domain, domain)
			.where(op.le, Funcall.isnull(synm.optime, Funcall.toDate(DateFormat.jour0)), Funcall.toDate(timestamp))
			.u(tb.instancontxt(synconn, robot));
		
		return true;
	}

	public String vol_name;
	public String volume;

	/** UTC time of dirty */
	public String jserv_utc;
	public HashMap<String, String> jservs;

	public String installkey;
	public String rootkey;

	/**
	 * Json file path.
	 */
	@AnsonField(ignoreTo=true, ignoreFrom=true)
	public String json;

	/**
	 * Configure for on-load callback event handling.
	 * [0] handler class name which implements {@link ISynodeLocalExposer}; [1:] path to private/host.json
	 */
	public String[] startHandler;

	/**
	 * @since 0.2.6, only works for a readable configuration.
	 */
	@AnsonField(ignoreFrom=true)
	public String localIp;

	/**
	 * Synode IP exposed through a proxy.
	 * @since 0.2.5
	 */
	public String proxyIp;

	/** jserv port */
	public int port;
	/** Get jserv port */
	public String port() { return String.valueOf(port); }

	/** jserv port */
	public int proxyPort;
	/** Get jserv port */
	public String proxyPort() { return String.valueOf(proxyPort); }

	/** web page port */
	public int webport = 8900;

	/**
	 * Synode port exposed through a proxy.
	 * @since 0.2.5
	 */
	public int webProxyPort;

	/**
	 * Is this synode behind a reverse proxy?
	 * @since 0.2.5
	 */
	public boolean reverseProxy;

	public HashMap<String, String> envars;

	/** Connection Idle Seconds */
	public float connIdleSnds;

	/**
	 * Should only be used in win-serv mode.
	 * @param web_inf
	 * @return Settings
	 * @throws AnsonException
	 * @throws IOException
	 */
	public static AppSettings load(String web_inf, String... json) throws AnsonException, IOException {
		String abs_json = FilenameUtils.concat(EnvPath.replaceEnv(web_inf), _0(json, serv_json));
		logi("[AppSettings] Loading settings from %s", abs_json);

		FileInputStream inf = new FileInputStream(new File(abs_json));

		AppSettings settings = (AppSettings) Anson.fromJson(inf);
		if (settings.jservs == null)
			settings.jservs = new HashMap<String, String>();
		settings.webinf = web_inf;
		settings.json = abs_json;

		return settings;
	}
	
	/**
	 * Move to Antson?
	 * @return
	 * @throws AnsonException
	 * @throws IOException
	 */
	public AppSettings save() throws AnsonException {
		try (FileOutputStream inf = new FileOutputStream(new File(json))) {
			toBlock(inf, JsonOpt.beautify());
		} catch (IOException e) {
			e.printStackTrace();
			throw new AnsonException(e);
		} 
		return this;
	}
	
	public AppSettings() {
		installkey = "0123456789ABCDEF";
		jservs = new HashMap<String, String>();
	}
	
	/**
	 * Update env-vars to system properties.
	 * @return this
	 */
	public AppSettings setEnvs(boolean print) {
		System.setProperty(vol_name, volume);
		if (print)
			logi("%s\t: %s", vol_name, volume);
		
		if (envars != null)
		for (String v : envars.keySet()) {
			System.setProperty(v, envars.get(v));
			if (print)
				logi("%s\t: %s", v, envars.get(v));
		}
		
		return this;
	}

	/**
	 * Install process: <br>
	 * 1. load settings.json<br>
	 * 2. update env-variables<br>
	 * 3. initiate connects<br>
	 * 4. setup db, create tables it's sqlite drivers for the first time, see {@link AppSettings#setupdb(String, String, SynodeConfig, boolean)}. <br>
	 * 
	 * <h5>Note:</h5>
	 * Multiple App instance must avoid defining same variables.
	 * 
	 * @param url_path e. g. "jserv-album"
	 * @param webinf
	 * @param settings_json
	 * @return local jserv (IP is self-detected)
	 * @throws Exception 
	 */
	public static AppSettings checkInstall(String url_path, String webinf,
			String config_xml, String settings_json, boolean forceDrop) throws Exception {

		logi("[INSTALL-CHECK] checking ...");
		Configs.init(webinf);

		logi("[INSTALL-CHECK] load %s ...", settings_json);
		AppSettings settings = AppSettings
							.load(webinf, settings_json)
							.setEnvs(true);

		logi("[INSTALL-CHECK] load connects with %s/* ...", webinf);
		Connects.init(webinf);

		String $vol_home = "$" + settings.vol_name;
		logi("[INSTALL-CHECK] load dictionary configuration %s/* ...", $vol_home);
		YellowPages.load(EnvPath.concat(
				new File(".").getAbsolutePath(),
				webinf,
				// 0.7.5 EnvPath.replaceEnv($vol_home)));
				// 0.7.6
				$vol_home));

		SynodeConfig cfg = YellowPages.synconfig().replaceEnvs();
		
		if (!isblank(settings.installkey)) {
			logi("[INSTALL-CHECK]\n!!! FIRST TIME INITIATION !!!\nInstall: Calling setupdb() with configurations in %s ...", config_xml);
			settings.setupdb(url_path, config_xml, cfg, forceDrop).save();
		}
		else 
			logi("[INSTALL-CHECK]\n!!! SKIP DB SETUP !!!\nStarting application without db setting ...");

		settings.updateDBJserv(cfg.https, cfg.synconn,
				new SynodeMeta(cfg.synconn), cfg.domain, cfg.synode());
		
		return settings;
	}

	public AppSettings persistDB(SynodeConfig cfg, SynodeMeta synm, String node, JServUrl jserv)
			throws TransException, SQLException {
		return persistDB(cfg, synm, node, jserv.jserv());
	}

	public AppSettings persistDB(SynodeConfig cfg, SynodeMeta synm, String node, String jserv)
			throws TransException, SQLException {
		String now = DateFormat.formatime_utc(new Date());
		updateNewJserv(cfg.synconn, cfg.domain, synm, node, jserv, now, cfg.synode());
		jservs = loadJservs(cfg, synm);
		return this;
	}
	
	public static HashMap<String, String[]> loadJservss(DATranscxt st, SynodeConfig cfg, SynodeMeta synm)
			throws SQLException, TransException {
		return synm.loadJservs(st,
				cfg.domain, rs -> JServUrl.valid(rs.getString(synm.jserv)));
	}

	public static HashMap<String, String> loadJservs(SynodeConfig cfg, SynodeMeta synm)
			throws SQLException, TransException {
		DATranscxt tb = new DATranscxt(cfg.synconn);

		return ((AnResultset) tb.select(synm.tbl)
		  .cols(synm.jserv, synm.synoder, synm.optime)
		  .whereEq(synm.domain, cfg.domain)
		  .rs(tb.instancontxt(cfg.synconn, DATranscxt.dummyUser()))
		  .rs(0))
		  .map(synm.synoder,
			  (rs) -> rs.getString(synm.jserv),
			  (rs) -> JServUrl.valid(rs.getString(synm.jserv)));
	}
	
	public AppSettings persistDB(SynodeConfig cfg, SynodeMeta synm,
			HashMap<String, String[]> jservs_time) throws TransException, SQLException {
		if (jservs_time != null) {
			for (String n : jservs_time.keySet()) 
				updateNewJserv(cfg.synconn, cfg.domain, synm, n,
								jservs_time.get(n)[0], jservs_time.get(n)[1], cfg.synode());
			
			jservs = loadJservs(cfg, synm);
		}
		return this;
	}

	/**
	 * Update my syn_node.jserv url according to settings and local IP.
	 * @param https
	 * @param synconn can be null, for ignoring db update
	 * @param synm can be null, for ignoring db update
	 * @param mysid
	 * @return jserv-url
	 * @throws TransException
	 * @throws SQLException
	 */
	private String updateDBJserv(boolean https,
			String synconn, SynodeMeta synm, String domain, String mysid) throws TransException, SQLException {
		String ip = getLocalIp();

		IUser robot = DATranscxt.dummyUser();
		try {
			String servurl = new JServUrl(https, ip, port).jserv();

			DATranscxt tb = new DATranscxt(synconn);
			tb.update(synm.tbl, robot)
			  .nv(synm.jserv, servurl)
			  .whereEq(synm.pk, mysid)
			  .whereEq(synm.domain, domain)
			  .u(tb.instancontxt(synconn, robot));
			
			this.jservs.put(mysid, servurl);

			return servurl;
		} catch (Exception e) {
			e.printStackTrace();
			throw new TransException(e.getMessage());
		}
	}

	/** Find jserv from {@link #jservs}. */
	public String jserv(String nid) {
		return jservs.get(nid);
	}

	public String jserv(String peer, String jserv) {
		if (jservs == null)
			jservs = new HashMap<String, String>();
		jservs.put(peer, jserv);
		return jserv;
	}

	public AppSettings jservs(HashMap<String, String[]> jservs) {
		this.jservs.clear();
		if (jservs != null)
		for (String n : jservs.keySet())
			this.jservs.put(n, jservs.get(n)[0]);
		return this;
	}

	/**
	 * Must be called after DA layer initiation is finished.
	 * @throws Exception 
	 */
	public static void updateOrgConfig(SynodeConfig cfg, AppSettings settings) throws Exception {
		DocOrgMeta orgMeta = new DocOrgMeta(cfg.sysconn);
		IUser rob = DATranscxt.dummyUser();
		DATranscxt st = new DATranscxt(cfg.sysconn);
		st.update(orgMeta.tbl, rob)
			.nv(orgMeta.webNode, EnvPath.replaceEnv(cfg.org.webroot))
			.whereEq(orgMeta.pk, cfg.org.orgId)
			.u(st.instancontxt(cfg.sysconn, rob));
	}

	public String getLocalHostJson() {
		return startHandler[1];
	}

	/**
	 * @param https
	 * @return "http(s)://ip:web-port", while ip, port = proxy or local ip, port
	 */
	public String getLocalWebroot(boolean https) {
		return f("%s://%s", https ? "https" : "http",
				this.reverseProxy ? this.webrootProxy(https) : this.webrootLocal(https));
	}

	/**
	 * @param https
	 * @return proxy ip:port, no "http(s)://"
	 */
	private String webrootProxy(boolean https) {
		if (!https && webProxyPort == 80 || https && webProxyPort == 443)
			return proxyIp;
		else
			return f("%s:%s", proxyIp, webProxyPort);
	}

	private String webrootLocal(boolean https) {
		if (!https && webport == 80 || https && webport == 443)
			return localIp;
		else
			return f("%s:%s", localIp, webport);
	}
}
