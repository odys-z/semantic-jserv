package io.oz.jserv.docs.syn.jetty;

import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.Utils.logi;
import static io.oz.jserv.docs.syn.ExpSynodetier.setupDomanagers;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;

import javax.servlet.annotation.WebServlet;

import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.ServPort.PrintstreamProvider;
import io.odysz.semantic.jserv.R.AnQuery;
import io.odysz.semantic.jserv.U.AnUpdate;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.HeartLink;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
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
	String conn0;

	public String jserv() { return jserv; }

	/**
	 * Eclipse run configuration example:
	 * <pre>Run - Run Configurations - Arguments
	 * Program Arguments
	 * 192.168.0.100 8964 ura zsu src/test/res/WEB-INF config-0.xml no-jserv.00
	 * 
	 * VM Arguments
	 * -DVOLUME_HOME=../volume
	 * </pre>
	 * volume home = relative path to web-inf.
	 * 
	 * @param args [0] ip, [1] port, [2] org, [3] domain, [4] web-inf, [5] config.xml, [6] conn-id
	 * @throws Exception
	 */
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

			SynotierJettyApp app = createSyndoctierApp(synconn, cfgxml, bind, port, webinf, org, domain)
								.start(() -> System.out, () -> System.err);

			Utils.pause(String.format("[Synodetier] started at port %s, org %s, domain %s, configure file %s, conn %s",
					port, org, domain, cfgxml, synconn));
			
			app.stop();
		}
		catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}

	public static SynotierJettyApp createSyndoctierApp(String serv_conn,
			String config_xml, String bindIp, int port,
			String webinf, String org, String domain) throws Exception {

		Configs.init(webinf, config_xml);
		String synid  = Configs.getCfg(Configs.keys.synode);
		Utils.logi("------------ Starting %s ... --------------", synid);
	
		HashMap<String,SynDomanager> domains = setupDomanagers(org, domain, synid, serv_conn, SynodeMode.peer);
	
		ExpDoctier doctier  = new ExpDoctier(synid, serv_conn)
							.startier(org, domain, SynodeMode.peer)
							.domains(domains);
		ExpSynodetier syner = new ExpSynodetier(org, domain, synid, serv_conn, SynodeMode.peer)
							.domains(domains);
		
		SynotierJettyApp synapp = instanserver(webinf, serv_conn, config_xml, bindIp, port);
		return registerPorts(synapp, serv_conn,
				new AnSession(), new AnQuery(), new AnUpdate(), new HeartLink())
			.addServPort(doctier)
			.addServPort(syner)
			;
	}

	public SynotierJettyApp start(PrintstreamProvider out, PrintstreamProvider err) throws Exception {
		printout = out;
		printerr = err;

		ServPort.outstream(printout);
		ServPort.errstream(printout);

		server.start();
		return this;
	}

	/**
	 * Start jserv with Jetty, register jserv-ports to Jetty.
	 * 
	 * @param <T> subclass of {@link ServPort}
	 * @param synapp
	 * @param conn
	 * @param servports
	 * @return Jetty server, the {@link SynotierJettyApp}
	 * @throws Exception
	 */
	@SafeVarargs
	static public <T extends ServPort<? extends AnsonBody>> SynotierJettyApp registerPorts(
			SynotierJettyApp synapp, String conn, T ... servports) throws Exception {

		// SynotierJettyApp synapp = instanserver(configPath, conn, configxml, bindIp, port);

        synapp.schandler = new ServletContextHandler(synapp.server, "/");
        for (T t : servports) {
        	synapp.registerServlets(synapp.schandler, t.trb(new DATranscxt(conn)));
        }

		logi("Server is bound to %s\nFirst bound URI: %s", synapp.jserv, synapp.server.getURI());
        return synapp;
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
	public static SynotierJettyApp instanserver(String configPath, String conn0, String configxml,
			String bindIp, int port) throws Exception {
	
		Syngleton.initSynodetier(configxml, conn0, ".", configPath, "ABCDEF0123456789");
	    AnsonMsg.understandPorts(Port.syntier);
	    
	    SynotierJettyApp synapp = new SynotierJettyApp();
	    synapp.conn0 = conn0;
	
	    if (isblank(bindIp))
	    	synapp.server = new Server();
	    else
	    	synapp.server = new Server(new InetSocketAddress(bindIp, port));
	
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

	SynodeMeta synm;
	ExpDocTableMeta docm;

	public SynotierJettyApp metas(SynodeMeta synm, ExpDocTableMeta docm) {
		this.synm = synm;
		this.docm = docm;
		return this;
	}

	/**
	 * { url-pattern: { domain: domanager } },<br>
	 * e. g. { docs.sync: { zsu: { new SnyDomanger(x, y) } }
	 */
	public HashMap<String, HashMap<String, SynDomanager>> synodetiers;
	
	/**
	 * Url pattern (key in {@link #synodetiers}) of {@link ExpSynodetier}.
	 */
	String syntier_url;

	PrintstreamProvider printout;
	PrintstreamProvider printerr;

	<T extends ServPort<? extends AnsonBody>> SynotierJettyApp registerServlets(
    		ServletContextHandler context, T t) {
		WebServlet info = t.getClass().getAnnotation(WebServlet.class);
		for (String pattern : info.urlPatterns()) {
			context.addServlet(new ServletHolder(t), pattern);
			
			if (t instanceof ExpSynodetier) {
				synodetiers.put(pattern, ((ExpSynodetier)t).domains);
				syntier_url = pattern;
			}
		}
		
		return this;
	}

	public SynotierJettyApp addServPort(ServPort<?> p) {
       	registerServlets(schandler, p);
       	return this;
	}
	
	public void stop() throws Exception {
		if (server != null)
			server.stop();
	}
	
	/**
	 * Synode id for the default domain upon which the {@link ExpSynodetier} works.
	 * @return synode id
	 */
	public String synode() {
		if (synodetiers != null && synodetiers.containsKey(syntier_url))
			for (SynDomanager domanager : synodetiers.get(syntier_url).values())
				return domanager.synode;
		return null;
	}

	public SynotierJettyApp loadDomains(SynodeMode synmod) throws Exception {
		if (synodetiers == null)
			synodetiers = new HashMap<String, HashMap<String, SynDomanager>>();
		
		DATranscxt t0 = new DATranscxt(null);
		IUser usr = DATranscxt.dummyUser();

		AnResultset rs = (AnResultset) t0
				.select(synm.tbl)
				.groupby(synm.domain)
				.groupby(synm.synuid)
				.rs(t0.instancontxt(conn0, usr))
				.rs(0);
		
		while (rs.next()) {
			String domain = rs.getString(synm.domain);
			SynDomanager domanger = new SynDomanager(
					rs.getString(synm.org),
					domain,
					rs.getString(synm.synoder),
					conn0, synmod);
			synodetiers.get(syntier_url).put(domain, domanger);
		}

		return this;
	}

	/**
	 * Try join (login) known domains
	 * @return
	 * @throws IOException 
	 * @throws SsException 
	 * @throws AnsonException 
	 * @throws SemanticException 
	 */
	public SynotierJettyApp openDomains() throws SemanticException, AnsonException, SsException, IOException {
		if (synodetiers != null && synodetiers.containsKey(syntier_url))
			for (SynDomanager domanager : synodetiers.get(syntier_url).values())
				domanager.updomains((domain, mynid, peer, xp) -> {
					Utils.logi("%s: domain is bringing up: %s : %s", mynid, domain, peer);
				});

		return this;
	}
}
