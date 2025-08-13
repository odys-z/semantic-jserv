package io.odysz.jsample;

import java.io.IOException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.jsample.protocol.Samport;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jserv.JSingleton;

/**
 * @since 1.0.0
 */
@WebListener
public class Sampleton extends JSingleton implements ServletContextListener {

	/**
	 * @since 1.5.3
	 */
	public final SampleSettings settings;

	public Sampleton(SampleSettings settings) {
		this.settings = settings;
	}

	/**
	 * @deprecated since 1.5.4, JSample is not maintained for working in Servlet containers. 
	 */
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		String relapath = null;
		try {
			super.onInitialized(sce);

			// Because of the java enum limitation, or maybe the author's knowledge limitation, 
			// JMessage needing a IPort instance to handle ports that implemented a new version of valof() method handling all ports.<br>
			// E.g. {@link Samport#menu#valof(name)} can handling both {@link Port} and Samport's enums.
			// AnsonMsg.understandPorts(Samport.menu);
			JProtocol.setup("jserv-sample", Samport.menu);

			relapath = Configs.getCfg("cheap", "config-path");

			// meta must loaded by DATranscxt before initCheap()
			// HashMap<String, ICheapChecker> checker = null; // To be tested
			// CheapEnginv1.initCheap(getFileInfPath(relapath), checker);
		} catch (IOException e) {
			e.printStackTrace();
			Utils.warn("%s: %s\nCheck Config.xml:\ntable=cheap\nk=config-path\nv=%s",
					e.getClass().getName(), e.getMessage(), relapath);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		// CheapEnginv1.stopCheap();
		super.onDestroyed(sce);
	}

}
