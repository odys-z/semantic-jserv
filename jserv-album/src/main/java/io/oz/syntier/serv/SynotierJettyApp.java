package io.oz.syntier.serv;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.HashMap;

import javax.servlet.annotation.WebServlet;

import org.apache.commons.io_odysz.FilenameUtils;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.kohsuke.args4j.CmdLineParser;

import io.odysz.common.Configs;
import io.odysz.common.EnvPath;
import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.ServPort.PrintstreamProvider;
import io.odysz.semantic.jserv.R.AnQuery;
import io.odysz.semantic.jserv.U.AnUpdate;
import io.odysz.semantic.jserv.echo.Echo;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.HeartLink;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.syn.DBSynTransBuilder;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.syn.registry.Syntities;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.ExpDoctier;
import io.oz.jserv.docs.syn.ExpSynodetier;
import io.oz.jserv.docs.syn.SynDomanager;
import io.oz.jserv.docs.syn.singleton.AppSettings;
import io.oz.jserv.docs.syn.singleton.Syngleton;
import io.oz.syn.SynodeConfig;
import io.oz.syn.YellowPages;

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

	public static final String webinf = "./src/main/webapp/WEB-INF";

	final Syngleton syngleton;

	Server server;

	ServletContextHandler schandler;
	public Syngleton syngleton() { return syngleton; }	
	public String jserv() { return syngleton.jserv; }

	public SynotierJettyApp(SynodeConfig cfg) throws Exception {
		syngleton = new Syngleton(cfg);
	}

	/**
	 * Eclipse run configuration example:
	 * <pre>Run - Run Configurations - Arguments
	 * Program Arguments
	 * 192.168.0.100 8964
	 * 
	 * VM Arguments
	 * -DVOLUME_HOME=../volume
	 * </pre>
	 * volume home = relative path to web-inf.
	 * 
	 * @param
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		CliArgs cli = new CliArgs();
		CmdLineParser parser = new CmdLineParser(cli);
		parser.parseArgument(args);

        Utils.logi("VOLUME_HOME : %s", System.getProperty("VOLUME_HOME"));

		Configs.init(webinf);
		Connects.init(webinf);

		if (!isblank(cli.installkey)) {
			YellowPages.load(FilenameUtils.concat(
					new File(".").getAbsolutePath(),
					webinf,
					EnvPath.replaceEnv("$VOLUME_HOME")));
			SynodeConfig cfg = YellowPages.synconfig();
			AppSettings.setupdb(cfg, webinf, "$VOLUME_HOME", "config.xml", cli.installkey);
			createStartSyndocTier("$VOLUME_HOME", webinf, "syntity.json", cli.installkey, cli.ip, cli.port);
		}
		else {
			createStartSyndocTier("$VOLUME_HOME", webinf, "syntity.json", cli.rootkey, cli.ip, cli.port);
		}
	}

	/**
	 * Start Jetty and allow uid to login.
	 * 
	 * <p>Test equivolant: {@code io.oz.jserv.docs.syn.singleton.CreateSyndocTierTest#createStartSyndocTierTest(...)}</p>
	 * @param conn
	 * @param port
	 * @return JettyHelper
	 * @throws IOException
	 * @throws Exception
	 */
	private static SynotierJettyApp createStartSyndocTier(String envolume, String webinf, String syntity_json,
			String rootkey, String ip, int port, PrintstreamProvider ... oe) throws IOException, Exception {

		String volpath = FilenameUtils.concat(webinf, EnvPath.replaceEnv(envolume));
		YellowPages.load(volpath);
		SynodeConfig cfg = YellowPages.synconfig();
		cfg.mode = SynodeMode.peer;
		
		AppSettings.setupdb(cfg, webinf, envolume, "config.xml", rootkey);

		SynotierJettyApp app = SynotierJettyApp
				.instanserver(webinf, cfg, "config.xml", ip, port);
		app.syngleton.loadomains(cfg);

		return SynotierJettyApp
			.registerPorts(app, cfg.sysconn,
				new AnSession(), new AnQuery(), new HeartLink(),
				new Echo(true))
			.addDocServPort(cfg.domain, webinf, syntity_json)
			.start(isNull(oe) ? () -> System.out : oe[0], !isNull(oe) && oe.length > 1 ? oe[1] : () -> System.err)
			;
	}
	
	/**
	 * Start a Jetty app with system print stream for logging.
	 * 
	 * @return the Jetty App, with a servlet server.
	 * @throws Exception
	 */
	public static SynotierJettyApp startSyndoctier(SynodeConfig cfg,
			String webinf, String cfg_xml, String syntity_json) throws Exception {

		return SynotierJettyApp 
			.createSyndoctierApp(cfg, webinf, cfg_xml, syntity_json)
			.start(() -> System.out, () -> System.err)
			;
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
	public static SynotierJettyApp createSyndoctierApp(SynodeConfig cfg,
			String webinf, String config_xml, String syntity_json) throws Exception {

		String synid  = cfg.synode();
		String sync = cfg.synconn;

		SynotierJettyApp synapp = SynotierJettyApp
						.instanserver(webinf, cfg, config_xml, cfg.localhost, cfg.port)
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
		syngleton.loadomains(cfg);
		return this;
	}

	public SynotierJettyApp addDocServPort(String domain, String cfgroot, String syntity_json) throws Exception {
		SynDomanager domanger = syngleton.domanager(domain);

		addServPort(new ExpDoctier(domanger)
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

	public void updateJservs(SynodeMeta synm, SynodeConfig cfg, String domain)
			throws TransException, SQLException {
		syngleton.updatePeerJservs(synm, cfg, domain);
	}

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
	
	    SynotierJettyApp synapp = new SynotierJettyApp(cfg);

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

}
