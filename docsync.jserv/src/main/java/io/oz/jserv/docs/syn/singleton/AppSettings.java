package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt._0;
import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.LangExt.shouldnull;
import static io.odysz.common.LangExt.mustnonull;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.logT;
import static io.odysz.common.Utils.warn;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.util.HashMap;

import org.apache.commons.io_odysz.FilenameUtils;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonField;
import io.odysz.anson.x.AnsonException;
import io.odysz.common.Configs;
import io.odysz.common.EnvPath;
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
	 * @return
	 * @throws Exception
	 */
	public AppSettings setupdb(String url_path, String config_xml) throws Exception {
		if (isblank(rootkey)) {
			mustnonull(installkey, "[AppSettings] Install-key cannot be null if root-key is empty.");

			String $vol_home = "$" + vol_name;

			mustnonull(jservs);

			YellowPages.load(FilenameUtils.concat(
					new File(".").getAbsolutePath(),
					webinf,
					EnvPath.replaceEnv($vol_home)));

			SynodeConfig cfg = YellowPages.synconfig().replaceEnvs();

			Syngleton.defltScxt = new DATranscxt(cfg.sysconn);
			AppSettings.setupdb(cfg, url_path, webinf, $vol_home, config_xml, installkey, this);

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
	 * @param webinf config root, path of cocnfig.xml, e. g. WEB-INF
	 * @param envolume volume home variable, e. g. $VOLUME_HOME
	 * @param config_xml config file name, e. g. config.xml
	 * @param rootkey 
	 * @param jserv for peers, e. g. "peer:http://127.0.0.1:8964". If this is provided,
	 * will be written through into table SynodeMeta.tbl. 
	 * @throws Exception
	 */
	public static void setupdb(SynodeConfig cfg, String url_path, String webinf, String envolume, String config_xml,
			String rootkey, AppSettings settings) throws Exception {
		
		// Syngleton.defltScxt = new DATranscxt(cfg.sysconn);
		Syngleton.setupSysRecords(cfg, YellowPages.robots());

		Syntities regists = Syntities.load(webinf, f("%s/syntity.json", envolume), 
			(synreg) -> {
				throw new SemanticException("Configure meta as class name in syntity.json %s", synreg.table);
			});
		
		if (!isblank(regists.conn()) && !eq(cfg.synconn, regists.conn()))
			throw new SemanticException("Synode configuration's syn-conn dosen't match regists' conn id (which can be null), %s != %s.");
		
		Syngleton.setupSyntables(cfg, regists.metas.values(),
				webinf, config_xml, ".", rootkey, true);

		DBSynTransBuilder.synSemantics(new DATranscxt(cfg.synconn), cfg.synconn, cfg.synode(), regists);

		if (!isNull(settings.jservs))
			setupJserv(cfg, settings, url_path);
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
		
		// Syngleton.setupSysRecords(cfg, YellowPages.robots());

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
	public static String setupJserv(SynodeConfig cfg, AppSettings settings, String jserv_album) throws TransException, SQLException {
		String[] jservs = settings.jservs.split(" ");
		SynodeMeta synm = new SynodeMeta(cfg.synconn);

		String localserv = null;
		for (String jserv : jservs) {
			String[] sid_url = jserv.split(":");
			if (isNull(sid_url) || len(sid_url) < 2)
				throw new IllegalArgumentException("jserv: " + jserv);
			
			String url = jserv.replaceFirst(sid_url[0] + ":", "");
			// cfg.jserv(sid_url[0], url);
			
			if (eq(cfg.synode(), sid_url[0])) {
				logT(new Object() {}, "Ignoring updating jserv to local node: %s");
				localserv = updateLocalJserv(sid_url,
						settings.port, jserv_album, cfg.synconn, synm, cfg.synode());
			}
			else
				updatePeerJservs(cfg.synconn, cfg.domain, synm, sid_url[0], url);
		}
		return localserv;
		
		// updatePeerJservs(cfg, new SynodeMeta(cfg.synconn));
	}


	/**
	 * @param settings e. g. "X:http://ip:port/jserv-album".split()
	 * @param synconn
	 * @param synm
	 * @param mysid
	 * @return 
	 * @throws TransException
	 * @throws SQLException
	 */
	private static String updateLocalJserv(String[] settings, int setting_port, String jserv_album,
			String synconn, SynodeMeta synm, String mysid) throws TransException, SQLException {
		String ip = null;
		try { ip = getLocalIp();
		} catch (IOException e) {
			e.printStackTrace();
			ip = settings[2].replaceAll("^//", "");
		}

		if (len(settings) <= 4 || !eq(settings[2], f("//%s", ip)))
			Utils.warn(
				"Local Ip is not the same as configured Ip, replacing %s with %s.",
				settings[2], ip);
		
		IUser robot = DATranscxt.dummyUser();
		try {
			String servurl = f("%s://%s:%s%s", settings[1],
					ip, setting_port == 0 ? 80 : setting_port, 
					isblank(jserv_album) ? "" :
					jserv_album.startsWith("/") ? jserv_album : "/" + jserv_album);

			DATranscxt tb = new DATranscxt(synconn);
			tb.update(synm.tbl, robot)
			.nv(synm.jserv, servurl)
			.whereEq(synm.pk, mysid)
			// .whereEq(synm.domain, domain)
			.u(tb.instancontxt(synconn, robot));
	
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
		  socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
		  return socket.getLocalAddress().getHostAddress();
		}
	}

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
	public String bindip;
	public String jservs;
	public String installkey;
	public String rootkey;

	public int port;
	public String port() { return String.valueOf(port); }

	private HashMap<String, String> envars;

	/**
	 * Json file path.
	 */
	@AnsonField(ignoreTo=true, ignoreFrom=true)
	public String json; 

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
			toBlock(inf);
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
	 * Find the correct ip and return the suitable one, "0.0.0.0" as the last one.
	 * @return ip to be bound
	 */
	public String bindip() {
		warn("Find the correct ip and return the suitable one, '0.0.0.0' as the last one.");
		return bindip;
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
	 * @return AppSettings
	 * @throws Exception 
	 */
	public static AppSettings checkInstall(String url_path, String webinf, String config_xml, String settings_json) throws Exception {
		logi("[INSTALL-CHECK] checking ...");
		Configs.init(webinf);

		logi("[INSTALL-CHECK] load %s ...", settings_json);
		AppSettings settings = AppSettings
							.load(webinf, settings_json)
							.setEnvs(true);

		logi("[INSTALL-CHECK] load connects with %s/* ...", webinf);
		Connects.init(webinf);

		if (!isblank(settings.installkey)) {
			logi("[INSTALL-CHECK] install: Calling setupdb() with configurations in %s ...", config_xml);
			settings.setupdb(url_path, config_xml).save();
		}
		else {
//			// inject semantics
//			logi("[INSTALL-CHECK] reboot: Replacing semantics with syn-table metas ...");
//			// settings.rebootdb(cfg, webinf, webinf, config_xml, settings_json);
//			String $vol_home = "$" + settings.vol_name;
//			YellowPages.load(FilenameUtils.concat(
//					new File(".").getAbsolutePath(),
//					webinf,
//					EnvPath.replaceEnv($vol_home)));
//
//			Syntities regists = Syntities.load(webinf, f("%s/syntity.json", settings.volume), 
//					(synreg) -> {
//						throw new SemanticException("Configure meta as class name in syntity.json %s", synreg.table);
//					});
//			
//			SynodeConfig cfg = YellowPages.synconfig().replaceEnvs();
//			DBSynTransBuilder.synSemantics(new DATranscxt(cfg.synconn), cfg.synconn, cfg.synode(), regists);
//
//			if (!isNull(settings.jservs))
//				setupJserv(cfg, settings, url_path);
		}
		
		return settings;
	}
}
