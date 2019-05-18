package io.odysz.jsample;

import java.io.IOException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.xml.sax.SAXException;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.jsample.protocol.Samport;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.sworkflow.CheapEngin;
import io.odysz.sworkflow.ICheapChecker;
import io.odysz.transact.x.TransException;

@WebListener
public class Sampleton extends JSingleton implements ServletContextListener {

//	private static boolean initAgain = false;

	@Override
	public void contextInitialized(ServletContextEvent sce) {
//		if (initAgain) {
//			// This method can be called twice by tomcat. And there are similar reports like:
//			// https://stackoverflow.com/questions/16702011/tomcat-deploying-the-same-application-twice-in-netbeans 
//			Utils.warn("Once agin ??????????");
//			return;
//		}
//
//		initAgain = true;

		super.onInitialized(sce);
		
		String relapath = null;
		try {
			// Because of the java enum limitation, or maybe the author's knowledge limitation, 
			// JMessage needing a IPort instance to handle ports that implemented a new version of valof() method handling all ports.<br>
			// E.g. {@link Samport#menu#valof(name)} can handling both {@link Port} and Samport's enums.
			JMessage.understandPorts(Samport.menu);

			// init semantics
			// Current version only support default connection's semantics.
//			DATranscxt.initConfigs(Connects.defltConn(),
//				FilenameUtils.concat(rootINF(), "semantics.xml"));
			
			ICheapChecker checker = null; // TODO

			relapath = Configs.getCfg("cheap", "config-path");
			// meta must loaded by DATranscxt before initCheap()
			CheapEngin.initCheap(getFileInfPath(relapath), checker);
		} catch (IOException e) {
			e.printStackTrace();
			Utils.warn("%s: %s\nCheck Config.xml:\ntable=cheap\nk=config-path\nv=%s",
					e.getClass().getName(), e.getMessage(), relapath);
		} catch (TransException | SAXException e) {
			e.printStackTrace();
//		} catch (SQLException e) {
//			e.printStackTrace();
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		CheapEngin.stopCheap();
		super.onDestroyed(sce);
	}

}
