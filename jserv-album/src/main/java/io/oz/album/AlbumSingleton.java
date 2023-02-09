package io.oz.album;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.JUser;
import io.odysz.semantics.x.SemanticException;
import io.oz.album.tier.PhotoMeta;
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
			
			Docsyncer.init(System.getProperty("JSERV_NODE"));
			
			Docsyncer.addSyncTable(new PhotoMeta(Connects.defltConn()));
			Docsyncer.addSyncTable(new JUser.JUserMeta(Connects.defltConn()));

		} catch (SemanticException | SAXException | IOException | SQLException e) {
			e.printStackTrace();
		} catch (AnsonException e) {
			e.printStackTrace();
		} catch (SsException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		super.onDestroyed(sce);
	}

}
