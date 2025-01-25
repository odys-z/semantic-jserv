package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt._0;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.LangExt.mustnonull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
import io.odysz.semantic.syn.DBSynTransBuilder;
import io.odysz.semantic.syn.Synode;
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
	 *  install && !root: install
	 * !install &&  root: boot
	 *  
	 * !install && !root: error
	 *  install &&  root: boot
	 * </pre>
	 * @param config_xml
	 * @return
	 * @throws Exception
	 */
	public AppSettings setupdb(String config_xml) throws Exception {
		if (!isblank(installkey) && isblank(rootkey)) {
			String $vol_home = "$" + vol_name;

			mustnonull(jservs);

			YellowPages.load(FilenameUtils.concat(
					new File(".").getAbsolutePath(),
					webinf,
					EnvPath.replaceEnv($vol_home)));

			SynodeConfig cfg = YellowPages.synconfig().replaceEnvs();

			AppSettings.setupdb(cfg, webinf, $vol_home, config_xml, installkey, jservs);
			rootkey = installkey;
			installkey = null;
		}
		else if (isblank(installkey)) {
			mustnonull(rootkey, "[AppSettings] Rootkey cannot be null if installing key is empty.");
			// else go to booting
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
	public static void setupdb(SynodeConfig cfg, String webinf, String envolume, String config_xml,
			String rootkey, String jservs) throws Exception {
		
		Syngleton.setupSysRecords(cfg, YellowPages.robots());

		Syntities regists = Syntities.load(webinf, f("%s/syntity.json", envolume), 
			(synreg) -> {
				throw new SemanticException("Configure meta as class name in syntity.json %s", synreg.table);
			});
		
		Syngleton.setupSyntables(cfg, regists.metas.values(),
				webinf, config_xml, ".", rootkey, true);

		DBSynTransBuilder.synSemantics(new DATranscxt(cfg.synconn), cfg.synconn, cfg.synode(), regists);

		if (!isNull(jservs))
			setupJservs(cfg, jservs);
	}
	
	public static void rebootdb(SynodeConfig cfg, String webinf, String envolume, String config_xml,
			String rootkey) throws Exception {
		
		Syngleton.setupSysRecords(cfg, YellowPages.robots());

		Syntities regists = Syntities.load(webinf, f("%s/syntity.json", envolume), 
			(synreg) -> {
				throw new SemanticException("Configure meta as class name in syntity.json %s", synreg.table);
			});
		
		Syngleton.bootSyntables(cfg, webinf, config_xml, ".", rootkey);

		DBSynTransBuilder.synSemantics(new DATranscxt(cfg.synconn), cfg.synconn, cfg.synode(), regists);
	}

	private static void setupJservs(SynodeConfig cfg, String jservss) throws TransException {
		String[] jservs = jservss.split(" ");
		for (String jserv : jservs) {
			String[] sid_url = jserv.split(":");
			if (isNull(sid_url) || len(sid_url) < 2)
				throw new IllegalArgumentException("jserv: " + jserv);
			
			String url = jserv.replaceFirst(sid_url[0] + ":", "");
			Utils.logi("[%s] Setting peer %s's jserv: %s", cfg.synode(), sid_url[0], url);
			cfg.jserv(sid_url[0], url);
		}
		
		updatePeerJservs(cfg, new SynodeMeta(cfg.synconn));
	}

	public static void updatePeerJservs(SynodeConfig cfg, SynodeMeta synm)
			throws SemanticException {

		IUser robot = DATranscxt.dummyUser();

		try {
			DATranscxt tb = new DATranscxt(cfg.synconn);
			for (Synode sn : cfg.peers())
				tb.update(synm.tbl, robot)
					.nv(synm.jserv, sn.jserv)
					.whereEq(synm.pk, sn.synid)
					.whereEq(synm.domain, cfg.domain)
					.u(tb.instancontxt(cfg.synconn, robot));
		} catch (Exception e) {
			e.printStackTrace();
			throw new SemanticException("Failed to setup peers jserv:\n" + e.getMessage());
		}
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
		Utils.logi("[AppSettings] Loading settings from %s", abs_json);

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
			Utils.logi("%s\t: %s", vol_name, volume);
		
		if (envars != null)
		for (String v : envars.keySet()) {
			System.setProperty(v, envars.get(v));
			if (print)
				Utils.logi("%s\t: %s", v, envars.get(v));
		}
		
		return this;
	}

	/**
	 * Find the correct ip and return the suitable one, "0.0.0.0" as the last one.
	 * @return ip to be bound
	 */
	public String bindip() {
		Utils.warn("Find the correct ip and return the suitable one, '0.0.0.0' as the last one.");
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
	 * @param webinf
	 * @param settings_json
	 * @return AppSettings
	 * @throws Exception 
	 */
	public static AppSettings checkInstall(String webinf, String config_xml, String settings_json) throws Exception {
		Configs.init(webinf);
		AppSettings settings = AppSettings
							.load(webinf, settings_json)
							.setEnvs(true);

		Connects.init(webinf);

		if (!isblank(settings.installkey))
			settings.setupdb(config_xml).save();
		
		return settings;
	}
}
