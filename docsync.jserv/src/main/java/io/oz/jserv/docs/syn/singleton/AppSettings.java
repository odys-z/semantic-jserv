package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt._0;
import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.LangExt.shouldnull;
import static io.odysz.common.LangExt.mustnonull;
import static io.odysz.common.LangExt.mustnull;
import static io.odysz.common.LangExt.mustnoBlankAny;
import static io.odysz.common.LangExt.ifnull;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.warn;
import static io.odysz.common.Utils.warnT;
import static io.odysz.transact.sql.parts.condition.ExprPart.constr;
import static io.odysz.transact.sql.parts.condition.Funcall.isnull;
import static io.odysz.common.DateFormat.jour0;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Set;

import org.xml.sax.SAXException;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonException;
import io.odysz.anson.AnsonField;
import io.odysz.anson.JsonOpt;
import io.odysz.common.Configs;
import io.odysz.common.DateFormat;
import io.odysz.common.EnvPath;
import io.odysz.common.FilenameUtils;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.jclient.SessionClient;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jprotocol.JProtocol.OnError;
import io.odysz.semantic.jprotocol.JServUrl;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.meta.SynChangeMeta;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.meta.SyntityMeta;
import io.odysz.semantic.util.DAHelper;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Update;
import io.odysz.transact.sql.parts.Logic.op;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.meta.DocOrgMeta;
import io.oz.syn.DBSynTransBuilder;
import io.oz.syn.SyncUser;
import io.oz.syn.Synode;
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

	/**
	 * Client uri for access central sevices,
	 * e.g. f("/regist-sys/%s", c.synid)
	 */
	static String reg_uri = "/regiest-sys";

	@AnsonField(ignoreFrom=true, ignoreTo=true)
	String webinf;
	
	/** <pre>
	 * !root-key &amp;&amp; !install-key: error
	 * !root-key &amp;&amp;  install-key: install
	 * 
	 *  root-key &amp;&amp; !install-key: boot
	 *  root-key &amp;&amp;  install-key: warn and clean install-key, boot
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
				Utils.warn("Jservs Shouldn't be empty, unless this node is setup for joining a domain. synode: %s",
							cfg.synode());

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
	 * 0. setup sys-conn records with {@link Syngleton#setupSysRecords}<br>
	 * 1. setup with {@link Syngleton#setupSyntables(SynodeConfig, SyntityMeta[], String, String, String, String, boolean...)}<br>
	 * 2. load with {@link DBSynTransBuilder#synSemantics(DATranscxt, String, String, Syntities)}<br>
	 * 
	 * @param cfg
	 * @param url_path
	 * @param webinf config root, path of cocnfig.xml, e. g. WEB-INF
	 * @param config_xml config file name, e. g. config.xml
	 * @param rootkey
	 * @throws Exception
	 */
	void setupdb(SynodeConfig cfg, String url_path, String webinf, String config_xml,
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

	private HashMap<String, String> jservs;

	/** UTC time of dirty */
	public String jserv_utc;
	public AppSettings jserv_utc(String utc) {
		jserv_utc = utc;
		return this;
	}


	String installkey;
	public String installkey() { return installkey; }

	String rootkey;
	public String rootkey() { return rootkey; }

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
	String localIp;
	public String localIp() { return this.localIp; }

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

		try (FileInputStream inf = new FileInputStream(new File(abs_json))) {

			AppSettings settings = (AppSettings) Anson.fromJson(inf);
			if (settings.jservs == null)
				settings.jservs = new HashMap<String, String>();
			settings.webinf = web_inf;
			settings.json = abs_json;

			return settings;
		}
	}
	
	/**
	 * Move to Antson?
	 * @return
	 * @throws AnsonException
	 * @throws IOException
	 */
	public AppSettings save_rt() throws IOException {
		// according to Grok
//		try (FileOutputStream inf = new FileOutputStream(new File(json))) {
//			toBlock(inf, JsonOpt.beautify());
//		} catch (IOException e) {
//			e.printStackTrace();
//			throw new AnsonException(e);
//		} 
//		return this;

		mustnonull(rootkey);
		mustnull(installkey);

		/*
		String tempname = f("%s.%d", json, (int)(Math.random() * 1000));
		Utils.logi("=== [%s] writing %s ===", DateFormat.now(), tempname);

		File file = new File(json);
		File temp = new File(tempname);
		try (Writer writer = new BufferedWriter(
				 new OutputStreamWriter(
					 new FileOutputStream(temp), StandardCharsets.UTF_8))) {
			writer.write(toBlock(JsonOpt.beautify()));
			writer.flush();
			Utils.logi("=== settings.json written to temp successfully ===");
		} catch (IOException e) {
			Utils.warn("CRITICAL: Failed to write settings.json.temp! %s", e.getMessage());
			e.printStackTrace();
			throw new AnsonException(e);
		}

		try {
	        Files.copy(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
	        Utils.logi("=== settings.json replaced successfully (copy) ===");
	    } catch (IOException e) {
	        Utils.warn("CRITICAL: Failed to copy %s → %s ! %s", tempname, json, e.getMessage());
	        e.printStackTrace();
	        throw new AnsonException(e);
	    }
		Utils.logi("=== settings.json saved successfully ===");
		*/

		Utils.logi("=== [%s] writing %s ===", DateFormat.now(), json);
		toFile(json, JsonOpt.beautify());

		// TODO Let's flag this section with debug, my field that is configurable.
		String backup = f("%s-fingerprint", json);
		Utils.logi("=== [%s] writing %s ===", DateFormat.now(), backup);
		toFile(backup, JsonOpt.beautify());
		return this;
	}
	
	public AppSettings() {
		// installkey = "0123456789ABCDEF";
		jservs = new HashMap<String, String>();
	}
	
	/**
	 * Install process: <br>
	 * 1. load settings.json<br>
	 * 2. update env-variables<br>
	 * 3. initiate connects<br>
	 * 4. setup db, create tables it's sqlite drivers for the first time,
	 * see {@link AppSettings#setupdb(String, String, SynodeConfig, boolean)}. <br>
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
			settings.setupdb(url_path, config_xml, cfg, forceDrop);
			
			settings.save_rt();
		}
		else 
			logi( "[INSTALL-CHECK]\n!!! SKIP DB SETUP !!!\n"
				+ "Starting application without db setting ...");

		return settings;
	}

	/**
	 * Load {@link JServUrl} of id syncfg.synid from syncfg.synconn,
	 * keeps later version modified by users.
	 * @return true if there are records in db is later than this.jserv_utc.
	 * @throws SQLException
	 * @throws TransException
	 * @throws SAXException
	 */
	public boolean loadDBLaterservs(SynodeConfig syncfg, SynodeMeta synm)
			throws SQLException, TransException {

		DATranscxt synb = new DATranscxt(syncfg.synconn);
		IUser robot = DATranscxt.dummyUser();

		AnResultset rs = (AnResultset) synb
				.select(synm.tbl, "t")
				.whereEq(synm.org, syncfg.org.orgId)
				.whereEq(synm.domain, syncfg.domain)

				// Funcall.sql has a bug for this:
				// .where(op.ge, Funcall.toDate(Funcall.isnull(synm.jserv_utc, Funcall.toDate(DateFormat.jour0))), Funcall.toDate(ifnull(utc, DateFormat.jour0)))
				// -> datetime('ifnull(optime, datetime('1911-10-10'))') >= datetime('1911-10-10')
				// 
				// And "datetime(optime) = datetime('1911-10-10')" will not work.
				//
				// This line only works for sqlite (Google AI: sqlite doesn't has built-in datetime type):
				.where(op.gt, isnull(synm.jserv_utc, constr(jour0)), constr(LangExt.ifnull(jserv_utc, jour0)))
				// -> synid = 'X' AND ifnull(optime, '1911-10-10') > '1911-10-10'
				
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
	
	public Set<String> jservNodes() {
		return jservs == null ? null : jservs.keySet();
	}
	
	/** Find jserv from {@link #jservs}. */
	public String jserv(String nid) {
		return jservs.get(nid);
	}

	public AppSettings jserv(String peer, String jserv) {
		if (jservs == null)
			jservs = new HashMap<String, String>();
		jservs.put(peer, jserv);
		if (!JServUrl.valid(jserv))
			warnT(new Object() {}, "Jserv is invalid: %s", jserv);
		return this;
	}

	public AppSettings jservs(HashMap<String, ?> jservs) {
		this.jservs.clear();
		if (jservs != null)
		for (String n : jservs.keySet()) {
			if (jservs.get(n) instanceof String)
				this.jservs.put(n, (String)jservs.get(n));
			else if (jservs.get(n).getClass().isArray())
				this.jservs.put(n, ((String[])jservs.get(n))[0]);
			else throw new NullPointerException("Jservs type is not supported.");
		}
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
	
	/**
	 * @return IP in effect (no updating it there is one in effect).
	 */
	public String reportRversedIp() {
		return this.reverseProxy ? proxyIp :
			isblank(localIp) ? JServUrl.getLocalIp(2) : localIp;
	}
	
	/**
	 * @deprecated not used?
	 * @return
	 */
	public String reverseIp() {
		return this.reverseProxy ? proxyIp : localIp;
	}
	
	public String changedIp() {
		return JServUrl.getLocalIp(2);
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

	//////////////////////////////// registry worker 0 helpers ////////////////////////////////
	@AnsonField(ignoreTo=true, ignoreFrom=true)
	SessionClient registryClient;
	
	/**
	 * <pre>
There are 2 timestamps,

1. the one when local IP changed, which is propagated to other synode's dbs;

2. the one saved in json, AppSettings.jserv_utc, when users force to change a peer's jserv.

Also be aware that settings.jservs won't load jserv of the current node.
Instead, it is always generated automatically, and is overriden by proxyIp.

Settings.localIp is ignored when loading the json file, making a chance to report at boot,
then persist the timestamp 1 into db, or now() whenever localIp is changed.
A synode only report its own jserv to central.

if settings.jserv_utc is later than some other node's jser, keep the user's intervention.

Note: [0.7.6] Users change settings.json without verifying.

Two Workers Schema
==================

The jservs of other synodes is merged from both other peers and central.
- Worker 0 manage AppSettings.jservs, reaches only central, caring noth about peers;
- worker 1 queries all possible peers and merge into db, caring nothing about central and AppSettings.jservs;
- both workers are monitoring ip changes;
- [0.7.6] settings.json[jservs] takes effect if and only if the synode has rebooted.
    
--------------------------------+----------------------+-----------------------------------------
                          X whorker 0              X whorker 1
--------------------------------+----------------------+-----------------------------------------
(1)      settings.json          |     X:docsync        |      peers
AppSettings[jservs, jserv_utc]  -> syn_node.jserv, utc |
  [jservs.key != X]             |                      |
--------------------------------+----------------------+-----------------------------------------
(2)                             |     X:docsync         
                                |  syn_node[X].jserv  <-  changeIp() (optional?)
--------------------------------+----------------------+-----------------------------------------
(3)                             |     X:docsync        |      peer Y
                                |  syn_node[X].jserv   -> synode[X].jserv [syn_submit()]
                                |  syn_node[Y].jserv  <-> synode[Y].jserv [onSubmitReply()]
                                | * only persist working version
                                | * in case X cannot visite Hub
--------------------------------+----------------------+-----------------------------------------
(4)                                   X:docsync        |
                    changeIp() -> syn_node[X].jserv    |
--------------------------------+----------------------+-----------------------------------------
(5)        central              |     X:docsync        |
    cynodes[X].jserv, utc      <-  syn_node[X].jserv   |
--------------------------------+----------------------+-----------------------------------------
(6)        central              |     X:docsync        |
     cynodes[Z].jserv,utc       -> syn_node[Z].jserv   |
     cynodes[Y].jserv,utc       x> syn_node[Y].jserv   |

Notes on steps
==============

step (1)
--------

Brutally accept user intervention at stat up, if jserv_utc the saving time is later, this reqires:
- any newly update by both workers must be saved to file.
- Amdin is responsible for failed connections (failed connection can be correct)

setp (6)
--------

* won't verify is a jserv avalid or not, since jservs at central may or may not be working,
  but is the most staled if a peer connot connect to internet.
	 * </pre>
     * @param c
	 * @param synm
	 * @param synusr 
	 * @param err 
	 * @return is jservs have been changed and need exposing?
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException 
	 * @throws AnsonException 
	 * @throws SsException 
	 */
	public boolean merge_ip_json2db(SynodeConfig c, SynodeMeta synm, SyncUser synusr, OnError err)
			throws TransException, SQLException, AnsonException, SsException {


		if (isblank(localIp))
			updateDB_exceptMe(c, synm);

		String nextIp = JServUrl.getLocalIp(2);
		boolean toSubmit = false;

		if (isblank(localIp) ||
			!reverseProxy && !eq(localIp, nextIp)) {
			// (1.1) local == null, start service
			// (4) on inet ip changed

			localIp = nextIp;
			jserv_utc = DateFormat.now();

			if (!refresh_myserv(c, synm))
				warnT(new Object(){}, "Why someone thinks he is knowing myself better than me?"); 

			toSubmit = true;
		}
		else
			// (1.2) local != null, jservs_utc > syn_node[my].utc
			// (1.3) local != null, jservs_utc > syn_node[others].utc,
			toSubmit = refresh_myserv(c, synm);
		
		// localIp = nextIp;

		try {
			if (registryClient == null) {
				mustnonull(centralPswd);

				registryClient = SessionClient.loginWithUri(
						regiserv, reg_uri + "/" + c.synid, synusr.uid(), centralPswd, c.synid);
			}

			RegistResp resp;
			if (toSubmit)
				resp = synotifyCentral(c, registryClient, err);
			else
				// Refersh / merge local jservs with central.
				resp = queryDomConfig(c, registryClient, err);

			if (mergeReply_butme(c, resp, synm) || toSubmit) {
				loadDBLaterservs(c, synm);

//				mustnonull(rootkey);
//				mustnull(installkey);
				save_rt();
			}
		} catch (IOException e) {
			warn("Cannot query/submit to central: %s,\n%s", regiserv, e.getMessage());
		}

		return toSubmit;
	}

	/**
	 * Merge this settings.jservs[my] and db syn_node.jserv[my].
	 * @param c
	 * @param synm
	 * @return changed into db (this.jserv_utc is later than syn_node[myid].utc
	 * @throws TransException
	 * @throws SQLException
	 */
	private boolean refresh_myserv(SynodeConfig c, SynodeMeta synm)
			throws TransException, SQLException {
		String myjserv = new JServUrl(c.https,
							reportRversedIp(),
							reversedPort(c.https)).jserv();

		if (inst_updateLaterDBserv(c.synconn, c.org.orgId, c.domain, synm,
				c.synid, c.mode, myjserv, jserv_utc, c.synid)) {

			logi("[%s : %s] update peer %s's jserv: %s [%s]",
				 c.synconn, c.domain, c.synid, myjserv, jserv_utc);

			jserv(c.synid, myjserv);
			return true;
		}
		else return false;
	}

	/**
	 * Tells central about my jserv updating. The returned response's jservs need to be merged.
	 * 
	 * See also synodepy3.InstallerCli.submit_mysettings()
	 * 
	 * @param funcuri
	 * @param cfg
	 * @param client
	 * @param errCtx
	 * @return reply
	 * @throws IOException
	 * @throws SemanticException
	 * @throws AnsonException
	 */
	private RegistResp synotifyCentral(SynodeConfig cfg, SessionClient client,
			OnError errCtx) throws IOException, SemanticException, AnsonException {
		
		mustnoBlankAny(cfg.synid, cfg.org.orgId, cfg.org.orgType, jserv(cfg.synid));

		String funcuri = f("%s/%s", reg_uri, cfg.synid);

		AnsonHeader header = client.header()
				.act(funcuri, "syntifyCentral", RegistReq.A.submitSettings, jserv(cfg.synid));

		RegistReq req = new RegistReq(cfg.org.orgType)
				.dictionary(cfg)
				.myjserv(jserv(cfg.synid), jserv_utc)
				.mystate(cfg.mode == SynodeMode.hub ? CynodeStats.asHub : CynodeStats.asPeer);

		req.a(RegistReq.A.submitSettings);
		req.protocolPath = JProtocol.urlroot;

		RegistResp resp = client.commit(client
					.userReq(funcuri, Centralport.register, req)
					.header(header),
					errCtx);

		if (resp == null || !eq(resp.r, RegistResp.R.ok))
			errCtx.err(MsgCode.ext, "Submit my jserv to central failed: %s", this.regiserv);

		return (RegistResp) resp;
	}

	private RegistResp queryDomConfig(SynodeConfig cfg, SessionClient client, OnError errCtx)
			throws SemanticException, AnsonException, IOException {

		String funcuri = f("%s/%s", reg_uri, cfg.synid);

		AnsonHeader header = client.header()
				.act(funcuri, "syntifyCentral", RegistReq.A.queryDomConfig, jserv(cfg.synid));

		RegistReq req = new RegistReq(cfg.org.orgType)
				.dictionary(cfg);

		req.a(RegistReq.A.queryDomConfig);

		RegistResp resp = client.commit(client
					.userReq(funcuri, Centralport.register, req)
					.header(header),
					errCtx);
		
		if (resp == null || !eq(resp.r, RegistResp.R.ok))
			errCtx.err(MsgCode.ext, "Query domain at central failed: %s", this.regiserv);

		return (RegistResp) resp;
	}

	/**
	 * There is an equivalent, {@link io.oz.jserv.docs.syn.SynDomanager#onexchangeDBservs()}
	 * @param c
	 * @param rep
	 * @param synm
	 * @return
	 * @throws TransException
	 * @throws SQLException
	 */
	private boolean mergeReply_butme(SynodeConfig c, RegistResp rep, SynodeMeta synm)
			throws TransException, SQLException {

		if (rep != null && rep.diction != null && rep.diction.peers != null) {
			boolean dirty = false;
			for (Synode peer : rep.diction.peers) {
				if (!eq(peer.synid, c.synid) && JServUrl.valid(peer.jserv))
					dirty |= inst_updateLaterDBserv(c.synconn, c.org.orgId, c.domain, synm,
							peer.synid, isblank(peer.remarks) ? null : SynodeMode.valueOf(peer.remarks),
							peer.jserv, peer.optime, peer.oper);
			}
			return dirty;
		}
		else warnT(new Object() {}, "Merge with empty reply data?");
		return false;
	}

	//////////////////////////////// static helpers ////////////////////////////////
	/** 
	 * Brutally accept user interventions, especially, the hub's jserv updating.
	 * @param c
	 * @param synm
	 * @return updated some records in db.
	 * @throws TransException
	 * @throws SQLException
	 */
	boolean updateDB_exceptMe(SynodeConfig c, SynodeMeta synm)
			throws TransException, SQLException {

		mustnoBlankAny(c.synconn, c.org, c.domain, c.synid);
		DATranscxt tb = null; // = new DATranscxt(c.synconn);

		Update u = null;
		if (len(jservs) > 0) {
			IUser robot = DATranscxt.dummyUser();
			String timestamp = ifnull(jserv_utc, jour0);

			for (String sid : jservs.keySet()) {
				if (eq(sid, c.synid))
					continue;
				if (tb == null)
					tb = new DATranscxt(c.synconn);
				
				if (DAHelper.count(tb, c.synconn, synm.tbl,
					synm.org, c.org.orgId, synm.domain, c.domain, synm.pk, sid) == 0)
					warnT(new Object() {},
						  "[ERROR, if not the first boot] !!! This error is tolerated for running tests!\n"
						+ "Updating non-existing jserv of %s? Shouldn't reach here!", sid);

				Update pst = tb.update(synm.tbl, robot)
					.nv(synm.jserv, jserv(sid))
					.nv(synm.jserv_utc, timestamp)
					.nv(synm.oper, c.synid)
					.whereEq(synm.pk, sid)
					.whereEq(synm.domain, c.domain)
					.whereEq(synm.org, c.org.orgId)
					.where(op.lt, isnull(synm.jserv_utc, constr(jour0)), constr(timestamp))
					;
	
				if (u == null) u = pst;
				else u.post(pst);
			}
			if (tb != null) {
				SemanticObject res = u.u(tb.instancontxt(c.synconn, robot));
				return res.total() > 0;
			}
			else return false;
		}
		return false;
	}

	/**
	 * Persist jsev url into table syn_synode, if the time stamp is new.
	 * 
	 * @param timestamp_utc their timestamp, must newer than mine to update db
	 * @param createrid where does this version come from, null for same as peer
	 * @return true if the one in database is older and have been replaced, a.k.a. dirty 
	 * @throws TransException
	 * @throws SQLException
	 */
	public static boolean inst_updateLaterDBserv(String synconn, String org, String domain, SynodeMeta synm,
			String peer, SynodeMode synmode, String servurl, String timestamp_utc, String createrid)
			throws TransException, SQLException {
		mustnoBlankAny(synconn, org, domain, peer, servurl, createrid);

		DATranscxt tb = new DATranscxt(synconn);
			
		IUser robot = DATranscxt.dummyUser();

		String timestamp = ifnull(timestamp_utc, jour0);
		// logi("[%s : %s] Setting peer %s's jserv: %s [%s]", synconn, domain, peer, servurl, timestamp);

		if (DAHelper.count(tb, synconn, synm.tbl,
				synm.org, org, synm.domain, domain, synm.pk, peer) == 0) {
			
//			tb.insert(synm.tbl, robot)
//				.nv(synm.org, org)
//				.nv(synm.domain, domain)
//				.nv(synm.pk, peer)
//				.nv(synm.jserv, servurl)
//				.nv(synm.io_oz_synuid, SynChangeMeta.uids(createrid, peer))
//				.nv(synm.jserv_utc, timestamp)
//				.nv(synm.oper, createrid)

			mustnonull(synmode);
			insert_synode(tb, synm, robot, synconn, org, domain, peer, synmode, servurl, timestamp, createrid)
				.ins(tb.instancontxt(synconn, robot));
			
			return true; //res.total() > 0;
		}
		else {
			SemanticObject res = tb.update(synm.tbl, robot)
				.nv(synm.jserv, servurl)
				.nv(synm.jserv_utc, timestamp)
				.nv(synm.oper, createrid)
				.whereEq(synm.pk, peer)
				.whereEq(synm.domain, domain)
				.whereEq(synm.org, org)
				// see comments in loadJserv()
				// .where(op.le, Funcall.isnull(synm.jserv_utc, Funcall.toDate(DateFormat.jour0)), Funcall.toDate(timestamp))
				.where(op.lt, isnull(synm.jserv_utc, constr(jour0)), constr(timestamp))
				.u(tb.instancontxt(synconn, robot));
		
			return res.total() > 0;
		}
	}

	public static Insert insert_synode(DATranscxt tb, SynodeMeta synm, IUser robot,
			String synconn, String org, String domain, String peer,
			SynodeMode synodeMode, String servurl, String timestamp, String createrid) {
		timestamp = ifnull(timestamp, jour0);
		return tb
			.insert(synm.tbl, robot)
			.nv(synm.org, org)
			.nv(synm.domain, domain)
			.nv(synm.pk, peer)
			.nv(synm.jserv, servurl)
			.nv(synm.remarks, synodeMode == null ? null : synodeMode.name())
			.nv(synm.io_oz_synuid, SynChangeMeta.uids(createrid, peer))
			.nv(synm.jserv_utc, timestamp)
			.nv(synm.oper, createrid)
			;
	}
}
