package io.odysz.semantic.jserv;

import java.io.IOException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.xml.sax.SAXException;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.semantic.DASemantext;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DATranscxt;
import io.odysz.semantic.jsession.ISessionVerifier;
import io.odysz.semantic.jsession.SSession;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.x.SemanticException;

/**This application scope initializing and managing module should be here.
 * @author ody
 *
 */
@WebListener
public class JSingleton implements ServletContextListener {

	public static DATranscxt defltScxt;
	private static ISessionVerifier session;
	private static String rootINF;

	public void contextDestroyed(ServletContextEvent arg0) {
	}

	public void contextInitialized(ServletContextEvent evt) {
		Utils.printCaller(false);
		Utils.logi("JSingleton initializing...");

		rootINF = evt.getServletContext().getRealPath("/WEB-INF");
		Connects.init(rootINF);
		Configs.init(rootINF);
		
		try {
			ISemantext s = new DASemantext("inet", rootINF + "/semantics.xml");
			defltScxt = new DATranscxt(s);
			SSession.init(defltScxt);
			session = new SSession();
		} catch (SemanticException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	public static ISessionVerifier getSessionVerifier() {
		return session;
	}

	/**Get server root/WEB-INF path (filesystem local)
	 * @return
	 */
	public static String rootINF() {
		return rootINF;
	}

}
