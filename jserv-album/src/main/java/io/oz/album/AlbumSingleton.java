package io.oz.album;

import static io.odysz.common.LangExt.isblank;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;

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
import io.oz.album.helpers.Exiftool;
import io.oz.album.tier.PhotoMeta;
import io.oz.jserv.docsync.Docsyncer;
import io.oz.jserv.docsync.Synode;

@WebListener
public class AlbumSingleton extends JSingleton implements ServletContextListener {
	
	static public final String winserv_xml = "WEB-INF/winserv.xml";

	/** @since 0.6.50:temp-try */
	static String node;
	
	/** @since 0.6.50:temp-try */
	public static String synode() { return node; }

	@Override
	public void contextInitialized(ServletContextEvent sce) {

		try {
			String webinf = super.onInitialized(sce);
			
			AnsonMsg.understandPorts(AlbumPort.album);
			
			node = System.getProperty("JSERV_NODE");
			if (isblank(node)) node = "mvp-hub";
			Docsyncer.init(node);
			Synode.init(node);

			// MVP 0.2.1, temporary way of create meta
			Docsyncer.metas(Connects.getMeta(Connects.defltConn()));
			Docsyncer.addSyncTable(new PhotoMeta(Connects.defltConn()));
			
			// Exif.init(webinf);
			Exiftool.init();
			JSingleton.health = true;
		} catch (Exception e) {
			e.printStackTrace();
			JSingleton.health = false;
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		super.onDestroyed(sce);
	}

}
