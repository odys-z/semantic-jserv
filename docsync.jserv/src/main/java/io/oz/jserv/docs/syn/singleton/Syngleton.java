package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt.len;

import org.apache.commons.io_odysz.FilenameUtils;

import io.odysz.common.Configs;
import io.odysz.common.EnvPath;
import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.syn.DBSyntableBuilder;
import io.oz.synode.jclient.YellowPages;

public class Syngleton extends JSingleton {

	/**
	 * Load configurations, setup connections and semantics, setup session module.
	 * 
	 * @param cfgxml name of config.xml, to be optimized
	 * @param conn0 default connection accept updatings from doclients
	 * @param runtimeRoot
	 * @param configFolder, folder of connects.xml, config.xml and semnatics.xml
	 * @param rootKey, e.g. context.xml/parameter=root-key
	 * @return synode id (configured in @{code cfgxml})
	 * @throws Exception 
	 */
	public static String initSynodetier(String cfgxml, String conn0, String runtimeRoot,
			String configFolder, String rootKey) throws Exception {

		Utils.logi("Initializing synode with configuration file %s\n"
				+ "runtime root: %s\n"
				+ "configure folder: %s\n"
				+ "root-key length: %s",
				cfgxml, runtimeRoot, configFolder, len(rootKey));

		Configs.init(configFolder, cfgxml);
		Connects.init(configFolder);

		DATranscxt.configRoot(configFolder, runtimeRoot);
		DATranscxt.key("user-pswd", rootKey);
		
		DatasetCfg.init(configFolder);
		String synode = Configs.getCfg(Configs.keys.synode);

		DATranscxt.initConfigs(conn0, DATranscxt.loadSemantics(conn0),
			(c) -> new DBSyntableBuilder.SynmanticsMap(synode, c));
			
		defltScxt = new DATranscxt(conn0);
			
		Utils.logi("Initializing session with default jdbc connection %s ...", Connects.defltConn());

		AnSession.init(defltScxt);
		
		YellowPages.load(FilenameUtils.concat(configFolder, EnvPath.replaceEnv("$VOLUME_HOME")));
		
		return synode;
	}
}
