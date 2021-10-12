package io.oz.sandbox;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.JSingleton;
import io.oz.sandbox.protocol.Sandport;

@WebListener
public class SandboxSingleton extends JSingleton implements ServletContextListener {

	@Override
	public void contextInitialized(ServletContextEvent sce) {

		super.onInitialized(sce);
		
		AnsonMsg.understandPorts(Sandport.userstier);
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		super.onDestroyed(sce);
	}

}
