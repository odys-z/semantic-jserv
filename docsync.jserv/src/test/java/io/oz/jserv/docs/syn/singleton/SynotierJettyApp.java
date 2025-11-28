package io.oz.jserv.docs.syn.singleton;

import static io.odysz.common.LangExt._0;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.ifnull;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.LangExt.mustnonull;
import static io.odysz.common.Utils.warn;
import static io.odysz.common.Utils.warnT;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;

import javax.servlet.DispatcherType;
import javax.servlet.annotation.WebServlet;

import org.eclipse.jetty.ee8.servlet.FilterHolder;
import org.eclipse.jetty.ee8.servlet.FilterMapping;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import io.odysz.common.Configs;
import io.odysz.common.EnvPath;
import io.odysz.common.FilenameUtils;
import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jserv.echo.Echo;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.ServPort.PrintstreamProvider;
import io.odysz.semantic.jserv.R.AnQuery;
import io.odysz.semantic.jserv.U.AnUpdate;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.HeartLink;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.album.peer.SynDocollPort;
import io.oz.jserv.docs.syn.DocUser;
import io.oz.jserv.docs.syn.ExpDoctier;
import io.oz.jserv.docs.syn.ExpSynodetier;
import io.oz.jserv.docs.syn.SynDomanager;
import io.oz.jserv.docs.syn.T_SynDomanager;
import io.oz.syn.DBSynTransBuilder;
import io.oz.syn.SyncUser;
import io.oz.syn.registry.SynodeConfig;
import io.oz.syn.registry.Syntities;
import io.oz.syn.registry.SyntityReg;
import io.oz.syn.registry.YellowPages;

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
	static final String servpath = "jserv-album";
	
	public static final String webinf = "./src/test/res/WEB-INF";
	public static final String config_xml = "config.xml";
	public static final String settings_json = "settings.json";

	public static final String zsu = "zsu";
	public static final String ura = "URA";

	public static final String syntity_json = "syntity.json";
	public static final String clientUri = "/jetty";
	public static final String testDir   = "./src/test/res/";
	public static final String volumeDir = "./src/test/res/volume";

	
	final Syngleton syngleton;

	Server server;

	ServletContextHandler schandler;
	public Syngleton syngleton() { return syngleton; }	

	public String jserv() {
		return this.syngleton.myjserv();
	}

	public static void jvmStart(String[] _args) {
	}
	
	/**
	 * Response to Windows SCM stop commands.
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void jvmStop(String[] _args) {
	}

	public static void main(String[] args) {
		SynotierJettyApp app = _main(args);
		if (app.server != null)
			try {
				app.server.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
	}

	/**
	 * @param args [0] settings.xml
	 * @throws Exception
	 */
	public static SynotierJettyApp _main(String[] args, boolean ...break_exchagne) {
		try {
			// For Eclipse's running as Java Application
			// E. g. -DWEB-INF=src/main/webapp/WEB-INF
			String srcwebinf = ifnull(System.getProperty("WEB-INF"), webinf);
			JProtocol.setup(servpath, SynDocollPort.docoll);

			AppSettings settings = AppSettings.checkInstall(servpath,
					srcwebinf, config_xml, _0(args, settings_json), true);

			SynotierJettyApp app = boot(srcwebinf, config_xml, settings, _0(break_exchagne, false))
					.afterboot()
					.print("\n. . . . . . . . Synodtier Jetty Application is running . . . . . . . ");

			return app;
		} catch (Exception e) {
			e.printStackTrace();
			
			warn("Fatal errors there. The process is stopped.");
			System.exit(-1);
			return null;
		}
	}

	/**
	 * Bring up thread to check network changes and expose jserv locally.
	 * @return this
	 */
	SynotierJettyApp afterboot() {
		AppSettings settings = syngleton.settings;
		try {
			SynDomanager mngr = syngleton.domanager(syngleton.syncfg.domain);
			mngr.ipChangeHandler(len(settings.startHandler) == 0
								? null
								: ((ISynodeLocalExposer)Class
									.forName(settings.startHandler[0])
									.getDeclaredConstructor()
									.newInstance()));
		} catch (Exception e) {
			warnT(new Object(){}, "Exposing local resources failed!");
			e.printStackTrace();
		}
		return this;
	}
	
	static SynotierJettyApp boot(String webinf, String config_xml, AppSettings settings, boolean must_break,
			PrintstreamProvider ... oe) throws Exception {

		Utils.logi("%s : %s", settings.vol_name, EnvPath.getEnv(settings.vol_name));

		Configs.init(webinf);
		Connects.init(webinf);
		Syngleton.appName = ifnull(Configs.getCfg("app-name"), "Portfolio 0.7");

		mustnonull(settings.rootkey, f(
				"Rootkey cannot be null for starting App. settings:\n%s", 
				settings.toBlock()));

		String $vol_home = "$" + settings.vol_name;
		YellowPages.load(FilenameUtils.concat(new File(".").getAbsolutePath(),
				webinf, EnvPath.replaceEnv($vol_home)));
		SynodeConfig cfg = YellowPages.synconfig();
	
		Syngleton.defltScxt = new DATranscxt(cfg.sysconn);
		AppSettings.rebootdb(cfg, webinf, $vol_home, config_xml, settings.rootkey);

		// updating configuration that's allowed to be re-configured at each time of booting
		AppSettings.updateOrgConfig(cfg, settings);
		
		return createSyndoctierApp(cfg, settings,
					((ArrayList<SyncUser>) YellowPages.robots()).get(0),
					webinf, config_xml, f("%s/%s", $vol_home, "syntity.json"), must_break)

				.start(isNull(oe) ? () -> System.out : oe[0],
					  !isNull(oe) && oe.length > 1 ? oe[1] : () -> System.err)
				;
	}

	/**
	 * @param cfg
	 * @param admin 
	 * @throws Exception
	 */
	public SynotierJettyApp(SynodeConfig cfg, SyncUser admin, AppSettings settings) throws Exception {
		syngleton = new Syngleton(cfg, admin, settings);
	}

	/**
	 * Create an application instance working as a synode tier,
	 * by loading configurations, registering ServPorts.
	 * @param urlpath e. g. jserv-album
	 * @param syntity_json e. g. $VOLUME_HOME/syntity.json
	 * @param admin 
	 * @param requires {@link T_SynDomanager} must break break exchange, and resume at break points.
	 * @throws Exception
	 */
	public static SynotierJettyApp createSyndoctierApp(SynodeConfig cfg, AppSettings settings,
			SyncUser admin, String webinf, String config_xml, String syntity_json, boolean must_break) throws Exception {

		String synid  = cfg.synode();
		String sync = cfg.synconn;
		DocUser docusr = new DocUser(admin);

		SynotierJettyApp synapp = SynotierJettyApp
						.instanserver(webinf, cfg, admin, settings, config_xml)
						.loadomains(cfg, docusr);

		Utils.logi("------------ Starting %s ... --------------", synid);
	
		Syntities regists = Syntities.load(webinf, syntity_json, 
				(synreg) -> {
					throw new SemanticException("TODO %s (configure an entity table with meta type)", synreg.table);
				});	

		DBSynTransBuilder.synSemantics(new DATranscxt(sync), sync, synid, regists);

		return registerPorts(synapp, cfg.synconn,
				AnSession.init(cfg.sysconn), new AnQuery(), new AnUpdate(),
				new Echo(),
				new HeartLink())
			.addSynodetier(synapp, cfg, settings, docusr, must_break)
			.addDocServPort(cfg, regists.syntities)
			.allowCors(synapp.schandler)
			;
	}

	private SynotierJettyApp addSynodetier(SynotierJettyApp synapp, SynodeConfig cfg,
			AppSettings settings, DocUser admin, boolean must_break) throws Exception {
		SynDomanager domanger = synapp.syngleton.domanager(cfg.domain);
		domanger = (SynDomanager) new T_SynDomanager(synapp.syngleton, cfg, settings, must_break)
					.admin(admin.deviceId(cfg.synode()))
					.loadomainx();
		ExpSynodetier syncer = new ExpSynodetier(domanger)
								.syncIn(cfg.syncIns,
									(c, r, args) -> Utils.warn("[Syn-worker ERROR] code: %s, msg: %s", r));
		synapp.syngleton.syndomanagers.put(cfg.domain, domanger);
		addServPort(syncer);
		return this;
	}

	SynotierJettyApp loadomains(SynodeConfig cfg, DocUser admin) throws Exception {
		syngleton.loadomains(cfg, admin);
		return this;
	}

	public SynotierJettyApp addDocServPort(SynodeConfig cfg, ArrayList<SyntityReg> syntities) throws Exception {
		T_SynDomanager domanger = (T_SynDomanager) syngleton.domanager(cfg.domain);

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
			SynotierJettyApp synapp, String sysconn, T ... servports) throws Exception {

        synapp.schandler = new ServletContextHandler(synapp.server, "/" + servpath);
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
		mustnonull(info, f("ServPort %s must define @WebServlet", t.getClass().getName()));
		for (String pattern : info.urlPatterns()) {
			context.addServlet(new ServletHolder(t), pattern);
		}
		
		return this;
	}

	public SynotierJettyApp addServPort(ServPort<?> p) {
       	registerServlets(schandler, p);
       	return this;
	}
	
	/**
	 * A stub for implementing SCM stop command. This won't work currently as the method
	 * is called in a different process.
	 * 
	 * @throws Exception
	 */
	public void stop() throws Exception {
		if (server != null)
			server.stop();
	}
	
	static SynotierJettyApp instanserver(String configPath, SynodeConfig cfg, SyncUser admin,
			AppSettings settings, String config_xml) throws Exception {
	
	    SynotierJettyApp synapp = new SynotierJettyApp(cfg, admin, settings);
		Syngleton.defltScxt = new DATranscxt(cfg.sysconn);
    	synapp.server = new Server();

    	ServerConnector jconn = new ServerConnector(synapp.server);
    	jconn.setHost("0.0.0.0");
    	jconn.setPort(settings.port);
    	jconn.setIdleTimeout((long) (settings.connIdleSnds == 0.0 ? 30000 : settings.connIdleSnds * 1000));
    	synapp.server.addConnector(jconn);

	    return synapp;
	}

	private SynotierJettyApp allowCors(ServletContextHandler context) {
		CrossOriginFilter.synode(Syngleton.appName, syngleton().synode());

		FilterHolder holder = new FilterHolder(CrossOriginFilter.class);
		holder.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
		holder.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
		holder.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,POST,HEAD");
		holder.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin");
		holder.setName("cross-origin");
		FilterMapping fm = new FilterMapping();
		fm.setFilterName("cross-origin");
		fm.setPathSpec("*");
		
		context.addFilter(holder, "/*", EnumSet.of(DispatcherType.REQUEST));
		
		return this;
	}

	public SynotierJettyApp print(String... msg) {
		String qr = f("%s\n%s", syngleton.synode(), syngleton.settings.jserv(syngleton.synode()));
		Utils.logi("%s\nSynode %s", _0(msg, ""), qr);
		return this;
	}
}
