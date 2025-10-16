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
import static io.odysz.common.Utils.warn;
import static io.odysz.common.Utils.warnT;
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
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jprotocol.JProtocol.OnError;
import io.odysz.semantic.jprotocol.JServUrl;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.meta.SyntityMeta;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
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
	 * Load {@link JServUrl} of id {@link #synode} from {@link #synconn},
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
	
	/**
	 * @deprecated one-step
	 * @param myc
	 * @param nm
	 * @return
	 * @throws TransException
	 * @throws SQLException
	public boolean mergeJsonDB(SynodeConfig myc, SynodeMeta nm) throws TransException, SQLException {
		boolean dirty = mergeMyJserv(myc, nm);

		for (String synid : jservs.keySet()) {
			if (!eq(synid, myc.synid))
				dirty |= updateLaterDBserv(
						myc.synconn, myc.org.orgId, myc.domain, nm,
						synid, jserv(synid), jserv_utc,
						// the Synoder is me, usually reaches here when the user changed HUB at a peer.
						myc.synid);
		}
		dirty |= loadDBservs(myc, nm, jserv_utc);
		return dirty;
	}
	 */

	/**
	 * Merge settings.jserv with [synconn] syn_node.jserv(synid=myid),
	 * by resolving conflicts between this.jserv_utc and syn_node.optime.
	 * 
	 * Still needs to save settings.json.
	 * 
	 * @return dirty 
	 * @throws TransException
	 * @throws SQLException
	 * @deprecated one-step
	private boolean mergeMyJserv(SynodeConfig mycfg, SynodeMeta synm)
			throws TransException, SQLException {

		String myid = mycfg.synode();

		if (DAHelper.count(Syngleton.defltScxt, mycfg.synconn, synm.tbl, synm.domain,
				mycfg.domain, synm.org, mycfg.org.orgId, synm.synoder, mycfg.synid) <= 0)
			throw new TransException("Cannot find synode [%s : %s]. Configuration modules works?",
									mycfg.domain, mycfg.synid);
		
		// if (isblank(jserv(myid)))
		jserv(myid, new JServUrl(mycfg.https, reversedIp(), reversedPort(mycfg.https)).jserv());

		return updateLaterDBserv(mycfg.synconn, mycfg.org.orgId, mycfg.domain, synm,
								myid, jserv(myid), jserv_utc);
	}
	 */

	/**
	 * Persist then load to from db.
	 * @return this
	 * @throws TransException
	 * @throws SQLException
	 * @deprecated
	public boolean persistLoadDBserv(SynodeConfig cfg, SynodeMeta synm, String node,
			String jserv, String jserv_utc) throws TransException, SQLException {
		boolean dirty = updateLaterDBserv(cfg.synconn, cfg.org.orgId, cfg.domain,
										 synm, node, jserv, jserv_utc, cfg.synode());
		loadDBservs(cfg, synm, jour0);
		return dirty;
	}
	 */

	/**
	 * @deprecated
	 * @param cfg
	 * @param synm
	 * @param jservs_time
	 * @return
	 * @throws TransException
	 * @throws SQLException
	public AppSettings persistDBservs(SynodeConfig cfg, SynodeMeta synm,
			HashMap<String, String[]> jservs_time) throws TransException, SQLException {
		if (jservs_time != null) {
			for (String n : jservs_time.keySet()) 
				updateLaterDBserv(cfg.synconn, cfg.org.orgId, cfg.domain, synm, n,
								jservs_time.get(n)[0], jservs_time.get(n)[1], cfg.synode());
			
			loadDBservs(cfg, synm, jour0);
		}
		return this;
	}
	 */

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
	
	/**
	 * #deprected replaced by {@link #reversedIp()} and {@link #changedIp()} 
	 * @return IP in effects
	 */
	public String reversedIp() {
		return this.reverseProxy ? proxyIp :
			isblank(localIp) ? JServUrl.getLocalIp(2) : localIp;
	}
	
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
	SessionClient registryClient;
	
	/**
	 * <pre>
     * --------------------------------+----------------------+-----------------------------------------
     *                           X whorker 0              X whorker 1
     * --------------------------------+----------------------+-----------------------------------------
     * (1)      settings.json          |     X:docsync        |      peers
     * AppSettings[jservs, jserv_utc]  -> syn_node.jserv, utc |
     *   [jservs.key != X]             |                      |
     * --------------------------------+----------------------+-----------------------------------------
     * (2)                             |     X:docsync         
     *                                 |  syn_node[X].jserv  <-  changeIp() (optional?)
     * --------------------------------+----------------------+-----------------------------------------
     * (3)                             |     X:docsync        |      peer Y
     *                                 |  syn_node[X].jserv   -> synode[X].jserv [syn_submit()]
     *                                 |  syn_node[Y].jserv  <-> synode[Y].jserv [onSubmitReply()]
     *                                 | * only persist working version
     *                                 | * in case X cannot visite Hub
     * --------------------------------+----------------------+-----------------------------------------
     * (4)                                   X:docsync        |
     *                     changeIp() -> syn_node[X].jserv    |
     * --------------------------------+----------------------+-----------------------------------------
     * (5)        central              |     X:docsync        |
     *     cynodes[X].jserv, utc      <-  syn_node[X].jserv   |
     * --------------------------------+----------------------+-----------------------------------------
     * (6)        central              |     X:docsync        |
     *      cynodes[Z].jserv,utc       -> syn_node[Z].jserv   |
     *      cynodes[Y].jserv,utc       x> syn_node[Y].jserv   |
     * * requires verifying since jservs at central may or may not be working (not true as its utc is staled?)
     * </pre>
     * @param c
	 * @param s
	 * @param synm
	 * @param synusr 
	 * @param err 
	 * @return jservs have been changed, need exposing
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException 
	 * @throws AnsonException 
	 * @throws SsException 
	 */
	public boolean merge_ip_json2db(SynodeConfig c, SynodeMeta synm, SyncUser synusr, OnError err)
			throws TransException, SQLException, AnsonException, SsException {

		String nextIp = JServUrl.getLocalIp(2);
		boolean toSubmit = false;

		if (isblank(localIp) ||
			!reverseProxy && !eq(localIp, nextIp)) {
			// (1.1) local == null, start service
			// (4) on inet ip changed

			// localIp = nextIp;
			jserv_utc = DateFormat.now();

			if (!refresh_myserv(c, synm))
				warnT(new Object(){}, "Why someone thinks he is knowing myself better than me?"); 

			toSubmit = true;
		}
		else
			// (1.2) local != null, jservs_utc > syn_node[my].utc
			// (1.3) local != null, jservs_utc > syn_node[others].utc,
			toSubmit = refresh_myserv(c, synm);
		

		if (isblank(localIp))
			// brutally accept user intervenstion, especially, the hub jserv update.
			updateDB_exceptMe(c.synconn, c.org.orgId, c.domain, synm);

		localIp = nextIp;

		try {
			if (registryClient == null) {
				mustnonull(synusr.pswd());
				String reg_uri = f("/syn/%s", c.synid);

				registryClient = SessionClient.loginWithUri(
						regiserv, reg_uri, synusr.uid(), centralPswd, c.synid);
			}

			RegistResp resp;
			if (toSubmit)
				resp = synotifyCentral(c, registryClient, err);
			else
				// Refersh / merge local jservs with central.
				resp = queryCentral(c, registryClient, err);

			if (mergeReply(c, resp, synm)) {
				loadDBLaterservs(c, synm);
				save();
			}
		} catch (IOException e) {
			warn("Cannot query/submit to central: %s", e.getMessage());
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
		String myjserv = new JServUrl(c.https, reverseIp(), reversedPort(c.https)).jserv();
		if (updateLaterDBserv(c.synconn, c.org.orgId, c.domain, synm,
				c.synid, myjserv, jserv_utc, c.synid)) {
			jserv(c.synid, myjserv);
			return true;
		}
		else return false;
	}

	/**
	 * Tells central about my jserv updating. If this is the case of
	 * localIp == null, that's this node is start to run, both as hub or as peer.
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

		String funcuri = f("/syn/%s", cfg.synid);

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
		RegistResp resp = client.commit(q, errCtx);
		

		if (resp == null || !eq(resp.r, RegistResp.R.ok))
			errCtx.err(MsgCode.ext, "Submit my jserv to central failed: %s", this.regiserv);

		return (RegistResp) resp;
	}

	private RegistResp queryCentral(SynodeConfig cfg, SessionClient client, OnError errCtx) {
		return null;
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
	private boolean mergeReply(SynodeConfig c, RegistResp rep, SynodeMeta synm)
			throws TransException, SQLException {

		if (rep != null && rep.diction != null && rep.diction.peers != null) {
			boolean dirty = false;
			for (Synode peer : rep.diction.peers) {
				dirty |= updateLaterDBserv(c.synconn, c.org.orgId, c.domain, synm,
						peer.synid, peer.jserv, peer.optime, peer.oper);
			}
			return dirty;
		}
		else warnT(new Object() {}, "Merge with empty reply data?");
		return false;
	}

	//////////////////////////////// static helpers ////////////////////////////////
	static boolean updateDB_exceptMe(String synconn, String org, String domain, SynodeMeta synm) {
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
	public static boolean updateLaterDBserv(String synconn, String org, String domain, SynodeMeta synm,
			String peer, String servurl, String timestamp_utc, String... createrid)
			throws TransException, SQLException {
		mustnoBlankAny(synconn, org, domain, peer, servurl, createrid);

		DATranscxt tb = new DATranscxt(synconn);
			
		IUser robot = DATranscxt.dummyUser();

		String timestamp = ifnull(timestamp_utc, jour0);
		logi("[%s : %s] Setting peer %s's jserv: %s [%s]", synconn, domain, peer, servurl, timestamp);

		SemanticObject res = tb.update(synm.tbl, robot)
			.nv(synm.jserv, servurl)
			.nv(synm.jserv_utc, timestamp)
			.nv(synm.oper, _0(createrid, peer))
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
