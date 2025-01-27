package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isblank;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import javax.servlet.annotation.WebServlet;

import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import io.odysz.common.Utils;
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
import io.odysz.semantic.syn.DBSynTransBuilder;
import io.odysz.semantic.syn.SyncUser;
import io.odysz.semantic.syn.registry.Syntities;
import io.odysz.semantic.syn.registry.SyntityReg;
import io.odysz.semantics.x.SemanticException;
import io.oz.jserv.docs.syn.DocUser;
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

	Server server;

	ServletContextHandler schandler;

	final Syngleton syngleton;
	public Syngleton syngleton() { return syngleton; }	

	String jserv;
	public String jserv() { return jserv; }

	public SynotierJettyApp(SynodeConfig cfg) throws Exception {
		syngleton = new Syngleton(cfg, null);
	}

	/**
	 * Create an application instance working as a synode tier.
	 * @param urlpath e. g. jserv-album
	 * @param syntity_json e. g. $VOLUME_HOME/syntity.json
	 * @param admin 
	 * @throws Exception
	 */
	public static SynotierJettyApp createSyndoctierApp(SynodeConfig cfg, SyncUser admin,
			String urlpath, String webinf, String config_xml, String syntity_json) throws Exception {

		String synid  = cfg.synode();
		String sync = cfg.synconn;

		SynotierJettyApp synapp = SynotierJettyApp
						.instanserver(webinf, cfg, config_xml, "0.0.0.0", 8964)
						.loadomains(cfg, new DocUser(admin));

		Utils.logi("------------ Starting %s ... --------------", synid);
	
		Syntities regists = Syntities.load(webinf, syntity_json, 
				(synreg) -> {
					throw new SemanticException("TODO %s (configure an entity table with meta type)", synreg.table);
				});	

		DBSynTransBuilder.synSemantics(new DATranscxt(sync), sync, synid, regists);

		return registerPorts(synapp, urlpath, cfg.synconn,
				new AnSession(), new AnQuery(), new AnUpdate(),
				new HeartLink())
			.addDocServPort(cfg, regists.syntities)
			.addSynodetier(synapp, cfg)
			;
	}

	private SynotierJettyApp addSynodetier(SynotierJettyApp synapp, SynodeConfig cfg)
			throws Exception {
		SynDomanager domanger = synapp.syngleton.domanager(cfg.domain);
		ExpSynodetier syncer = new ExpSynodetier(domanger)
								.syncIn(cfg.syncIns,
									(c, r, args) -> Utils.warn("[Syn-worker ERROR] code: %s, msg: %s", r));
		addServPort(syncer);
		return this;
	}

	SynotierJettyApp loadomains(SynodeConfig cfg, DocUser admin) throws Exception {
		syngleton.loadomains(cfg, admin);
		return this;
	}

	public SynotierJettyApp addDocServPort(SynodeConfig cfg, ArrayList<SyntityReg> syntities) throws Exception {
		SynDomanager domanger = syngleton.domanager(cfg.domain);

		addServPort(new ExpDoctier(domanger, null)
				.registSynEvent(cfg, syntities));
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
	 * @param sysconn
	 * @param servports
	 * @return Jetty server, the {@link SynotierJettyApp}
	 * @throws Exception
	 */
	@SafeVarargs
	static public <T extends ServPort<? extends AnsonBody>> SynotierJettyApp registerPorts(
			SynotierJettyApp synapp, String urlpath, String sysconn, T ... servports) throws Exception {

        synapp.schandler = new ServletContextHandler(synapp.server, urlpath);
        for (T t : servports) {
        	synapp.registerServlets(synapp.schandler, t.trb(new DATranscxt(sysconn)));
        }

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

	/**
	 * Create a Jetty instance at local host, jserv-root
	 * for accessing online Synodes.
	 * 
	 * <p>Debug Tip:</p> list all local tcp listening ports:
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
	
	    SynotierJettyApp synapp = new SynotierJettyApp(cfg);

		Syngleton.defltScxt = new DATranscxt(cfg.sysconn);
	
	    InetAddress inet = InetAddress.getLocalHost();
	    String addrhost  = inet.getHostAddress(); // this result is different between Windows and Linux

	    if (isblank(bindIp) || eq("*", bindIp)) {
	    	synapp.server = new Server();
	    	ServerConnector httpConnector = new ServerConnector(synapp.server);
	        httpConnector.setHost(addrhost);
	        httpConnector.setPort(port);
	        httpConnector.setIdleTimeout(5000);
	        synapp.server.addConnector(httpConnector);
	    }
	    else
	    	synapp.server = new Server(new InetSocketAddress(bindIp, port));
	
//		synapp.syngleton.jserv = String.format("%s://%s:%s",
//				cfg.https ? "https" : "http",
//				bindIp == null ? addrhost : bindIp, port);
	
	    return synapp;
	}

	public void print() {
		Utils.logi("Synode %s: %s", syngleton.synode(), jserv);
	}
}
