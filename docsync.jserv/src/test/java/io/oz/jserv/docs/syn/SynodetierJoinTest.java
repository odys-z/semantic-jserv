package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.waiting;
import static io.odysz.semantic.syn.Docheck.ck;
import static io.odysz.semantic.syn.Docheck.printChangeLines;
import static io.odysz.semantic.syn.Docheck.printNyquv;
import static io.oz.jserv.docs.syn.Dev.docm;
import static io.oz.jserv.docs.syn.SynoderTest.X;
import static io.oz.jserv.docs.syn.SynoderTest.Y;
import static io.oz.jserv.docs.syn.SynoderTest.Z;
import static io.oz.jserv.docs.syn.SynoderTest.azert;
import static io.oz.jserv.docs.syn.SynoderTest.ura;
import static io.oz.jserv.docs.syn.SynoderTest.zsu;
import static io.oz.jserv.test.JettyHelperTest.webinf;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.syn.DBSyntableBuilder;
import io.odysz.semantic.syn.Docheck;
import io.odysz.semantic.syn.SyncRobot;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.jetty.SynotierJettyApp;
import io.oz.synode.jclient.SynodeConfig;
import io.oz.synode.jclient.YellowPages;

/**
 * 
 * Run JettyHelperTest to initialoze table a_roles.
 * 
 * -Dsyndocs.ip="host-ip"
 * 
 * @author ody
 */
class SynodetierJoinTest {

	static ErrorCtx errLog;
	
	static final String clientconn = "main-sqlite";
	static final String syrskyi = "syrskyi";
	static final String slava = "слава україні";

	static final String[] servs_conn  = new String[] {
			"no-jserv.00", "no-jserv.01", "no-jserv.02", "no-jserv.03"};

	static final String[] config_xmls = new String[] {
			"config-0.xml", "config-1.xml", "config-2.xml", "config-3.xml"};
	
	static SynotierJettyApp[] jetties;

	static {
		try {
			jetties = new SynotierJettyApp[4];

			docm = new T_PhotoMeta(clientconn);
			
			errLog = new ErrorCtx() {
				@Override
				public void err(MsgCode code, String msg, String...args) {
					fail(msg);
				}
			};

		} catch (TransException e) {
			e.printStackTrace();
		}
	}
	
	@BeforeAll
	static void init() throws Exception {
		String p = new File("src/test/res").getAbsolutePath();
    	System.setProperty("VOLUME_HOME", p + "/volume");
    	logi("VOLUME_HOME : %s", System.getProperty("VOLUME_HOME"));

		Configs.init(webinf);
		Connects.init(webinf);
		YellowPages.load("$VOLUME_HOME");

		ck = new Docheck[servs_conn.length];
		
		// int port = 8090;
		for (int i = 0; i < servs_conn.length; i++) {
			if (jetties[i] != null)
				jetties[i].stop();
			
			SynodeConfig config = new SynodeConfig();
			config.synconn = servs_conn[i];
			config.sysconn = servs_conn[i];

			SynotierJettyApp.setupSysRecords(config, YellowPages.robots());

			jetties[i] = startSyndoctier(config);
					// System.getProperty("syndocs.ip"), port++, true);

			ck[i] = new Docheck(azert, zsu, servs_conn[i],
								jetties[i].synode(), SynodeMode.peer, docm);
		}
	}

	@Test
	void testSynodetierJoin() throws Exception {
		setupDomain();
	}
	
	/**
	 * <pre>
	 * X ←join- Y, X ←join- Z;
	 * X ←sync- Z, X ←sync- Y.
	 * </pre>
	 * @throws Exception
	 */
	void setupDomain() throws Exception {
		boolean[] lights = new boolean[] {true, false, false};
		
		waiting(lights, Y);
		joinby(lights, X, Y); // no subscription of Z
		awaitAll(lights, -1);
		ck[X].synodes(X, Y);
		ck[Y].synodes(X, Y);

		waiting(lights, Z);
		joinby(lights, X, Z);
		awaitAll(lights, -1);
		ck[X].synodes(X, Y, Z);
		ck[Z].synodes(X, -1, Z);

		waiting(lights, Z);
		syncdomain(lights, Z);
		awaitAll(lights, -1);
		ck[X].synodes(X, Y, Z);
		ck[Y].synodes(X, Y, -1);
		ck[Z].synodes(X, -1, Z); // Z joined latter, no subs or Y's joining 

		waiting(lights, Y);
		syncdomain(lights, Y);
		awaitAll(lights, -1);
		ck[X].synodes(X, Y, Z);
		ck[Y].synodes(X, Y, Z);
		ck[Z].synodes(X, -1, Z);
	}
	
	void joinby(boolean[] lights, int to, int by) throws Exception {
		SynotierJettyApp hub = jetties[to];
		SynotierJettyApp prv = jetties[by];

		for (String servpattern : hub.synodetiers.keySet()) {
			if (len(hub.synodetiers.get(servpattern)) > 1 || len(prv.synodetiers.get(servpattern)) > 1)
				fail("Multiple synchronizing domain schema is an issue not handled in v 2.0.0.");
			
			for (String dom : hub.synodetiers.get(servpattern).keySet()) {
				SynDomanager hubmanger = hub.synodetiers.get(servpattern).get(dom);
				SynDomanager prvmanger = prv.synodetiers.get(servpattern).get(dom);
	
				prvmanger.joinDomain(dom, hubmanger.synode, hub.jserv(), syrskyi, slava,
						(rep) -> { lights[by] = true; });
			}
		}
	}

	static void syncdomain(boolean[] lights, int tx, Docheck... ck)
			throws SemanticException, AnsonException, SsException, IOException {

		SynotierJettyApp t = jetties[tx];

		for (String servpattern : t.synodetiers.keySet()) {
			if (len(t.synodetiers.get(servpattern)) > 1)
				fail("Multiple synchronizing domain schema is an issue not handled in v 2.0.0.");

			for (String dom : t.synodetiers.get(servpattern).keySet()) {
				t.synodetiers.get(servpattern).get(dom).updomains(
					(domain, mynid, peer, repb, xp) -> {
						if (!isNull(ck))
							try {
								printChangeLines(ck);
								printNyquv(ck);
							} catch (TransException | SQLException e) {
								e.printStackTrace();
							}

						if (eq(domain, dom) && eq(mynid, jetties[tx].synode())) {
							lights[tx] = true;
							Utils.logi("lights[%s] = true", tx);
						}
						else {
							DBSyntableBuilder trb = isNull(xp) ? null : xp[0].trb;
							throw new NullPointerException(String.format(
								"Unexpected callback for domain: %s, my-synode-id: %s, to peer: %s, synconn: %s",
								domain, mynid, peer, xp == null || trb == null ? "unknown" : trb.synconn()));
						}
					});
			}
		}
	}

	/**
	 * Start a synode tier with the user identity which is authorized
	 * to login to every peer. 
	 * 
	 * @param serv_conn
	 * @param config_xml
	 * @param port
	 * @param drop_syntbls force dropping table before commit TableMeta.ddlSqlite.
	 * @return the Jetty App, with a servlet server.
	 * @throws Exception
	 */
	static SynotierJettyApp startSyndoctier(SynodeConfig cfg) throws Exception {
		String serv_conn = cfg.synconn;
		String config_xml= cfg.confxml;
		String host = cfg.host;
		int port = cfg.port;

//		SynChangeMeta chm;
//		SynSubsMeta sbm;
//		SynchangeBuffMeta xbm;
//		SynSessionMeta ssm;
//		PeersMeta prm;
		SynodeMeta synm;
//
//		chm  = new SynChangeMeta();
//		sbm  = new SynSubsMeta(chm);
//		xbm  = new SynchangeBuffMeta(chm);
//		ssm  = new SynSessionMeta();
//		prm  = new PeersMeta();
		synm = new SynodeMeta(serv_conn);
//		docm = new T_PhotoMeta(serv_conn);
//
//		// setupSqliTables(serv_conn, false, synm, chm, sbm, xbm, prm, ssm, docm);

		SyncRobot tierobot = YellowPages.getRobot(syrskyi);
		tierobot = new SyncRobot(syrskyi, slava, syrskyi + "@" + ura).orgId(ura);

		return SynotierJettyApp 
			.createSyndoctierApp(serv_conn, config_xml, host, port, webinf, zsu, tierobot)
			.start(() -> System.out, () -> System.err)
			.loadDomains(synm, SynodeMode.peer)
			;
	}

	/**
	 * initialize oz_autoseq, a_users with sql script files,
	 * i. e., oz_autoseq.ddl, oz_autoseq.sql, a_users.sqlite.sql.
	 * 
	 * @param conn
	static void initSysRecords(SynodeConfig cfg, SyncRobot[] robots) {
		SynotierJettyApp.initSysRecords(cfg, robots);
	}
	 */
}
