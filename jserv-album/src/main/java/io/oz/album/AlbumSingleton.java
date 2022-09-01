package io.oz.album;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.xml.sax.SAXException;

import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantics.x.SemanticException;
import io.oz.jserv.sync.Docsyncer;

@WebListener
public class AlbumSingleton extends JSingleton implements ServletContextListener {
	
	static public final String winserv_xml = "WEB-INF/winserv.xml";

	@Override
	public void contextInitialized(ServletContextEvent sce) {

		try {
			super.onInitialized(sce);
			
			AnsonMsg.understandPorts(AlbumPort.album);
			// Anson.verbose = true;
			
			Docsyncer.init(sce, System.getProperty("JSERV_NODE"));
		} catch (SemanticException | SAXException | IOException | SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		super.onDestroyed(sce);
	}

}
