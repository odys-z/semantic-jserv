package io.odysz.semantic.jserv;

import java.io.IOException;

import javax.servlet.ServletContextEvent;

import org.apache.commons.io.FilenameUtils;
import org.xml.sax.SAXException;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.jsession.ISessionVerifier;
import io.odysz.semantic.jsession.SSession;

/**This jserv lib  initializing and managing module. Subclass must be a web listener.
 * See {@link io.odysz.jsample.Sampleton} example of how to use JSingleton in application.
 * @author odys-z@github.com
 */
public class JSingleton {

	public static DATranscxt defltScxt;
	private static ISessionVerifier ssVerier;
	private static String rootINF;

	public void onDestroyed(ServletContextEvent arg0) {
		SSession.stopScheduled(5);
		Connects.close();
	}

	public void onInitialized(ServletContextEvent evt) {
		Utils.printCaller(false);
		Utils.logi("JSingleton initializing...");

		rootINF = evt.getServletContext().getRealPath("/WEB-INF");
		Connects.init(rootINF);
		Configs.init(rootINF);
		
		try {
			DatasetCfg.init(rootINF);
			// HashMap<String, DASemantics> cfgs =  DATranscxt.initConfigs(Connects.defltConn(), rootINF + "/semantics.xml");
			// ISemantext s = new DASemantext(Connects.defltConn(), cfgs, new JRobot());
			defltScxt = new DATranscxt(Connects.defltConn());
			SSession.init(defltScxt, evt.getServletContext());
			ssVerier = new SSession();
		} catch (SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	public static ISessionVerifier getSessionVerifier() {
		return ssVerier;
	}

	/**Get server root/WEB-INF path (filesystem local)
	 * @return
	 */
	public static String rootINF() {
		return rootINF;
	}

	/**Get WEB-INF file path
	 * @param filename
	 * @return rootINF() + filename
	 */
	public static String getFileInfPath(String filename) {
		return FilenameUtils.concat(rootINF(), filename);
	}

}
