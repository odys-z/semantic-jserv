package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.len;

import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.syn.DBSynTransBuilder;
import io.odysz.semantic.syn.Synode;
import io.odysz.semantic.syn.registry.Syntities;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.syn.SynodeConfig;
import io.oz.syn.YellowPages;

public class AppSettings {

	/**
	 * 
	 * @param webinf config root, path of cocnfig.xml, e. g. WEB-INF
	 * @param envolume volume home variable, e. g. $VOLUME_HOME
	 * @param config_xml config file name, e. g. config.xml
	 * @throws Exception
	 */
	static void setupdb(String webinf, String envolume, String config_xml) throws Exception {
		YellowPages.load(envolume);

		SynodeConfig cfg = YellowPages.synconfig();
		setupdb(cfg, webinf, envolume, config_xml, "ABCDEF0123465789");
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
			String rootkey, String... jservs) throws Exception {
		
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

	private static void setupJservs(SynodeConfig cfg, String[] jservss) throws TransException {
		String[] jservs = jservss[0].split(" ");
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
}
