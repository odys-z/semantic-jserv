package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt.f;

import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.syn.DBSynTransBuilder;
import io.odysz.semantic.syn.registry.Syntities;
import io.odysz.semantics.x.SemanticException;
import io.oz.syn.SynodeConfig;
import io.oz.syn.YellowPages;

public class AppSettings {

	static void setupdb(String webinf, String envolume, String config_xml) throws Exception {
		YellowPages.load(envolume);

		SynodeConfig cfg = YellowPages.synconfig();
		setupdb(cfg, webinf, envolume, config_xml);
	}

	static void setupdb(SynodeConfig cfg, String webinf, String envolume, String config_xml) throws Exception {
		
		Syngleton.setupSysRecords(cfg, YellowPages.robots());

		Syntities regists = Syntities.load(webinf, f("%s/syntity.json", envolume), 
			(synreg) -> {
				throw new SemanticException("Configure meta as class name in syntity.json %s", synreg.table);
			});
		
		Syngleton.setupSyntables(cfg, regists.metas.values(),
				webinf, config_xml, ".", "ABCDEF0123465789", true);

		DBSynTransBuilder.synSemantics(new DATranscxt(cfg.synconn), cfg.synconn, cfg.synode(), regists);
	}
}
