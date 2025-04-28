package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt._0;
import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.LangExt.shouldnull;
import static io.odysz.common.LangExt.mustnonull;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.logT;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.HashMap;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonField;
import io.odysz.anson.JsonOpt;
import io.odysz.anson.x.AnsonException;
import io.odysz.common.Configs;
import io.odysz.common.EnvPath;
import io.odysz.common.FilenameUtils;
import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.meta.SyntityMeta;
import io.odysz.semantic.syn.DBSynTransBuilder;
import io.odysz.semantic.syn.registry.Syntities;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.meta.DocOrgMeta;
import io.oz.syn.SynodeConfig;
import io.oz.syn.YellowPages;

/**
 * @since 0.7.0
 */
public class AppSettings extends Anson {

	/** 
	 * Install configurations.
	 * <pre> {
	 *   type: "io.oz.jserv.docs.syn.singleton.AppSettings",
	 *   vol_name : e. g. "VOLUME_HOME",
	 *   volume   : e. g. "c:/album",
	 *   bindip   : e. g. "127.0.0.1",
	 *   webroots : e. g. "X:web-x-ip:port/jserv-album Y:web-y-ip:port/jserv-album"
	 * }</pre>
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
			throw new SemanticException("Synode configuration's syn-conn dosen't match regists' conn id (which can be null), %s != %s.");
		
		Syngleton.setupSyntables(cfg, regists.metas.values(),
				webinf, config_xml, ".", rootkey, forceTest);

		DBSynTransBuilder.synSemantics(new DATranscxt(cfg.synconn), cfg.synconn, cfg.synode(), regists);

		setupJserv(cfg, url_path);
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
	 * @param cfg
	 * @param jservss e. g. "X:https://host-ip:port/jserv-album Y:https://..."
	 * @return 
	 * @throws TransException
	 * @throws SQLException 
	 */
	public AppSettings setupJserv(SynodeConfig cfg, String jserv_album) throws TransException, SQLException {

		if (len(this.jservs) == 0)
			throw new SemanticException("Design 0.2.3: AppSettings.jservs, from settings.json, cannot be empty.");

		SynodeMeta synm = new SynodeMeta(cfg.synconn);

		for (String peer : jservs.keySet()) {
			if (eq(cfg.synode(), peer)) 
				logT(new Object() {}, "Ignoring updating jserv to local node: %s", peer);
			else
				updatePeerJservs(cfg.synconn, cfg.domain, synm, peer, jservs.get(peer));
		}
		return this;
	}

	/**
	 * 
	 * @param https
	 * @param jserv_album jserv's url path
	 * @param synconn can be null, for ignoring db update
	 * @param synm can be null, for ignoring db update
	 * @param mysid
	 * @return
	 * @throws TransException
	 * @throws SQLException
	 */
	private String updateLocalJserv(boolean https, String jserv_album,
			String synconn, SynodeMeta synm, String mysid) throws TransException, SQLException {
		String ip = null;
		try { ip = getLocalIp();
		} catch (IOException e) {
			e.printStackTrace();
			// TODO FIXME 
			// Setup as an offline synode 
			return null;
		}

		IUser robot = DATranscxt.dummyUser();
		try {
			String servurl = f("http%s://%s:%s%s",
					https ? "s" : "", ip, port == 0 ? 80 : port, 
					isblank(jserv_album) ? "" :
					jserv_album.startsWith("/") ? jserv_album : "/" + jserv_album);

//			if (!isblank(synconn) && synm != null) {
			DATranscxt tb = new DATranscxt(synconn);
			tb.update(synm.tbl, robot)
			  .nv(synm.jserv, servurl)
			  .whereEq(synm.pk, mysid)
			  .u(tb.instancontxt(synconn, robot));
//			}
			
			this.jservs.put(mysid, servurl);

			return servurl;
		} catch (Exception e) {
			e.printStackTrace();
			throw new TransException(e.getMessage());
		}
		
	}
	
	/**
	 * Thanks to https://stackoverflow.com/a/38342964/7362888
	 * @return local ip
	 * @throws SocketException 
	 * @throws UnknownHostException 
	 */
	public static String getLocalIp() throws IOException {
	    try(final DatagramSocket socket = new DatagramSocket()) {
	    	boolean succeed = false;
	    	int tried = 0;
	    	while (!succeed && tried++ < 12)
	    		try {
	    			socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
	    			succeed = true;
	    		} catch (UncheckedIOException  e) {
	    			// starting service at network interface not ready yet
	    			Utils.warn("Network interface is not ready yet? Try again ...");
	    			try {
						Thread.sleep(3000);
					} catch (InterruptedException e1) { }
	    		}
		  return socket.getLocalAddress().getHostAddress();
		}
	}

	/**
	 * Persist jsev url into table syn_synode.
	 * 
	 * @param synconn
	 * @param domain
	 * @param synm
	 * @param peer
	 * @param servurl
	 * @throws TransException
	 * @throws SQLException
	 */
	private static void updatePeerJservs(String synconn, String domain, SynodeMeta synm,
			String peer, String servurl) throws TransException, SQLException {
		logi("[%s] Setting peer %s's jserv: %s", domain, peer, servurl);

		IUser robot = DATranscxt.dummyUser();
		DATranscxt tb;
		try {
			tb = new DATranscxt(synconn);
		} catch (Exception e) {
			e.printStackTrace();
			throw new TransException(e.getMessage());
		}
		tb.update(synm.tbl, robot)
			.nv(synm.jserv, servurl)
			.whereEq(synm.pk, peer)
			.whereEq(synm.domain, domain)
			.u(tb.instancontxt(synconn, robot));
	}

	public String vol_name;
	public String volume;
	/** Fault: this should, must, be moved to dictionary.json */
	public HashMap<String, String> jservs;
	public String installkey;
	public String rootkey;

	public int port;
	public String port() { return String.valueOf(port); }

	public HashMap<String, String> envars;

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
	public AppSettings save() throws AnsonException, IOException {
		try (FileOutputStream inf = new FileOutputStream(new File(json))) {
			toBlock(inf, JsonOpt.beautify());
		} 
		return this;
	}
	
	public AppSettings() {
		installkey = "0123456789ABCDEF";
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
	 * 4. setup db, create tables it's sqlite drivers. <br>
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
	public static AppSettings checkInstall(String url_path, String webinf, String config_xml, String settings_json, boolean forceTest) throws Exception {
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
		YellowPages.load(FilenameUtils.concat(
				new File(".").getAbsolutePath(),
				webinf,
				EnvPath.replaceEnv($vol_home)));

		SynodeConfig cfg = YellowPages.synconfig().replaceEnvs();
		
		/*
		String jserv;
		if (!isblank(settings.installkey)) {
			logi("[INSTALL-CHECK] install: Calling setupdb() with configurations in %s ...", config_xml);
			settings.setupdb(url_path, config_xml, cfg, forceTest).save();
			
			// also update jserv
			jserv = settings.updateLocalJserv(cfg.https, url_path, cfg.synconn, new SynodeMeta(cfg.synconn), cfg.synode());
		}
		else {
			logi("[INSTALL-CHECK] Starting application without db setting ...", config_xml);
			// also update jserv
			// jserv = settings.updateLocalJserv(cfg.https, url_path, null, null, null) ;
			jserv = settings.updateLocalJserv(cfg.https, url_path, cfg.synconn, new SynodeMeta(cfg.synconn), cfg.synode());
		}
		*/
		if (!isblank(settings.installkey)) {
			logi("[INSTALL-CHECK] install: Calling setupdb() with configurations in %s ...", config_xml);
			settings.setupdb(url_path, config_xml, cfg, forceTest).save();
		}
		else 
			logi("[INSTALL-CHECK] Starting application without db setting ...", config_xml);
		// String jserv =
		settings.updateLocalJserv(cfg.https, url_path, cfg.synconn, new SynodeMeta(cfg.synconn), cfg.synode());
		
		return settings; // settings.local_serv
	}

	
	/**
	 * Must called after DA layer initiation is finished.
	 * @throws Exception 
	 */
	public static void updateOrgConfig(SynodeConfig cfg, AppSettings settings) throws Exception {
		DocOrgMeta orgMeta = new DocOrgMeta(cfg.sysconn);
		IUser rob = DATranscxt.dummyUser();
		DATranscxt st = new DATranscxt(cfg.sysconn);
		st.update(orgMeta.tbl, rob)
			.nv(orgMeta.webroot, EnvPath.replaceEnv(cfg.org.webroot))
			.whereEq(orgMeta.pk, cfg.org.orgId)
			.u(st.instancontxt(cfg.sysconn, rob));
	}

	public String jserv(String nid) {
		return jservs.get(nid);
	}

}
