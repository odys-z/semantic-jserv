package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt.f;

import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.syn.DBSynTransBuilder;
import io.odysz.semantic.syn.registry.Syntities;
import io.odysz.semantics.x.SemanticException;
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
	 * @throws Exception
	 */
	public static void setupdb(SynodeConfig cfg, String webinf, String envolume, String config_xml, String rootkey) throws Exception {
		
		Syngleton.setupSysRecords(cfg, YellowPages.robots());

		Syntities regists = Syntities.load(webinf, f("%s/syntity.json", envolume), 
			(synreg) -> {
				throw new SemanticException("Configure meta as class name in syntity.json %s", synreg.table);
			});
		
		Syngleton.setupSyntables(cfg, regists.metas.values(),
				webinf, config_xml, ".", rootkey, true);

		DBSynTransBuilder.synSemantics(new DATranscxt(cfg.synconn), cfg.synconn, cfg.synode(), regists);
	}
}
