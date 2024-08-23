package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.len;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.syn.DBSyntableBuilder;

public class Syngleton extends JSingleton {

	public static void initSynodetier(String cfgxml, String runtimeRoot, String configFolder, String rootKey)
			throws Exception {
		initSynodetier(cfgxml, Connects.defltConn(), runtimeRoot, configFolder, rootKey);
	}

	/**
	 * @param cfgxml name of config.xml, to be optimized
	 * @param conn0 default connection accept updatings from doclients
	 * @param runtimeRoot
	 * @param configFolder, folder of connects.xml, config.xml and semnatics.xml
	 * @param rootKey, e.g. context.xml/parameter=root-key
	 * @throws Exception 
	 */
	public static void initSynodetier(String cfgxml, String conn0, String runtimeRoot,
			String configFolder, String rootKey) throws Exception {

		Utils.logi("Initializing synode with configuration file %s\n"
				+ "runtime root: %s\n"
				+ "configure folder: %s\n"
				+ "root-key length: %s",
				cfgxml, runtimeRoot, configFolder, len(rootKey));

		Connects.init(configFolder);
		Configs.init(configFolder, cfgxml);

		DATranscxt.configRoot(configFolder, runtimeRoot);
		DATranscxt.key("user-pswd", rootKey);
		
		DatasetCfg.init(configFolder);
		String synode = Configs.getCfg(Configs.keys.synode);

		DATranscxt.initConfigs(conn0, DATranscxt.loadSemantics(conn0),
			(c) -> new DBSyntableBuilder.SynmanticsMap(synode, c));
			
		defltScxt = new DATranscxt(conn0);
			
		Utils.logi("Initializing session with default jdbc connection %s ...", Connects.defltConn());

		AnSession.init(defltScxt);

	}
}
