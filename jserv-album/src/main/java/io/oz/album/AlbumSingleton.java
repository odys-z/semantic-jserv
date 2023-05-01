package io.oz.album;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docsync.Docsyncer;
import io.oz.jserv.docsync.Synode;

@WebListener
public class AlbumSingleton extends JSingleton implements ServletContextListener {
	
	static public final String winserv_xml = "WEB-INF/winserv.xml";

	@Override
	public void contextInitialized(ServletContextEvent sce) {

		try {
			super.onInitialized(sce);
			
			AnsonMsg.understandPorts(AlbumPort.album);
			
			Docsyncer.init(System.getProperty("JSERV_NODE"));
			/*
			Docsyncer.addSyncTable(new PhotoMeta(Connects.defltConn()));
			Docsyncer.addSyncTable(new JUser.JUserMeta(Connects.defltConn()));
			Synode.init(System.getProperty("JSERV_NODE"));
			*/

		} catch (TransException | SAXException | IOException | SQLException | AnsonException | SsException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		super.onDestroyed(sce);
	}

}
