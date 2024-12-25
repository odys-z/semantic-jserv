package io.oz.syntier.serv;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.mustnonull;
import java.io.File;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;

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
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.HeartLink;
import io.odysz.semantic.syn.DBSynTransBuilder;
import io.odysz.semantic.syn.SyncUser;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.syn.registry.Syntities;
import io.odysz.semantic.syn.registry.SyntityReg;
import io.odysz.semantics.x.SemanticException;
import io.oz.jserv.docs.syn.DocUser;
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
	 * @param args
	 * @throws Exception
	 */
	public static void main(String[] args) throws Exception {
		main_("$VOLUME_HOME", args);
	}

	/**
	 * Test API equivalent of {@link #main(String[])}.
	 * @param _vol_home environment variable name for volume path, e. g. "VOLUME_HOME", without '$'
	 * @param args cli args, e. g. -ip <bind-ip>, where the binding IP will override the dictionary.json.
	 * @return  Jetty application
	 * @throws Exception
	 */
	public static SynotierJettyApp main_(String _vol_home, String[] args, PrintstreamProvider ... oe)
			throws Exception {

		CliArgs cli = new CliArgs();
		CmdLineParser parser = new CmdLineParser(cli);
		parser.parseArgument(args);

		mustnonull(cli.ip, "JUnit args for IDE bug: -ea -Dip=127.0.0.1 -Dinstall-key=### -DWEBROOT_PRV=#.#.#.# -DWEBROOT_HUB=#.#.#.#, -Djservs=\"X:127.0.0.1:8964 Y:127.0.0.1:8965\"");
		
		Utils.logi("%s : %s", _vol_home, System.getProperty(_vol_home));

		Configs.init(webinf);
		Connects.init(webinf);

		String $vol_home = "$" + _vol_home;
		
		YellowPages.load($vol_home);
		SynodeConfig cfg = YellowPages.synconfig();
		if (cfg.mode == null)
			cfg.mode = SynodeMode.peer;
		
		String[] ip_urlpath = new String[] {isblank(cli.ip) ? cfg.localhost : cli.ip, cli.urlpath};

		if (!isblank(cli.installkey)) {
			mustnonull(cli.jservs);

			YellowPages.load(FilenameUtils.concat(
					new File(".").getAbsolutePath(),
					webinf,
					EnvPath.replaceEnv($vol_home)));

			AppSettings.setupdb(cfg, webinf, $vol_home, "config.xml", cli.installkey, cli.jservs);
		}
		
		return createSyndoctierApp( cfg.ip(ip_urlpath[0]),
									((ArrayList<SyncUser>) YellowPages.robots()).get(0),
									ip_urlpath[1], webinf, "config.xml",
									f("%s/%s", $vol_home, "syntity.json"))

				.start(isNull(oe) ? () -> System.out : oe[0],
					  !isNull(oe) && oe.length > 1 ? oe[1] : () -> System.err)
				;
	}

	public SynotierJettyApp(SynodeConfig cfg) throws Exception {
		syngleton = new Syngleton(cfg);
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
						.instanserver(webinf, cfg, config_xml, cfg.localhost, cfg.port)
						.loadomains(cfg, new DocUser(admin));

		Utils.logi("------------ Starting %s ... --------------", synid);
	
		Syntities regists = Syntities.load(webinf, syntity_json, 
				(synreg) -> {
					throw new SemanticException("TODO %s (configure an entity table with meta type)", synreg.table);
				});	

		DBSynTransBuilder.synSemantics(new DATranscxt(sync), sync, synid, regists);

		return registerPorts(synapp, urlpath, cfg.synconn,
				new AnSession(), new AnQuery(), new AnUpdate(), new HeartLink())
			.addDocServPort(cfg, regists.syntities)
			.addSynodetier(synapp, cfg)
			;
	}

	private SynotierJettyApp addSynodetier(SynotierJettyApp synapp, SynodeConfig cfg)
			throws Exception {
		SynDomanager domanger = synapp.syngleton.domanager(cfg.domain);
		ExpSynodetier syncer = new ExpSynodetier(domanger)
								.syncIn(cfg.syncIns);
		addServPort(syncer);
		return this;
	}

	SynotierJettyApp loadomains(SynodeConfig cfg, DocUser admin) throws Exception {
		syngleton.loadomains(cfg, admin);
		return this;
	}

	public SynotierJettyApp addDocServPort(SynodeConfig cfg, ArrayList<SyntityReg> syntities) throws Exception {
		SynDomanager domanger = syngleton.domanager(cfg.domain);

		addServPort(new ExpDoctier(domanger)
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
	
		synapp.syngleton.jserv = String.format("%s:%s", bindIp == null ? addrhost : bindIp, port);
	
	    return synapp;
	}

	public void print() {
		Utils.logi("Synode %s: %s", syngleton.synode(), syngleton.jserv);
	}

}
