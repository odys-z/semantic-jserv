package io.odysz.jsample;

import java.io.IOException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.apache.commons.io.FilenameUtils;
import org.xml.sax.SAXException;

import io.odysz.common.Configs;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.sworkflow.CheapEngin;
import io.odysz.transact.x.TransException;

@WebListener
public class Sampleton extends JSingleton implements ServletContextListener {

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		super.onInitialized(sce);
		
		try {
			CheapEngin.initCheap(FilenameUtils.concat(rootINF(), Configs.getCfg("cheap", "config-path")), null);
		} catch (TransException | IOException | SAXException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		CheapEngin.stopCheap();
		super.onDestroyed(sce);
	}

}
