package io.oz.jserv.test;

import java.lang.reflect.InvocationTargetException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;

import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import io.odysz.anson.Anson;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.ServPort;
import io.oz.jserv.docs.syn.Syngleton;

/**
 * Start an embedded Jetty server for ee8.
 * See <a href='https://stackoverflow.com/a/66368511'>
 * Joakim Erdfelt, Some quick history</a> 
 * 
 * Reference:
 * <ol><li><a href='https://www.javacodegeeks.com/wp-content/uploads/2016/09/Jetty-Server-Cookbook.pdf'>
 * Jetty Server Cookbook</a></li>
 * <li><a href='https://github.com/jetty/jetty-examples/tree/12.0.x/embedded'>
 * Github: jetty/jetty-examples/embedded</a></li>
 * <li><a href='https://jetty.org/docs/jetty/12/programming-guide/index.html'>
 * Jetty 12 Programming Guide</a>, Jetty Documentation</li>
 * </ol>
 * 
 * @author odys-z@github.com
 *
 */
public class JettyHelper {
	static Server server;
	private static ServletContextHandler schandler;
	/** one singleton / container per tier per org? */
	// static String configxml = "per servlet container";

    /**
     * Start an embedded Jetty 12 server, evn ee8, for test etc.
     * 
     * <p>Note: all serv-port types must have a default contructor (zero parameters).
     * If this is not possible, use {@link #registerServlets(ServletContextHandler, ServPort)}</p>
     * @param configxml e. g. config.xml
     * @param ip
     * @param port
     * @param servports
     * @throws Exception
     * @since 2.0.0
     */
    @SafeVarargs
	public static void startJserv(String configPath, String conn, String configxml, String ip, int port, Class<? extends ServPort<?>> ... servports)
    		throws Exception {

        instanserver(configPath, conn, configxml, ip, port);

        schandler = new ServletContextHandler(server, "/");
        for (Class<? extends ServPort<?>> c : servports) {
        	registerServlets(schandler, c);
        }
		
        server.start();
    }

	private static void instanserver(String configPath, String conn0, String configxml, String ip, int port) throws Exception {
        Anson.verbose = false;

    	Syngleton.initSynodetier(configxml, conn0, ".", configPath, "ABCDEF0123456789");
        AnsonMsg.understandPorts(Port.docsync);
        
        if (server != null)
        	server.stop();

        server = new Server();

        ServerConnector httpConnector = new ServerConnector(server);
        httpConnector.setHost(ip);
        httpConnector.setPort(port);
        httpConnector.setIdleTimeout(5000);
        server.addConnector(httpConnector);
	}

	@SafeVarargs
	public static <T extends ServPort<? extends AnsonBody>> void startJserv(
			String configPath, String conn, String configxml,
			String ip, int port, T ... servports) throws Exception {

        instanserver(configPath, conn, configxml, ip, port);

        schandler = new ServletContextHandler(server, "/");
        for (T t : servports) {
        	registerServlets(schandler, t.trb(new DATranscxt(conn)));
        }

        server.start();
	}

    static <T extends ServPort<? extends AnsonBody>> void registerServlets(ServletContextHandler context, T t) {
		WebServlet info = t.getClass().getAnnotation(WebServlet.class);
		for (String pattern : info.urlPatterns()) {
			context.addServlet(new ServletHolder(t), pattern);
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

	public static void addPort(ServPort<?> p) {
       	registerServlets(schandler, p);
	}
	
	public static void stop() throws Exception {
		if (server != null)
			server.stop();
	}

}
