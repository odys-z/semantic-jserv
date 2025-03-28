package io.oz.syntier.serv;

import static io.odysz.common.LangExt._0;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.ifnull;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.mustnonull;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.warn;

import java.io.File;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.EnumSet;

import javax.servlet.DispatcherType;
import javax.servlet.annotation.WebServlet;

import org.apache.commons.io_odysz.FilenameUtils;
import org.eclipse.jetty.ee8.servlet.FilterHolder;
import org.eclipse.jetty.ee8.servlet.FilterMapping;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;

import com.google.zxing.WriterException;

import io.odysz.common.Configs;
import io.odysz.common.EnvPath;
import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.echo.Echo;
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
import io.oz.album.helpers.QrProps;
import io.oz.album.helpers.QrTerminal;
import io.oz.album.peer.SynDocollPort;
import io.oz.jserv.docs.syn.DocUser;
import io.oz.jserv.docs.syn.ExpDoctier;
import io.oz.jserv.docs.syn.ExpSynodetier;
import io.oz.jserv.docs.syn.SynDomanager;
import io.oz.jserv.docs.syn.singleton.AppSettings;
import io.oz.jserv.docs.syn.singleton.CrossOriginFilter;
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
	public static final String servpath = "/jserv-album";
	
	public static final String webinf = "WEB-INF";
	public static final String config_xml = "config.xml";
	public static final String settings_json = "settings.json";

	final Syngleton syngleton;

	Server server;

	ServletContextHandler schandler;
	public Syngleton syngleton() { return syngleton; }	

	String jserv;
	public String jserv() { return jserv; }
	public SynotierJettyApp jserv(String url) {
		jserv = url;
		return this;
	}

	/**
	 * @param args [0] settings.xml
	 * @throws Exception
	 */
	public static void main(String[] args) {
		try {
			// For Eclipse's running as Java Application
			// E. g. -DWEB-INF=src/main/webapp/WEB-INF
			String srcwebinf = ifnull(System.getProperty("WEB-INF"), webinf);

			String jserv = AppSettings.checkInstall(servpath, srcwebinf, config_xml, settings_json, false);

			boot(srcwebinf, config_xml, _0(args, settings_json))
			.jserv(jserv)
			.print("\n. . . . . . . . Synodtier Jetty Application is running . . . . . . . ");
		} catch (Exception e) {
			e.printStackTrace();
			
			warn("Fatal errors there. The process is stopped.");
			System.exit(-1);
		}
	}
	
	/**
	 * Response to SCM stop commands. This is a stub and won't work as
	 * the method is called in different process than the main process.
	 * 
	 * @param args
	 * @throws Exception
	 */
	public static void stop(String[] args) throws Exception {
		// if (server != null) server.stop();
	}
	
	static SynotierJettyApp boot(String webinf, String config_xml, String settings_json,
			PrintstreamProvider ... oe) throws Exception {

		logi("Loading settings: %s", settings_json);
		AppSettings pset = AppSettings.load(webinf, settings_json);
		
		String p = pset.volume;
		if (!new File(p).exists()) // absolute
			p = new File(FilenameUtils.concat(webinf, pset.volume)).getAbsolutePath();
		if (!new File(p).exists()) { // relative
			warn("Can't find volume: settings.json: %s\nvolume: %s\nconfig-root: %s", pset.json, pset.volume, webinf);
			throw new SemanticException("Can't find volume with settings.json/[volume] in absolute and relative to WEB-INF.", pset.volume);
		}

		System.setProperty(pset.vol_name, p);
		Utils.logi("%s:\n%s\n%s", settings_json, p, pset.toString());
		
		return boot(webinf, config_xml, pset, oe);
	}

	static SynotierJettyApp boot(String webinf, String config_xml, AppSettings settings,
			PrintstreamProvider ... oe) throws Exception {

		Utils.logi("%s : %s", settings.vol_name, System.getProperty(settings.vol_name));

		Configs.init(webinf);
		Connects.init(webinf);
		Syngleton.appName = ifnull(Configs.getCfg("app-name"), "Portfolio 0.7");

		String $vol_home = "$" + settings.vol_name;
		
		YellowPages.load($vol_home);
		SynodeConfig cfg = YellowPages.synconfig();
		if (cfg.mode == null)
			cfg.mode = SynodeMode.peer;
		
		mustnonull(settings.rootkey, f(
				"Rootkey cannot be null for starting App. settings:\n%s", 
				settings.toBlock()));

		YellowPages.load(FilenameUtils.concat(new File(".").getAbsolutePath(),
				webinf, EnvPath.replaceEnv($vol_home)));

		Syngleton.defltScxt = new DATranscxt(cfg.sysconn);
		AppSettings.rebootdb(cfg, webinf, $vol_home, config_xml, settings.rootkey);

		// updating configuration that's allowed to be re-configured at each time of booting
		AppSettings.updateOrgConfig(cfg, settings);
		
		return createSyndoctierApp(cfg, settings,
									((ArrayList<SyncUser>) YellowPages.robots()).get(0),
									webinf, config_xml,
									f("%s/%s", $vol_home, "syntity.json"))

				.start(isNull(oe) ? () -> System.out : oe[0],
					  !isNull(oe) && oe.length > 1 ? oe[1] : () -> System.err)
				;
	}

	/**
	 * @param cfg
	 * @throws Exception
	 */
	public SynotierJettyApp(SynodeConfig cfg, AppSettings settings) throws Exception {
		syngleton = new Syngleton(cfg, settings);
	}

	/**
	 * Create an application instance working as a synode tier.
	 * @param urlpath e. g. jserv-album
	 * @param syntity_json e. g. $VOLUME_HOME/syntity.json
	 * @param admin 
	 * @throws Exception
	 */
	public static SynotierJettyApp createSyndoctierApp(SynodeConfig cfg, AppSettings settings,
			SyncUser admin, String webinf, String config_xml, String syntity_json) throws Exception {

		String synid  = cfg.synode();
		String sync = cfg.synconn;

		SynotierJettyApp synapp = SynotierJettyApp
						.instanserver(webinf, cfg, settings, config_xml)
						.loadomains(cfg, new DocUser(admin));

		Utils.logi("------------ Starting %s ... --------------", synid);
	
		Syntities regists = Syntities.load(webinf, syntity_json, 
				(synreg) -> {
					throw new SemanticException("TODO %s (configure an entity table with meta type)", synreg.table);
				});	

		DBSynTransBuilder.synSemantics(new DATranscxt(sync), sync, synid, regists);

		return registerPorts(synapp, cfg.synconn,
				new AnSession(), new AnQuery(), new AnUpdate(),
				new Echo(), new SynDocollects(cfg.sysconn, synapp.syngleton.domanager(cfg.domain)),
				new HeartLink())
			.addDocServPort(cfg, regists.syntities)
			.addSynodetier(synapp, cfg)
			.allowCors(synapp.schandler)
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

		addServPort(new ExpDoctier(domanger, new DocreateHandler())
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

        synapp.schandler = new ServletContextHandler(synapp.server, servpath);
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
	
	static SynotierJettyApp instanserver(String configPath, SynodeConfig cfg, AppSettings settings,
			String config_xml) throws Exception {
	
	    AnsonMsg.understandPorts(SynDocollPort.docoll);
	
	    SynotierJettyApp synapp = new SynotierJettyApp(cfg, settings);

		Syngleton.defltScxt = new DATranscxt(cfg.sysconn);
	
    	synapp.server = new Server(new InetSocketAddress("0.0.0.0", settings.port));

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
		try {
			String qr = f("%s\n%s", syngleton.synode(), jserv);
			Utils.logi("%s\nSynode %s", _0(msg, ""), qr);
			QrTerminal.print(qr, new QrProps(true));
		} catch (WriterException e) {
			e.printStackTrace();
		}
		return this;
	}
}
