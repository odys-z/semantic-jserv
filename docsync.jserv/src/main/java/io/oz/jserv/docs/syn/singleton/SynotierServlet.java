package io.oz.jserv.docs.syn.singleton;

import java.io.File;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import io.oz.syn.registry.SynodeConfig;
import io.oz.syn.registry.YellowPages;

public class SynotierServlet extends Syngleton implements ServletContextListener {

	public SynotierServlet(SynodeConfig cfg, AppSettings settings) throws Exception {
		super(cfg, settings);
	}

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		String p = new File(".").getAbsolutePath();

		try {
			YellowPages.load(p);
			SynodeConfig cfg = YellowPages.synconfig();

			Syngleton.setupSysRecords(cfg, YellowPages.robots());
			
			Syngleton.setupSyntables(cfg, null,
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
