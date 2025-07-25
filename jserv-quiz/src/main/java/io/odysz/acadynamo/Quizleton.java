package io.odysz.acadynamo;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import io.odysz.jquiz.protocol.Quizport;
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
		try {
			super.onInitialized(sce);
			AnsonMsg.understandPorts(Quizport.menu);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		super.onDestroyed(sce);
	}

}
