package io.oz.jserv.docs.syn.jetty;

import static io.odysz.common.Utils.logi;
import static io.oz.jserv.docs.syn.ExpSynodetier.setupDomanagers;

import java.net.InetAddress;
import java.util.HashMap;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;

import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import io.odysz.anson.Anson;
import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.R.AnQuery;
import io.odysz.semantic.jserv.U.AnUpdate;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.HeartLink;
import io.odysz.semantic.meta.SemanticTableMeta;
import io.odysz.semantic.syn.SynodeMode;
import io.oz.jserv.docs.syn.ExpDoctier;
import io.oz.jserv.docs.syn.ExpSynodetier;
import io.oz.jserv.docs.syn.SynDomanager;
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
public class SynotierJettyApp {
	Server server;
	ServletContextHandler schandler;
	String jserv;
	public String jserv() { return jserv; }

	static void main(String[] args) {
	}

	public static SynotierJettyApp startSyndoctier(String serv_conn, String config_xml, int port,
			String webinf, String ura, String zsu, SemanticTableMeta ... docm) throws Exception {
		// AutoSeqMeta asqm = new AutoSeqMeta();
		// JRoleMeta arlm = new JUser.JRoleMeta();
		// JOrgMeta  aorgm = new JUser.JOrgMeta();
	
		// SynChangeMeta chm = new SynChangeMeta();
		// SynSubsMeta sbm = new SynSubsMeta(chm);
		// SynchangeBuffMeta xbm = new SynchangeBuffMeta(chm);
		// SynSessionMeta ssm = new SynSessionMeta();
		// PeersMeta prm = new PeersMeta();
	
		// SynodeMeta snm = new SynodeMeta(serv_conn);
		// docm = new T_PhotoMeta(serv_conn);
		// setupSqliTables(serv_conn, asqm, arlm, aorgm, snm, chm, sbm, xbm, prm, ssm, docm);

		// synode
		// String servIP = "localhost";
		
		Configs.init(webinf, config_xml);
		String synid  = Configs.getCfg(Configs.keys.synode);
		Utils.logi("------------ Starting %s ... --------------", synid);

		HashMap<String,SynDomanager> domains = setupDomanagers(ura, zsu, synid, serv_conn, SynodeMode.peer);

		ExpDoctier doctier  = new ExpDoctier(synid, serv_conn)
							.start(ura, zsu, SynodeMode.peer)
							.domains(domains);
		ExpSynodetier syner = new ExpSynodetier(ura, zsu, synid, serv_conn, SynodeMode.peer)
							.domains(domains);
		
		return SynotierJettyApp.startJettyServ(webinf, serv_conn, config_xml, // "config-0.xml",
				// servIP,
				port,
				new AnSession(), new AnQuery(), new AnUpdate(),
				new HeartLink())
			.addServPort(doctier)
			.addServPort(syner)
			;
	}
	/**
	 * { url-pattern: { domain: domanager } },<br>
	 * e. g. { docs.sync: { zsu: { new SnyDomanger(x, y) } }
	 */
	public HashMap<String, HashMap<String, SynDomanager>> synodetiers;

	/**
	 * Create a Jetty App at local host.
	 * @param configPath
	 * @param conn0
	 * @param configxml
	 * @param port
	 * @return Jetty App
	 * @throws Exception
	 */
	static SynotierJettyApp instanserver(String configPath, String conn0, String configxml, int port)
			throws Exception {
        Anson.verbose = false;

    	Syngleton.initSynodetier(configxml, conn0, ".", configPath, "ABCDEF0123456789");
        AnsonMsg.understandPorts(Port.docsync);
        
        SynotierJettyApp helper = new SynotierJettyApp();

        helper.server = new Server();

        // httpConnector.setHost(ip);
        InetAddress inet = InetAddress.getLocalHost();
        String addrhost  = inet.getHostAddress();
		helper.jserv = String.format("http://%s:%s", addrhost, port);

        ServerConnector httpConnector = new ServerConnector(helper.server);
        httpConnector.setHost(addrhost);
        httpConnector.setPort(port);
        httpConnector.setIdleTimeout(5000);
        helper.server.addConnector(httpConnector);
        
        helper.synodetiers = new HashMap<String, HashMap<String, SynDomanager>>();
        
        return helper;
	}

	/**
	 * Start jserv with Jetty.
	 * @param <T> subclass of {@link ServPort}
	 * @param configPath
	 * @param conn
	 * @param configxml name of config.xml, to be optimized
	 * @param ip
	 * @param port
	 * @param servports
	 * @return a helper instance
	 * @throws Exception
	 */
	@SafeVarargs
	static public <T extends ServPort<? extends AnsonBody>> SynotierJettyApp startJettyServ(
			String configPath, String conn, String configxml, int port,
			T ... servports) throws Exception {

		SynotierJettyApp helper = instanserver(configPath, conn, configxml, port);

        helper.schandler = new ServletContextHandler(helper.server, "/");
        for (T t : servports) {
        	helper.registerServlets(helper.schandler, t.trb(new DATranscxt(conn)));
        }

        helper.server.start();

		logi("Server started at %s\nURI: %s", helper.jserv, helper.server.getURI());
        return helper;
	}

    <T extends ServPort<? extends AnsonBody>> SynotierJettyApp registerServlets(ServletContextHandler context, T t) {
		WebServlet info = t.getClass().getAnnotation(WebServlet.class);
		for (String pattern : info.urlPatterns()) {
			context.addServlet(new ServletHolder(t), pattern);
			
			if (t instanceof ExpSynodetier)
				synodetiers.put(pattern, ((ExpSynodetier)t).domains);
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

	public SynotierJettyApp addServPort(ServPort<?> p) {
       	registerServlets(schandler, p);
       	return this;
	}
	
	public void stop() throws Exception {
		if (server != null)
			server.stop();
	}
}
