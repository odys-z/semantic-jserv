package io.odysz.jquiz.app;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import io.odysz.jsample.protocol.Quizport;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.JSingleton;

/**jserv-quiz singleton, similar to Sampleton.
 * 
 * @author odys-z@github.com
 */
@WebListener
public class Quizleton extends JSingleton implements ServletContextListener {

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		super.onInitialized(sce);
		AnsonMsg.understandPorts(Quizport.menu);
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		super.onDestroyed(sce);
	}

}
