package io.oz.album;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.webapp.WebAppContext;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jsession.AnSession;
import io.oz.album.tier.Albums;

import javax.servlet.Servlet;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;

public class JettyApp {

    public static void main(String[] args) throws Exception {

		AnsonMsg.understandPorts(AlbumPort.album);

        Server server = new Server(8080);

        WebAppContext wacHandler = new WebAppContext();

    	JSingleton.initJserv(".", "WEB-INF", "ABCDEF0123456789");

        wacHandler.setContextPath("/jserv-album");
        wacHandler.setResourceBase(".");
        registerServlets(wacHandler, Albums.class);
        registerServlets(wacHandler, AnSession.class);

        wacHandler.addServlet(ExampleServlet.class, "/exam");

        server.setHandler(wacHandler);
        
        server.start();
        server.join();
    }

    static <R extends AnsonBody> void registerServlets(WebAppContext context, Class<? extends ServPort<R>> type) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
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
}
