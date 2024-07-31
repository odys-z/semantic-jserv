package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.len;

import java.io.IOException;
import java.sql.SQLException;

import org.xml.sax.SAXException;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.syn.DBSyntableBuilder;
import io.odysz.semantics.x.SemanticException;

public class Syngleton extends JSingleton {

	/**
	 * @param cfgxml name of config.xml, to be optimized
	 * @param runtimeRoot
	 * @param configFolder, folder of connects.xml, config.xml and semnatics.xml
	 * @param rootKey, e.g. context.xml/parameter=root-key
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws SQLException 
	 * @throws SemanticException 
	 */
	public static void initSynodetier(String cfgxml, String runtimeRoot, String configFolder, String rootKey)
			throws SAXException, IOException, SemanticException, SQLException {
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
		for (String conn : Connects.getAllConnIds())
			// DATranscxt.loadSemantics(connId);
			DATranscxt.initConfigs(conn, DATranscxt.loadSemantics(conn),
				(c) -> new DBSyntableBuilder.SynmanticsMap(synode, c));
			
		defltScxt = new DATranscxt(Connects.defltConn());
			
		Utils.logi("Initializing session with default jdbc connection %s ...", Connects.defltConn());

		AnSession.init(defltScxt);
	}
}
