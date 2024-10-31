package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.notNull;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;

import javax.servlet.annotation.WebServlet;

import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import io.odysz.common.Utils;
import io.odysz.semantic.DASemantics.SemanticHandler;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.ServPort.PrintstreamProvider;
import io.odysz.semantic.jserv.R.AnQuery;
import io.odysz.semantic.jserv.U.AnUpdate;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.HeartLink;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.syn.DBSynTransBuilder;
import io.odysz.semantic.syn.registry.Syntities;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.ExpDoctier;
import io.oz.jserv.docs.syn.ExpSynodetier;
import io.oz.jserv.docs.syn.SynDomanager;
import io.oz.syn.SynodeConfig;

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
	final Syngleton syngleton;

	Server server;

	ServletContextHandler schandler;

	List<SemanticHandler> synchandlers;

	public SynotierJettyApp(SynodeConfig cfg, List<SemanticHandler> synchangeHandlers) {
		syngleton = new Syngleton(cfg);
		synchandlers = synchangeHandlers;
	}

	/**
	 * Eclipse run configuration example:
	 * <pre>Run - Run Configurations - Arguments
	 * Program Arguments
	 * 192.168.0.100 8964 ura zsu src/test/res/WEB-INF config-0.xml no-jserv.00 odyz
	 * 
	 * VM Arguments
	 * -DVOLUME_HOME=../volume
	 * </pre>
	 * volume home = relative path to web-inf.
	 * 
	 * @param args [0] ip, * for all hosts
	 *             [1] port,
	 *             [2] org,
	 *             [3] domain,
	 *             [4] web-inf,
	 *             [5] config.xml,
	 *             [6] conn-id,
	 *             [7] robot id already registered on each peers
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
	}

	/**
	 * Create an application instance working as a synode tier.
	 * @param serv_conn db connection of which to be synchronized
	 * @param config_xml name of config file, e.g. config.xml
	 * @param bindIp, optional, null or '*' for all inet interface
	 * @param port
	 * @param webinf
	 * @param domain
	 * @param robot
	 * @return Synode-tier Jetty App
	 * @throws Exception
	 */
	public static SynotierJettyApp createSyndoctierApp( String config_xml,
			String syntity_json, SynodeConfig cfg, String webinf) throws Exception {

		String synid  = cfg.synode();
		String sync = cfg.synconn;

		SynotierJettyApp synapp = SynotierJettyApp.instanserver(webinf, cfg, config_xml, cfg.localhost, cfg.port)
								.loadomains(cfg);

		Utils.logi("------------ Starting %s ... --------------", synid);
	
		Syntities regists = Syntities.load(webinf, syntity_json, 
				(synreg) -> {
					throw new SemanticException("TODO %s (configure an entity table with meta type)", synreg.table);
				});	

		DBSynTransBuilder.synSemantics(new DATranscxt(sync), sync, synid, regists);

		SynDomanager domanger = synapp.syngleton.domanager(cfg.domain);
		
		ExpSynodetier syner = new ExpSynodetier(domanger);
		
		return registerPorts(synapp, cfg.synconn,
				new AnSession(), new AnQuery(), new AnUpdate(), new HeartLink())
			.addDocServPort(cfg.domain, webinf, syntity_json)
			.addServPort(syner)
			;
	}

	SynotierJettyApp loadomains(SynodeConfig cfg) throws Exception {
		syngleton.loadDomains(cfg);
		return this;
	}

	public SynotierJettyApp addDocServPort(String domain, String cfgroot, String syntity_json) throws Exception {
		notNull(domain);
		SynDomanager domanger = syngleton.domanager(domain);

		addServPort(new ExpDoctier(domanger
//				Syntities.load(
//					cfgroot, syntity_json, 
//					(synreg) -> {
//						throw new SemanticException(
//							"TODO syntity name: %s (Configure syntity.meta to avoid this error)",
//							synreg.table);
//					})
					)
				.domx(domanger));
		return this;
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

        synapp.schandler = new ServletContextHandler(synapp.server, "/");
        for (T t : servports) {
        	synapp.registerServlets(synapp.schandler, t.trb(new DATranscxt(conn)));
        }

		// logi("Server is bound to %s\nFirst bound URI: %s", synapp.jserv, synapp.server.getURI());
        return synapp;
	}

    PrintstreamProvider printout;
	PrintstreamProvider printerr;

	<T extends ServPort<? extends AnsonBody>> SynotierJettyApp registerServlets(
    		ServletContextHandler context, T t) {
		WebServlet info = t.getClass().getAnnotation(WebServlet.class);
		for (String pattern : info.urlPatterns()) {
			context.addServlet(new ServletHolder(t), pattern);
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
	
	public String jserv() {
		return syngleton.jserv;
	}

	public void updateJservs(SynodeMeta synm, SynodeConfig cfg, String domain)
			throws TransException, SQLException {
		syngleton.updatePeerJservs(synm, cfg, domain);
	}

//	public SynotierJettyApp loadDomains(SynodeConfig cfg) throws Exception {
//		syngleton.loadDomains(cfg);
//		return this;
//	}

	/**
	 * Create a Jetty instance at local host, jserv-root
	 * for accessing online is in field {@link #jserv}.
	 * 
	 * Tip: list all local tcp listening ports:
	 * sudo netstat -ntlp
	 * see https://askubuntu.com/a/328293
	 * 
	 * @param configPath
	 * @param cfg
	 * @param configxml
	 * @param bindIp
	 * @param port
	 * @param robotInf information for creating robot, i. e. the user identity for login to peer synodes.
	 * @return Jetty App
	 * @throws Exception
	 */
	public static SynotierJettyApp instanserver(String configPath, SynodeConfig cfg, String configxml,
			String bindIp, int port) throws Exception {
	
	    AnsonMsg.understandPorts(Port.syntier);
	
	    SynotierJettyApp synapp = new SynotierJettyApp( cfg,
	    						Syngleton.synmap.get(smtype.synChange));

		Syngleton.defltScxt = new DATranscxt(cfg.sysconn);
	
	    if (isblank(bindIp) || eq("*", bindIp)) {
	    	synapp.server = new Server();
	    	ServerConnector httpConnector = new ServerConnector(synapp.server);
	        httpConnector.setHost("0.0.0.0");
	        httpConnector.setPort(port);
	        httpConnector.setIdleTimeout(5000);
	        synapp.server.addConnector(httpConnector);
	    }
	    else
	    	synapp.server = new Server(new InetSocketAddress(bindIp, port));
	
	    InetAddress inet = InetAddress.getLocalHost();
	    String addrhost  = inet.getHostAddress();
		synapp.syngleton.jserv = String.format("http://%s:%s", bindIp == null ? addrhost : bindIp, port);
	
	    synapp.syngleton.syndomanagers = new HashMap<String, SynDomanager>();
	    
	    return synapp;
	}

	public Syngleton syngleton() {
		return syngleton;
	}	

}
