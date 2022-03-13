package io.oz.album;

import java.lang.reflect.InvocationTargetException;

import javax.servlet.Servlet;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.HeartLink;
import io.oz.album.helpers.Winserv;
import io.oz.album.helpers.Winserv.keys;
import io.oz.album.tier.Albums;

public class JettyApp {
	static Server server;

    public static void main(String[] args) throws Exception {

		AnsonMsg.understandPorts(AlbumPort.album);

        WebAppContext wacHandler = new WebAppContext();

    	JSingleton.initJserv(".", "WEB-INF", "ABCDEF0123456789");
    	Winserv.init(AlbumSingleton.winserv_xml);

        wacHandler.setContextPath("/jserv-album");
        wacHandler.setResourceBase(".");
        registerServlets(wacHandler, Albums.class);
        registerServlets(wacHandler, AnSession.class);
        registerServlets(wacHandler, HeartLink.class);

        ServletHolder holderHome = new ServletHolder("static-home", DefaultServlet.class);
        holderHome.setInitParameter("resourceBase", "dist");
        // holderHome.setInitParameter("dirAllowed", "true");
        holderHome.setInitParameter("pathInfoOnly", "true");
        wacHandler.addServlet(holderHome, "/dist/*");

        server = new Server();

        server.setHandler(wacHandler);
        
        ServerConnector httpConnector = new ServerConnector(server);
        httpConnector.setHost(Winserv.v(keys.bind));
        httpConnector.setPort(Winserv.vint(keys.port, 8080));
        httpConnector.setIdleTimeout(5000);
        server.addConnector(httpConnector);
        
        // System.getenv().put("VOLUME_HOME", Winserv.v(keys.volume));
        System.setProperty("VOLUME_HOME", Winserv.v(keys.volume));

        server.start();
        server.join();
    }

    static <R extends AnsonBody> void registerServlets(WebAppContext context, Class<? extends ServPort<R>> type)
    		throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {

		WebServlet info = type.getAnnotation(WebServlet.class);
		for (String pattern : info.urlPatterns()) {
			Servlet servlet = type.getConstructor().newInstance();
			context.addServlet(new ServletHolder(servlet), pattern);
		}
	}

	static void registerServlets(ServletContextHandler context, Class<? extends HttpServlet> type)
    		throws InstantiationException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {

		WebServlet info = type.getAnnotation(WebServlet.class);
		for (String pattern : info.urlPatterns()) {
			HttpServlet servlet = type.getConstructor().newInstance();
			context.addServlet(new ServletHolder(servlet), pattern);
		}
	}
	
	public static void stop(String[] args) throws Exception {
		if (server != null)
			server.stop();
	}
}
