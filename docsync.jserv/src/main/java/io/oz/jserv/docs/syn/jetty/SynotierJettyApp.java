package io.oz.jserv.docs.syn.jetty;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.Utils.loadTxt;
import static io.odysz.common.Utils.logi;
import static io.oz.jserv.docs.syn.ExpSynodetier.setupDomanagers;
import static io.odysz.semantic.meta.SemanticTableMeta.setupSqliTables;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.ArrayList;
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
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantic.meta.PeersMeta;
import io.odysz.semantic.meta.SynChangeMeta;
import io.odysz.semantic.meta.SynSessionMeta;
import io.odysz.semantic.meta.SynSubsMeta;
import io.odysz.semantic.meta.SynchangeBuffMeta;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.meta.SyntityMeta;
import io.odysz.semantic.syn.DBSyntableBuilder;
import io.odysz.semantic.syn.SyncRobot;
import io.odysz.semantic.syn.Synode;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.ExpDoctier;
import io.oz.jserv.docs.syn.ExpSynodetier;
import io.oz.jserv.docs.syn.SynDomanager;
import io.oz.jserv.docs.syn.SynDomanager.OnDomainUpdate;
import io.oz.jserv.docs.syn.Syngleton;
import io.oz.synode.jclient.SynodeConfig;
import io.oz.synode.jclient.YellowPages;

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
	static DATranscxt syst;

	Server server;

	ServletContextHandler schandler;

	String jserv;
	String conn0;
	String synode;
	DATranscxt t0; 
	SyncRobot robot;

	public SynotierJettyApp(String synid) {
		this.synode = synid;
	}

	public String jserv() { return jserv; }

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
		try {
			String bind = args[0];
			int    port = Integer.valueOf(args[1]);
			String org     = args[2];
			String domain  = args[3];
			String webinf  = args.length > 4 ? args[4] : "WEB-INF";
			String cfgxml  = !isNull(args) && args.length > 5 ? args[5] : "config.xml";
			String synconn = !isNull(args) && args.length > 6 ? args[6] : "sqlite-main";
			String robid   = !isNull(args) && args.length > 7 ? args[7] : System.getenv("robot-id");
		
			Utils.logi("Starting Synodetier at port %s, org %s, domain %s, configure file %s, conn %s",
					port, org, domain, cfgxml, synconn);

			Configs.init(webinf);
			Connects.init(webinf);

			SynotierJettyApp app = createSyndoctierApp(
								synconn, cfgxml, bind, port, webinf, domain,
								YellowPages.getRobot(robid))
								.start(() -> System.out, () -> System.err);

			Utils.pause(String.format(
					"[%s] started at port %s, org %s, domain %s, configure file %s, conn %s",
					"SynotierJettyApp", port, org, domain, cfgxml, synconn));
			
			app.stop();
		}
		catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
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
	public static SynotierJettyApp createSyndoctierApp(String serv_conn,
			String config_xml, String bindIp, int port, String webinf,
			String domain, SyncRobot robot) throws Exception {

		Configs.init(webinf, config_xml);
		
		String synid  = Configs.getCfg(Configs.keys.synode);
		robot.deviceId(synid);

		DATranscxt.initConfigs(serv_conn, DATranscxt.loadSemantics(serv_conn),
		        (con) -> new DBSyntableBuilder.SynmanticsMap(synid, con));

		Utils.logi("------------ Starting %s ... --------------", synid);
	
		HashMap<String,SynDomanager> domains = setupDomanagers(robot.orgId(), domain, synid,
				serv_conn, SynodeMode.peer, Connects.getDebug(serv_conn));
	
		ExpDoctier doctier  = new ExpDoctier(synid, serv_conn)
							.startier(robot.orgId(), domain, SynodeMode.peer)
							.domains(domains);
		ExpSynodetier syner = new ExpSynodetier(robot.orgId(), domain, synid, serv_conn, SynodeMode.peer)
							.domains(domains);
		
		SynotierJettyApp synapp = instanserver(webinf, serv_conn, config_xml, bindIp, port, robot);
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
	 * Tip: list all local tcp listening ports:
	 * sudo netstat -ntlp
	 * see https://askubuntu.com/a/328293
	 * 
	 * @param configPath
	 * @param conn0
	 * @param configxml
	 * @param bindIp
	 * @param port
	 * @param robotInf information for creating robot, i. e. the user identity for login to peer synodes.
	 * @return Jetty App
	 * @throws Exception
	 */
	public static SynotierJettyApp instanserver(String configPath, String conn0, String configxml,
			String bindIp, int port, SyncRobot robt) throws Exception {

	    AnsonMsg.understandPorts(Port.syntier);
	
		String synid = Syngleton.initSynodetier(configxml, conn0, ".", configPath, "ABCDEF0123456789");

	    SynotierJettyApp synapp = new SynotierJettyApp(synid);

	    synapp.conn0 = conn0;
		synapp.t0 = new DATranscxt(conn0);
		synapp.robot = robt;

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
		synapp.jserv = String.format("http://%s:%s", bindIp == null ? addrhost : bindIp, port);
	
	    synapp.synodetiers = new HashMap<String, HashMap<String, SynDomanager>>();
	    
	    return synapp;
	}

	/**
	 * { servlet-url-pattern: { domain: domanager } }, only instance of {@link ExpSynodetier},<br>
	 * e. g. { docs.sync: { zsu: { new SnyDomanger(x, y) } }
	 */
	public HashMap<String, HashMap<String, SynDomanager>> synodetiers;
	
	/**
	 * Last (bug?) url pattern (key in {@link #synodetiers}) of {@link ExpSynodetier}.
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

	public SynotierJettyApp loadDomains(SynodeMeta synm, SynodeMode synmod) throws Exception {
		if (synodetiers == null)
			synodetiers = new HashMap<String, HashMap<String, SynDomanager>>();

		AnResultset rs = (AnResultset) t0
				.select(synm.tbl)
				.groupby(synm.domain)
				.groupby(synm.synoder)
				.whereEq(synm.pk, synode)
				.rs(t0.instancontxt(conn0, robot))
				.rs(0);
		
		while (rs.next()) {
			String domain = rs.getString(synm.domain);
			SynDomanager domanger = new SynDomanager(
					synm, rs.getString(synm.org),
					domain, synode,
					conn0, synmod, Connects.getDebug(conn0));
			synodetiers.get(syntier_url).put(domain, domanger);
		}

		return this;
	}

	/**
	 * Try join (login) known domains
	 * 
	 * @return this
	 * @throws IOException 
	 * @throws SsException 
	 * @throws AnsonException 
	 * @throws SQLException 
	 * @throws TransException 
	 */
	public SynotierJettyApp openDomains(OnDomainUpdate ... onok)
			throws AnsonException, SsException, IOException, TransException, SQLException {
		if (synodetiers != null && synodetiers.containsKey(syntier_url)) {
			for (SynDomanager dmgr : synodetiers.get(syntier_url).values()) {
				dmgr.loadSynclients(t0, robot)
					.openUpdateSynssions(robot,
						(domain, mynid, peer, repb, xp) -> {
							if (!isNull(onok))
								onok[0].ok(domain, mynid, peer, repb, xp);
						});
			}
		}

		return this;
	}

	/**
	 * initialize oz_autoseq, a_users with sql script files,
	 * i. e., oz_autoseq.ddl, oz_autoseq.sql, a_users.sqlite.sql.
	 * 
	 * Should be called on for installation.
	 * 
	 * @param conn
	 * @throws Exception 
	 */
	public static void initSysRecords(SynodeConfig cfg, Iterable<SyncRobot> robots) throws Exception {
		ArrayList<String> sqls = new ArrayList<String>();
		IUser usr = DATranscxt.dummyUser();
	
			for (String tbl : new String[] {"oz_autoseq", "a_users"}) {
				sqls.add("drop table if exists " + tbl);
				Connects.commit(cfg.sysconn, usr, sqls);
				sqls.clear();
			}
	
			for (String tbl : new String[] {
						"oz_autoseq.ddl",
						"oz_autoseq.sql",
						"a_users.sqlite.ddl",}) {
	
				sqls.add(loadTxt(SynotierJettyApp.class, tbl));
				Connects.commit(cfg.sysconn, usr, sqls);
				sqls.clear();
			}
			
			if (robots != null) {
				syst = new DATranscxt(cfg.sysconn);
				JUserMeta um = new JUserMeta();
				Insert ins = null;
				for (SyncRobot robot : robots) {
					Insert i = robot.insert(syst.insert(um.tbl));
					if (ins == null)
						ins = i;
					else ins.post(i);
				}
			}
	}
	
	public static void setupSyntables(String synconn) throws SQLException, TransException {
		SynChangeMeta chm;
		SynSubsMeta sbm;
		SynchangeBuffMeta xbm;
		SynSessionMeta ssm;
		PeersMeta prm;
		SynodeMeta synm;

		chm  = new SynChangeMeta();
		sbm  = new SynSubsMeta(chm);
		xbm  = new SynchangeBuffMeta(chm);
		ssm  = new SynSessionMeta();
		prm  = new PeersMeta();
		synm = new SynodeMeta(synconn);
		// docm = new T_PhotoMeta(cfg.synconn);

		setupSqliTables(synconn, false, synm, chm, sbm, xbm, prm, ssm);
		
		SyntityMeta[] entm = new SyntityMeta[0];
		setupSqliTables(synconn, false, entm);
	}

	public void updateJservs(SynodeMeta synm, SynodeConfig cfg, String domain) throws TransException, SQLException {
		if (!eq(domain, cfg.domain))
			throw new SemanticException("Updating domain %s, but got configuration of %s.", domain, cfg.domain);

		for (Synode sn : cfg.peers())
			syst.update(synm.tbl, robot)
				.nv(synm.jserv, jserv)
				.whereEq(synm.pk, sn.jserv)
				.whereEq(synm.domain, cfg.domain)
				.u(syst.instancontxt(cfg.sysconn, robot));
	}
}
