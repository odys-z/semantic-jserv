package io.oz.jserv.test;

import static io.odysz.common.Utils.logi;

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
	Server server;
	ServletContextHandler schandler;
	String jserv;

	static JettyHelper instanserver(String configPath, String conn0, String configxml, String ip, int port)
			throws Exception {
        Anson.verbose = false;

    	Syngleton.initSynodetier(configxml, conn0, ".", configPath, "ABCDEF0123456789");
        AnsonMsg.understandPorts(Port.docsync);
        
        JettyHelper helper = new JettyHelper();

        helper.server = new Server();

        ServerConnector httpConnector = new ServerConnector(helper.server);
        httpConnector.setHost(ip);
        httpConnector.setPort(port);
        httpConnector.setIdleTimeout(5000);
        helper.server.addConnector(httpConnector);
        
        return helper;
	}

	@SafeVarargs
	static public <T extends ServPort<? extends AnsonBody>> JettyHelper startJserv(
			String configPath, String conn, String configxml, String ip, int port,
			T ... servports) throws Exception {

		JettyHelper helper = instanserver(configPath, conn, configxml, ip, port);

        helper.schandler = new ServletContextHandler(helper.server, "/");
        for (T t : servports) {
        	helper.registerServlets(helper.schandler, t.trb(new DATranscxt(conn)));
        }

        helper.server.start();
        
        
		helper.jserv = String.format("http://%s:%s", ip, port);
		logi("Server started at %s", helper.jserv);
        return helper;
	}

    <T extends ServPort<? extends AnsonBody>> JettyHelper registerServlets(ServletContextHandler context, T t) {
		WebServlet info = t.getClass().getAnnotation(WebServlet.class);
		for (String pattern : info.urlPatterns()) {
			context.addServlet(new ServletHolder(t), pattern);
		}
		return this;
	}

	static void registerServlets(ServletContextHandler context, Class<? extends HttpServlet> type)
			throws ReflectiveOperationException {
		WebServlet info = type.getAnnotation(WebServlet.class);
		for (String pattern : info.urlPatterns()) {
			HttpServlet servlet = type.getConstructor().newInstance();
			context.addServlet(new ServletHolder(servlet), pattern);
		}
	}

	public JettyHelper addServPort(ServPort<?> p) {
       	registerServlets(schandler, p);
       	return this;
	}
	
	public void stop() throws Exception {
		if (server != null)
			server.stop();
	}
}
