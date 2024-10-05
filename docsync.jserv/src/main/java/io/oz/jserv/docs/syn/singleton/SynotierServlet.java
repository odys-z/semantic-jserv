package io.oz.jserv.docs.syn.singleton;

import static io.oz.jserv.docs.syn.singleton.SynotierJettyApp.setupSyntables;
import static io.oz.jserv.docs.syn.singleton.SynotierJettyApp.setupSysRecords;

import java.io.File;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import io.oz.syn.SynodeConfig;
import io.oz.syn.YellowPages;

public class SynotierServlet extends Syngleton implements ServletContextListener {

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		String p = new File(".").getAbsolutePath();
//		System.setProperty("VOLUME_HOME", p + "/vol-" + i);

		try {
			YellowPages.load(p);
			SynodeConfig cfg = YellowPages.synconfig();

			setupSysRecords(cfg, YellowPages.robots());
			
			setupSyntables(cfg.synconn);

			Syngleton.initSynodeRecs(cfg, cfg.peers());
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// jetties[i] = startSyndoctier(cfgs[i]);
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
	}

}
