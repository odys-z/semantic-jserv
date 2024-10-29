package io.oz.jserv.docs.syn.singleton;

import java.io.File;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import io.oz.syn.SynodeConfig;
import io.oz.syn.YellowPages;

public class SynotierServlet extends Syngleton implements ServletContextListener {

	public SynotierServlet(SynodeConfig cfg) {
		super(cfg);
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		String p = new File(".").getAbsolutePath();

		try {
			YellowPages.load(p);
			SynodeConfig cfg = YellowPages.synconfig();

			Syngleton.setupSysRecords(cfg, YellowPages.robots());
			
			Syngleton.setupSyntables(this, cfg, null,
					"WEB-INF", "config.xml", ".", "********");

			Syngleton.initSynodeRecs(cfg, cfg.peers());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
	}
}
