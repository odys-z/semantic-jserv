package io.odysz.semantic.jserv;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DATranscxt;
import io.odysz.semantic.jsession.ISessionVerifier;
import io.odysz.semantic.jsession.SSession;

/**This application scope initializing and managing module should be here.
 * @author ody
 *
 */
@WebListener
public class JSingleton implements ServletContextListener {

	public static DATranscxt st;
	private static ISessionVerifier session;

	public void contextDestroyed(ServletContextEvent arg0) {
	}

	public void contextInitialized(ServletContextEvent evt) {
		Utils.printCaller(false);

		String xmlDir = evt.getServletContext().getRealPath("/WEB-INF");
		Connects.init(xmlDir);
		Configs.init(xmlDir);
		
		st = new DATranscxt(null);
		
		session = new SSession();
	}

	public static ISessionVerifier getSessionVerifier() {
		return session;
	}

}
