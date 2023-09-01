package io.oz.album;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.apache.tika.exception.TikaException;
import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.transact.x.TransException;
import io.oz.album.helpers.Exif;
import io.oz.album.tier.PhotoMeta;
import io.oz.jserv.docsync.Docsyncer;
import io.oz.jserv.docsync.Synode;

@WebListener
public class AlbumSingleton extends JSingleton implements ServletContextListener {
	
	static public final String winserv_xml = "WEB-INF/winserv.xml";

	@Override
	public void contextInitialized(ServletContextEvent sce) {

		try {
			String webinf = super.onInitialized(sce);
			
			AnsonMsg.understandPorts(AlbumPort.album);
			
			String node = System.getProperty("JSERV_NODE");
			Docsyncer.init(node);
			Synode.init(node);

			// MVP 0.2.1, temporary way of create meta
			Docsyncer.metas(Connects.getMeta(Connects.defltConn()));
			Docsyncer.addSyncTable(new PhotoMeta(Connects.defltConn()));
			
			Exif.init(webinf);
			JSingleton.health = true;
		} catch (TransException | SAXException | TikaException | IOException | SQLException | AnsonException | SsException | ReflectiveOperationException e) {
			e.printStackTrace();
			JSingleton.health = false;
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		super.onDestroyed(sce);
	}

}
