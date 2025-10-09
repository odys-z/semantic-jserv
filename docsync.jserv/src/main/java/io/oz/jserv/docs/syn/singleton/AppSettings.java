package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt._0;
import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.LangExt.shouldnull;
import static io.odysz.common.LangExt.mustnonull;
import static io.odysz.common.LangExt.mustnoBlankAny;
import static io.odysz.common.LangExt.ifnull;
import static io.odysz.common.Utils.logi;
import static io.odysz.transact.sql.parts.condition.ExprPart.constr;
import static io.odysz.transact.sql.parts.condition.Funcall.isnull;
import static io.odysz.common.DateFormat.jour0;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;

import org.xml.sax.SAXException;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonException;
import io.odysz.anson.AnsonField;
import io.odysz.anson.JsonOpt;
import io.odysz.common.Configs;
import io.odysz.common.EnvPath;
import io.odysz.common.FilenameUtils;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.jclient.SessionClient;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jprotocol.JProtocol.OnError;
import io.odysz.semantic.jprotocol.JServUrl;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.meta.SyntityMeta;
import io.odysz.semantic.util.DAHelper;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.parts.Logic.op;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.meta.DocOrgMeta;
import io.oz.syn.DBSynTransBuilder;
import io.oz.syn.SynodeMode;
import io.oz.syn.registry.Centralport;
import io.oz.syn.registry.CynodeStats;
import io.oz.syn.registry.RegistReq;
import io.oz.syn.registry.RegistResp;
import io.oz.syn.registry.SynodeConfig;
import io.oz.syn.registry.Syntities;
import io.oz.syn.registry.YellowPages;

/**
 * @since 0.7.0
 * 
 * ISSUE/DECISION Since Sep 26, 2025, AppSettings is also responsible for Synodes Networking?
 */
public class AppSettings extends Anson {

	/** 
	 * Configuration file name.
	 */
	static String setup_json = "settings.json";

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
	 * @return this
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

	public String regiserv;
	public String vol_name;
	public String volume;

	public HashMap<String, String> jservs;

	/** UTC time of dirty */
	public String jserv_utc;
	public AppSettings jserv_utc(String utc) {
		jserv_utc = utc;
		return this;
	}


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
	 * @since 0.2.6, only for showing a readable text line.
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
	 * Central password for submit jserv
	 * @since 0.2.6
	 */
	public String centralPswd;

	/**
	 * Should only be used in win-serv mode.
	 * @param web_inf
	 * @return Settings
	 * @throws AnsonException
	 * @throws IOException
	 */
	public static AppSettings load(String web_inf, String... json) throws AnsonException, IOException {
		String abs_json = FilenameUtils.concat(EnvPath.replaceEnv(web_inf), _0(json, setup_json));
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
							.updateEnvs(true);

		logi("[INSTALL-CHECK] load connects with %s/* ...", webinf);
		Connects.init(webinf);

		String $vol_home = "$" + settings.vol_name;
		logi("[INSTALL-CHECK] load dictionary configuration %s/* ...", $vol_home);
		YellowPages.load(EnvPath.concat(
				new File(".").getAbsolutePath(), webinf, $vol_home));

		SynodeConfig cfg = YellowPages.synconfig().replaceEnvs();
		
		if (!isblank(settings.installkey)) {
			logi( "[INSTALL-CHECK]\n!!! FIRST TIME INITIATION !!!\n"
				+ "Install: Calling setupdb() with configurations in %s ...",
				config_xml);
			settings.setupdb(url_path, config_xml, cfg, forceDrop).save();
		}
		else 
			logi( "[INSTALL-CHECK]\n!!! SKIP DB SETUP !!!\n"
				+ "Starting application without db setting ...");

		return settings;
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
	 * @param createrid where does this version come from
	 * @return true if the one in database is older and have been replaced, a.k.a. dirty 
	 * @return true if the newer version is accepted
	 * @throws TransException
	 * @throws SQLException
	 */
	static boolean updateLaterJserv(String synconn, String org, String domain, SynodeMeta synm,
			String peer, String servurl, String timestamp_utc, String createrid)
			throws TransException, SQLException {
		mustnoBlankAny(synconn, org, domain, peer, servurl, createrid);

		DATranscxt tb = null;
			tb = new DATranscxt(synconn);

			
		IUser robot = DATranscxt.dummyUser();

		String timestamp = ifnull(timestamp_utc, jour0);
		logi("[%s : %s] Setting peer %s's jserv: %s [%s]", synconn, domain, peer, servurl, timestamp);

		SemanticObject res = tb.update(synm.tbl, robot)
			.nv(synm.jserv, servurl)
			.nv(synm.jserv_utc, timestamp)
			.nv(synm.oper,  createrid)
			.whereEq(synm.pk, peer)
			.whereEq(synm.domain, domain)
			.whereEq(synm.org, org)
			// see comments in loadJserv()
			// .where(op.le, Funcall.isnull(synm.jserv_utc, Funcall.toDate(DateFormat.jour0)), Funcall.toDate(timestamp))
			.where(op.lt, isnull(synm.jserv_utc, constr(jour0)), constr(timestamp))
			.u(tb.instancontxt(synconn, robot));
		
		return res.total() > 0;
	}

	/**
	 * Load {@link JServUrl} of id {@link #synode} from {@link #synconn},
	 * compare the time stamp, and return the database one if databse's time stamp is later,
	 * otherwise takes no action.
	 * @param syncfg 
	 * @return JServUrl
	 * @throws SQLException
	 * @throws TransException
	 * @throws SAXException
	 */
	boolean loadJservs(SynodeConfig syncfg, SynodeMeta synm, String utc)
			throws SQLException, TransException {

		DATranscxt synb = new DATranscxt(syncfg.synconn);
		IUser robot = DATranscxt.dummyUser();

		AnResultset rs = (AnResultset) synb
				.select(synm.tbl, "t")
				.whereEq(synm.org, syncfg.org.orgId)
				.whereEq(synm.domain, syncfg.domain)
				// .whereEq(synm.synoder, synode)

				// Funcall.sql has bug for this:
				// .where(op.ge, Funcall.toDate(Funcall.isnull(synm.jserv_utc, Funcall.toDate(DateFormat.jour0))), Funcall.toDate(ifnull(utc, DateFormat.jour0)))
				// -> datetime('ifnull(optime, datetime('1911-10-10'))') >= datetime('1911-10-10')
				// 
				// And "datetime(optime) = datetime('1911-10-10')" will not work.
				//
				// This line only works for sqlite (Google AI: sqlite doesn't has built-in datetime type):
				.where(op.gt, isnull(synm.jserv_utc, constr(jour0)), constr(LangExt.ifnull(utc, jour0)))
				// -> synid = 'X' AND ifnull(optime, 1911-10-10) >= 1911-10-10
				.rs(synb.instancontxt(syncfg.synconn, robot))
				.rs(0);

		boolean loaded = false;
		while (rs.next()) {
			String jsrv = rs.getString(synm.jserv);
			if (JServUrl.valid(jsrv)) {
				jserv(rs.getString(synm.synoder), jsrv);
				loaded = true;
			}
		}
		return loaded;
		
	}
	
	public boolean mergeLoadJservs(SynodeConfig myc, SynodeMeta nm) throws TransException, SQLException {
		boolean dirty = mergeMyJserv(myc, nm);

		for (String synid : jservs.keySet()) {
			if (eq(synid, myc.synid))
				;// dirty |= mergeMyJserv(myc, nm);
			else dirty |= updateLaterJserv(
							myc.synconn, myc.org.orgId, myc.domain, nm,
							synid, jserv(synid), jserv_utc,
							// the Synoder is me, usually reaches here when the user changed HUB at a peer.
							myc.synid);
		}
		dirty |= loadJservs(myc, nm, jserv_utc);
		return dirty;
	}

	/**
	 * Persist then load to from db.
	 * @return this
	 * @throws TransException
	 * @throws SQLException
	 */
	public boolean persistLaterLoadJserv(SynodeConfig cfg, SynodeMeta synm, String node,
			String jserv, String jserv_utc) throws TransException, SQLException {
		boolean dirty = updateLaterJserv(cfg.synconn, cfg.org.orgId, cfg.domain,
										 synm, node, jserv, jserv_utc, cfg.synode());
		loadJservs(cfg, synm, jour0);
		return dirty;
	}
	
	public static HashMap<String, String[]> loadJservss(DATranscxt st, SynodeConfig cfg, SynodeMeta synm)
			throws SQLException, TransException {
		return synm.loadJservs(st,
				cfg.domain, rs -> JServUrl.valid(rs.getString(synm.jserv)));
	}

	public AppSettings persistLaterJservs(SynodeConfig cfg, SynodeMeta synm,
			HashMap<String, String[]> jservs_time) throws TransException, SQLException {
		if (jservs_time != null) {
			for (String n : jservs_time.keySet()) 
				updateLaterJserv(cfg.synconn, cfg.org.orgId, cfg.domain, synm, n,
								jservs_time.get(n)[0], jservs_time.get(n)[1], cfg.synode());
			
			loadJservs(cfg, synm, jour0);
		}
		return this;
	}

	/**
	 * Merge settings.jserv with [synconn] syn_node.jserv, resolving conflicts
	 * by comparing this.jserv_utc and syn_node.optime.
	 * 
	 * Still needs to save settings.json.
	 * 
	 * @return dirty 
	 * @throws TransException
	 * @throws SQLException
	 */
	private boolean mergeMyJserv(SynodeConfig mycfg, SynodeMeta synm) throws TransException, SQLException {
		String myid = mycfg.synode();

		if (DAHelper.count(Syngleton.defltScxt, mycfg.synconn, synm.tbl, synm.domain,
				mycfg.domain, synm.org, mycfg.org.orgId, synm.synoder, mycfg.synid) <= 0)
			throw new TransException("It's supposed to create synode [%s : %s] by configuration module.",
					mycfg.domain, mycfg.synid);
		
		if (isblank(jserv(myid)))
			jserv(myid, new JServUrl(mycfg.https, reversedIp(), reversedPort(mycfg.https)).jserv());

		return updateLaterJserv(mycfg.synconn, mycfg.org.orgId, mycfg.domain, synm,
								myid, jserv(myid), jserv_utc, myid);
	}

	/** Find jserv from {@link #jservs}. */
	public String jserv(String nid) {
		return jservs.get(nid);
	}

	public AppSettings jserv(String peer, String jserv) {
		if (jservs == null)
			jservs = new HashMap<String, String>();
		jservs.put(peer, jserv);
		return this;
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
	
	public String reversedIp() {
		return this.reverseProxy ? proxyIp :
			isblank(localIp) ? JServUrl.getLocalIp(2) : localIp;
	}
	
	public int reversedPort(boolean https) {
		if (port == 0) port = https ? 443 : 80;
		return this.reverseProxy ? proxyPort : port;
	}
	
	/**
	 * Update env-vars to system properties.
	 * @return this
	 */
	private AppSettings updateEnvs(boolean print) {
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
	 * Tells central this node is running, both as hub or as peer.
	 * @param funcuri
	 * @param cfg
	 * @param client
	 * @param errCtx
	 * @return reply
	 * @throws IOException
	 * @throws SemanticException
	 * @throws AnsonException
	 */
	public RegistResp synotifyCentral(String funcuri, SynodeConfig cfg, SessionClient client,
			OnError errCtx) throws IOException, SemanticException, AnsonException {

		AnsonHeader header = client.header()
				.act(funcuri, "syntifyCentral", RegistReq.A.submitSettings, jserv(cfg.synid));

		RegistReq req = new RegistReq(cfg.org.orgType)
				.dictionary(cfg)
				.myjserv(jserv(cfg.synid), jserv_utc)
				// see also synodepy3.InstallerCli.submit_mysettings()
				.mystate(cfg.mode == SynodeMode.hub ? CynodeStats.asHub : CynodeStats.asPeer);

		req.a(RegistReq.A.submitSettings);
		req.protocolPath = JProtocol.urlroot;

		AnsonMsg<RegistReq> q = client.userReq(funcuri, Centralport.register, req)
				.header(header);
		AnsonResp resp = client.commit(q, errCtx);
		
		return (RegistResp) resp;
	}
}
