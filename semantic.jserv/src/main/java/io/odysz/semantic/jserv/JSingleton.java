package io.odysz.semantic.jserv;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import io.odysz.common.Utils;
import io.odysz.semantic.DA.Connects;
import io.odysz.transact.sql.Transcxt;

/**This application scope initializing and managing module should be here.
 * @author ody
 *
 */
@WebListener
public class JSingleton implements ServletContextListener {

	public static Transcxt st;

	public void contextDestroyed(ServletContextEvent arg0) {
	}

	public void contextInitialized(ServletContextEvent evt) {
		Utils.printCaller(false);

		String xmlDir = evt.getServletContext().getRealPath("/WEB-INF");
		Connects.init(xmlDir);
		
		st = new Transcxt(null);
	}

}
