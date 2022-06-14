package io.odysz.acadynamo;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.xml.sax.SAXException;

import io.odysz.jquiz.protocol.Quizport;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantics.x.SemanticException;

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
		} catch (SemanticException | SAXException | IOException | SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		super.onDestroyed(sce);
	}

}
