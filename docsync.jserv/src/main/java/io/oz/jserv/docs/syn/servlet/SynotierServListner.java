package io.oz.jserv.docs.syn.servlet;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import io.oz.jserv.docs.syn.singleton.Syngleton;

public class SynotierServListner extends Syngleton implements ServletContextListener {

	@Override
	public void contextInitialized(ServletContextEvent sce) {

	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		// TODO Auto-generated method stub

	}

}
