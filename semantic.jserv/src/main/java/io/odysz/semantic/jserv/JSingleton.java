package io.odysz.semantic.jserv;

import static io.odysz.common.LangExt.bool;
import static io.odysz.common.LangExt.isblank;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import org.apache.commons.io_odysz.FilenameUtils;

import io.odysz.common.Configs;
import io.odysz.common.Configs.keys;
import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.ISessionVerifier;

/**
 * This jserv lib  initializing and managing module. Subclass must be a web listener.
 * 
 * @author odys-z@github.com
 */
public class JSingleton {

	public static DATranscxt defltScxt;
	public static boolean health;

	protected static ISessionVerifier ssVerier;
	
	/** @deprecated 2.0 */
	protected static String webINF;

	public void onDestroyed(ServletContextEvent arg0) {
		AnSession.stopScheduled(5);
		Connects.close();
	}

	/**
	 * @param evt
	 * @return configure's root path, e.g. /WEB-INF
	 * @throws Exception 
	 */
	public String onInitialized(ServletContextEvent evt)
			throws Exception {
		Utils.printCaller(false);
		Utils.logi("JSingleton initializing...");

		ServletContext ctx = evt.getServletContext();
		webINF = ctx.getRealPath("/WEB-INF");
		String root = ctx.getRealPath(".");
		initJserv(root, webINF, ctx.getInitParameter("io.oz.root-key"));
		return webINF;
	}
	
	/**
	 * For initializing from Jetty - it's not able to find root path?
	 * 
	 * @deprecated can't load smtype.synchange for 2.0
	 * 
	 * @param root
	 * @param rootINF, e.g. WEB-INF
	 * @param rootKey, e.g. context.xml/parameter=root-key
	 * @throws Exception 
	 */
	public static void initJserv(String root, String rootINF, String rootKey)
			throws Exception {

		webINF = rootINF;
		Connects.init(rootINF);
		Configs.init(rootINF);

		DATranscxt.configRoot(rootINF, root);
		DATranscxt.key("user-pswd", rootKey);
		
		DatasetCfg.init(rootINF);
		
		for (String connId : Connects.getAllConnIds())
			DATranscxt.loadSemantics(connId);

		defltScxt = new DATranscxt(Connects.defltConn());
			
		Utils.logi("Initializing session with default jdbc connection %s ...", Connects.defltConn());

		AnSession.init(defltScxt);
	}

	public static ISessionVerifier getSessionVerifier() {
		if (ssVerier == null) {
			String cfg = Configs.getCfg(keys.disableTokenKey);
			boolean verifyToken = isblank(cfg) ? true : bool(cfg);
			if (!verifyToken)
				Utils.warn("Verifying token is recommended but is disabled by config.xml/k=%s",
						keys.disableTokenKey);
			ssVerier = new AnSession(verifyToken);
		}
		return ssVerier;
	}
	
	/**
	 * @since 1.4.36 avoid using Configs when testing.
	 * @param verifier
	 */
	public static void setSessionVerifier(ISessionVerifier verifier) {
		ssVerier = verifier;
	}

	/**Get server root/WEB-INF path (filesystem local)
	 * @return WEB-INF root path
	public static String rootINF() { return webINF; }
	 */

	/**Get WEB-INF file path
	 * @param filename
	 * @return rootINF() + filename
	 */
	public static String getFileInfPath(String filename) {
		return FilenameUtils.concat(webINF, filename);
	}
}
