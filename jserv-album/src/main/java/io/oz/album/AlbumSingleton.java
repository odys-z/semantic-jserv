package io.oz.album;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.JSingleton;
import io.oz.album.helpers.Exiftool;
import io.oz.album.peer.AlbumPort;

/**
 * @deprecated
 */
@WebListener
public class AlbumSingleton extends JSingleton implements ServletContextListener {
	
	static public final String winserv_xml = "WEB-INF/winserv.xml";

	/**@deprecated 0.7.0
	 * @since 0.6.50:temp-try */
	static String node;
	
	/**
	 * @deprecated 0.7.0
	 * @since 0.6.50:temp-try */
	 public static String synode() { return node; }

	// SynodeServ synodeServ;

	@Override
	public void contextInitialized(ServletContextEvent sce) {

		try {
			@SuppressWarnings("unused")
			String webinf = super.onInitialized(sce);
			
			AnsonMsg.understandPorts(AlbumPort.album);
			
			// node = System.getProperty("JSERV_NODE");
			// if (isblank(node)) node = "mvp-hub";
			// synodeServ = new SynodeServ(node, SynodeMode.peer);

			// MVP 0.2.1, temporary way of create meta
			// Docsyncer.metas(Connects.getMeta(Connects.defltConn()));
			// Docsyncer.addSyncTable(new PhotoMeta(Connects.defltConn()));
			
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
