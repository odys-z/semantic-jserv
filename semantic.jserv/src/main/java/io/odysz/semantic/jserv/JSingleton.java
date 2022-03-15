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
	private static String webINF;

	public void onDestroyed(ServletContextEvent arg0) {
		AnSession.stopScheduled(5);
		Connects.close();
	}

	public void onInitialized(ServletContextEvent evt) throws SemanticException, SAXException, IOException, SQLException {
		Utils.printCaller(false);
		Utils.logi("JSingleton initializing...");

		ServletContext ctx = evt.getServletContext();
		webINF = ctx.getRealPath("/WEB-INF");
		String root = ctx.getRealPath(".");
		initJserv(root, webINF, ctx.getInitParameter("io.oz.root-key"));
		
		/*
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
		*/
	}
	
	/**For initializing from Jetty - it's not able to find root path?
	 * @param root
	 * @param rootINF
	 * @param rootKey, e.g. context.xml/parameter=root-key
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws SQLException 
	 * @throws SemanticException 
	 */
	public static void initJserv(String root, String rootINF, String rootKey)
			throws SAXException, IOException, SemanticException, SQLException {

		webINF = rootINF;
		Connects.init(rootINF);
		Configs.init(rootINF);
		DATranscxt.configRoot(rootINF, root);
		DATranscxt.key("user-pswd", rootKey);
		
		DatasetCfg.init(rootINF);
		
		for (String connId : Connects.getAllConnIds())
			// Don't remove this until Docker deployment is verified:
			// DATranscxt.loadSemantics(connId, JSingleton.getFileInfPath(Connects.getSmtcsPath(connId)));
			DATranscxt.loadSemantics(connId, Connects.getSmtcsPath(connId));

		defltScxt = new DATranscxt(Connects.defltConn());
			
		Utils.logi("Initializing session with default jdbc connection %s ...", Connects.defltConn());

		AnSession.init(defltScxt);
	}

	public static ISessionVerifier getSessionVerifier() {
		if (ssVerier == null)
			ssVerier = new AnSession();
		return ssVerier;
	}

	/**Get server root/WEB-INF path (filesystem local)
	 * @return WEB-INF root path
	 */
	public static String rootINF() { return webINF; }

	/**Get WEB-INF file path
	 * @param filename
	 * @return rootINF() + filename
	 */
	public static String getFileInfPath(String filename) {
		return FilenameUtils.concat(rootINF(), filename);
	}
}
