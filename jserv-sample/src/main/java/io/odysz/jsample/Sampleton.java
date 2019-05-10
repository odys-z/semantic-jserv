package io.odysz.jsample;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.apache.commons.io.FilenameUtils;
import org.xml.sax.SAXException;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.jsample.protocol.Samport;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.sworkflow.CheapEngin;
import io.odysz.sworkflow.ICheapChecker;
import io.odysz.transact.x.TransException;

@WebListener
public class Sampleton extends JSingleton implements ServletContextListener {

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		super.onInitialized(sce);
		
		String relapath = null;
		try {
			// Because of the java enum limitation, or maybe the author's knowledge limitation, 
			// JMessage needing a IPort instance to handle ports that implemented a new version of valof() method handling all ports.<br>
			// E.g. {@link Samport#menu#valof(name)} can handling both {@link Port} and Samport's enums.
			JMessage.understandPorts(Samport.menu);

			// init semantics
			// Current version only support default connection's semantics.
			DATranscxt.initConfigs(Connects.defltConn(),
				FilenameUtils.concat(rootINF(), "semantics.xml"));
			
			ICheapChecker checker = null; // TODO

			relapath = Configs.getCfg("cheap", "config-path");
			CheapEngin.initCheap(getFileInfPath(relapath), checker);
		} catch (IOException e) {
			Utils.warn("%s: %s\nCheck Config.xml:\ntable=cheap\nk=config-path\nv=%s",
					e.getClass().getName(), e.getMessage(), relapath);
		} catch (TransException | SAXException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		CheapEngin.stopCheap();
		super.onDestroyed(sce);
	}

}
