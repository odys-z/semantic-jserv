package io.odysz.semantic.jserv;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import org.apache.commons.io_odysz.FilenameUtils;
import org.xml.sax.SAXException;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.ISessionVerifier;
import io.odysz.semantics.x.SemanticException;

/**This jserv lib  initializing and managing module. Subclass must be a web listener.
 * @author odys-z@github.com
 */
public class JSingleton {

	public static DATranscxt defltScxt;
	private static ISessionVerifier ssVerier;
	private static String rootINF;

	public void onDestroyed(ServletContextEvent arg0) {
		AnSession.stopScheduled(5);
		Connects.close();
	}

	public void onInitialized(ServletContextEvent evt) {
		Utils.printCaller(false);
		Utils.logi("JSingleton initializing...");

		ServletContext ctx = evt.getServletContext();
		rootINF = ctx.getRealPath("/WEB-INF");
		String root = ctx.getRealPath(".");
		Connects.init(rootINF);
		Configs.init(rootINF);
		DATranscxt.configRoot(rootINF, root);
		DATranscxt.key("user-pswd", ctx.getInitParameter("io.oz.root-key"));
		
		try {
			DatasetCfg.init(rootINF);
			
			for (String connId : Connects.getAllConnIds())
				DATranscxt.loadSemantics(connId, JSingleton.getFileInfPath(Connects.getSmtcsPath(connId)));

			defltScxt = new DATranscxt(Connects.defltConn());
				
			Utils.logi("Initializing session with default jdbc connection %s ...", Connects.defltConn());
			AnSession.init(defltScxt, ctx);

		} catch (SAXException | IOException | SemanticException | SQLException e) {
			e.printStackTrace();
		}
	}

	public static ISessionVerifier getSessionVerifier() {
		if (ssVerier == null)
			ssVerier = new AnSession();
		return ssVerier;
	}

//	public static ISessionVerifier getSessionVerifierV11() {
//		return ssVerierV11;
//	}

	/**Get server root/WEB-INF path (filesystem local)
	 * @return WEB-INF root path
	 */
	public static String rootINF() { return rootINF; }

	/**Get WEB-INF file path
	 * @param filename
	 * @return rootINF() + filename
	 */
	public static String getFileInfPath(String filename) {
		return FilenameUtils.concat(rootINF(), filename);
	}
}
