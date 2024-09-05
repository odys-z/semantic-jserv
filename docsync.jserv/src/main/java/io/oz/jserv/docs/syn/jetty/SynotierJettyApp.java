package io.oz.jserv.docs.syn.jetty;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.touchDir;
import static io.oz.jserv.docs.syn.ExpSynodetier.setupDomanagers;

import java.io.PrintStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;

import javax.servlet.annotation.WebServlet;

import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util_ody.RolloverFileOutputStream;

import io.odysz.anson.Anson;
import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.R.AnQuery;
import io.odysz.semantic.jserv.U.AnUpdate;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.HeartLink;
import io.odysz.semantic.syn.SynodeMode;
import io.oz.jserv.docs.syn.ExpDoctier;
import io.oz.jserv.docs.syn.ExpSynodetier;
import io.oz.jserv.docs.syn.SynDomanager;
import io.oz.jserv.docs.syn.Syngleton;

/**
 * Start an embedded Jetty server for ee8.
 * See <a href='https://stackoverflow.com/a/66368511'>
 * Joakim Erdfelt, Some quick history</a> for why ee8.
 * 
 * <h6>References:</h6>
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

	public static void main(String[] args) throws Exception {
		try {
			String bind = args[0];
			int    port = Integer.valueOf(args[1]);
			String org     = args[2];
			String domain  = args[3];
			String webinf  = args.length > 4 ? args[4] : "WEB-INF";
			String cfgxml  = !isNull(args) && args.length > 5 ? args[5] : "config.xml";
			String synconn = !isNull(args) && args.length > 6 ? args[6] : "sqlite-main";
		
			Utils.logi("Starting Synodetier at port %s, org %s, domain %s, configure file %s, conn %s",
					port, org, domain, cfgxml, synconn);

			Configs.init(webinf);
			Connects.init(webinf);

			SynotierJettyApp app = startSyndoctier(synconn, cfgxml, bind, port, webinf, org, domain);

			Utils.pause(String.format("[Synodetier] started at port %s, org %s, domain %s, configure file %s, conn %s",
					port, org, domain, cfgxml, synconn));
			
			app.stop();
		}
		catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	public static SynotierJettyApp startSyndoctier(String serv_conn, String config_xml,
			String bindIp, int port,
			String webinf, String org, String domain) throws Exception {
		Configs.init(webinf, config_xml);
		String synid  = Configs.getCfg(Configs.keys.synode);
		Utils.logi("------------ Starting %s ... --------------", synid);
	
		HashMap<String,SynDomanager> domains = setupDomanagers(org, domain, synid, serv_conn, SynodeMode.peer);
	
		ExpDoctier doctier  = new ExpDoctier(synid, serv_conn)
							.start(org, domain, SynodeMode.peer)
							.domains(domains);
		ExpSynodetier syner = new ExpSynodetier(org, domain, synid, serv_conn, SynodeMode.peer)
							.domains(domains);
		
		return startJettyServ(webinf, serv_conn, config_xml, // "config-0.xml",
				bindIp, port,
				new AnSession(), new AnQuery(), new AnUpdate(), new HeartLink())
			.addServPort(doctier)
			.addServPort(syner)
			;
	}

	/**
	 * Create a Jetty instance at local host, jserv-root
	 * for accessing online is in field {@link #jserv}.
	 * 
	 * @param configPath
	 * @param conn0
	 * @param configxml
	 * @param port
	 * @param out 
	 * @return Jetty App
	 * @throws Exception
	 */
	static SynotierJettyApp instanserver(String configPath, String conn0, String configxml, String bindIp, int port)
			throws Exception {
	    Anson.verbose = false;
	
		Syngleton.initSynodetier(configxml, conn0, ".", configPath, "ABCDEF0123456789");
	    AnsonMsg.understandPorts(Port.docsync);
	    
	    SynotierJettyApp synapp = new SynotierJettyApp();
	
	    if (isblank(bindIp))
	    	synapp.server = new Server();
	    else
	    	synapp.server = new Server(new InetSocketAddress(bindIp, port));
	
	    // httpConnector.setHost(ip);
	    InetAddress inet = InetAddress.getLocalHost();
	    String addrhost  = inet.getHostAddress();
		synapp.jserv = String.format("http://%s:%s", addrhost, port);
	
	    ServerConnector httpConnector = new ServerConnector(synapp.server);
	    httpConnector.setHost(addrhost);
	    httpConnector.setPort(port);
	    httpConnector.setIdleTimeout(5000);
	    synapp.server.addConnector(httpConnector);
	    
	    synapp.synodetiers = new HashMap<String, HashMap<String, SynDomanager>>();
	    
	    return synapp;
	}

	/**
	 * { url-pattern: { domain: domanager } },<br>
	 * e. g. { docs.sync: { zsu: { new SnyDomanger(x, y) } }
	 */
	public HashMap<String, HashMap<String, SynDomanager>> synodetiers;

	/**
	 * Start jserv with Jetty, register jserv-ports to Jetty.
	 * 
	 * @param <T> subclass of {@link ServPort}
	 * @param configPath
	 * @param conn
	 * @param configxml name of config.xml, to be optimized
	 * @param ip
	 * @param port
	 * @param servports
	 * @return Jetty server, the {@link SynotierJettyApp}
	 * @throws Exception
	 */
	@SafeVarargs
	static public <T extends ServPort<? extends AnsonBody>> SynotierJettyApp startJettyServ(
			String configPath, String conn, String configxml, String bindIp, int port,
			T ... servports) throws Exception {

		SynotierJettyApp synapp = instanserver(configPath, conn, configxml, bindIp, port);

        synapp.schandler = new ServletContextHandler(synapp.server, "/");
        for (T t : servports) {
        	synapp.registerServlets(synapp.schandler, t.trb(new DATranscxt(conn)));
        }

		touchDir("jetty-log");
//        RolloverFileOutputStream os = new RolloverFileOutputStream("jetty-log/yyyy_mm_dd.log", true);
//        PrintStream logStream = new PrintStream(os);
        Utils.logOut(System.out);
        Utils.logErr(System.err);
       
        synapp.server.start();

		logi("Server started at %s\nURI: %s", synapp.jserv, synapp.server.getURI());
        return synapp;
	}

    <T extends ServPort<? extends AnsonBody>> SynotierJettyApp registerServlets(
    		ServletContextHandler context, T t) {
//    	if(!isNull(out_err)) {
//    		// Utils.logOut(out_err[0]);
//    		ServPort.outstream(out_err[0]);
//			if(out_err.length > 1)
//				// Utils.logErr(out_err[1]);
//				ServPort.outstream(out_err[1]);
//    	}
    	touchDir("jetty-log");
    	ServPort.rolloverLog("jetty-log/yyyy-mm-dd.log");

		WebServlet info = t.getClass().getAnnotation(WebServlet.class);
		for (String pattern : info.urlPatterns()) {
			context.addServlet(new ServletHolder(t), pattern);
			
			if (t instanceof ExpSynodetier)
				synodetiers.put(pattern, ((ExpSynodetier)t).domains);
		}
		return this;
	}

//	static void registerServlets(ServletContextHandler context, Class<? extends HttpServlet> type)
//			throws ReflectiveOperationException {
//		WebServlet info = type.getAnnotation(WebServlet.class);
//		for (String pattern : info.urlPatterns()) {
//			HttpServlet servlet = type.getConstructor().newInstance();
//			context.addServlet(new ServletHolder(servlet), pattern);
//		}
//	}

	public SynotierJettyApp addServPort(ServPort<?> p) {
       	registerServlets(schandler, p);
       	return this;
	}
	
	public void stop() throws Exception {
		if (server != null)
			server.stop();
	}
}
