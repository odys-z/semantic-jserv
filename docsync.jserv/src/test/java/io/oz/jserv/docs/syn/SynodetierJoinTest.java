package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.waiting;
import static io.odysz.semantic.syn.Docheck.ck;
import static io.odysz.semantic.syn.Docheck.printChangeLines;
import static io.odysz.semantic.syn.Docheck.printNyquv;
import static io.oz.jserv.docs.syn.Dev.docm;
import static io.oz.jserv.docs.syn.singleton.CreateSyndocTierTest.setVolumeEnv;
import static io.oz.jserv.docs.syn.singleton.CreateSyndocTierTest.ura;
import static io.oz.jserv.docs.syn.singleton.CreateSyndocTierTest.webinf;
import static io.oz.jserv.docs.syn.singleton.CreateSyndocTierTest.zsu;
import static org.junit.jupiter.api.Assertions.fail;


import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.common.Configs;
import io.odysz.common.IAssert;
import io.odysz.common.Utils;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.meta.SyntityMeta;
import io.odysz.semantic.syn.Docheck;
import io.odysz.semantic.syn.SyncUser;
import io.odysz.semantic.syn.Synode;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.AssertImpl;
import io.oz.jserv.docs.syn.singleton.Syngleton;
import io.oz.jserv.docs.syn.singleton.SynotierJettyApp;
import io.oz.syn.SynOrg;
import io.oz.syn.SynodeConfig;
import io.oz.syn.YellowPages;

/**
 * 
 * Run JettyHelperTest to initialize table a_roles.
 * 
 * -Dsyndocs.ip="host-ip"
 * 
 * @author ody
 */
public class SynodetierJoinTest {
	public static String owner  = "ody";
	public static String passwd = "abc";
	
	public static final int X = 0;
	public static final int Y = 1;
	public static final int Z = 2;
	public static final int W = 3;

	public static IAssert azert = new AssertImpl();

	public static ErrorCtx errLog;
	
	public static final String clientconn = "main-sqlite";
	public static final String syrskyi = "syrskyi";
	public static final String slava = "слава україні";

	public static final String[] servs_conn  = new String[] {
			"no-jserv.00", "no-jserv.01", "no-jserv.02", "no-jserv.03"};

	public static final String sys_conn  = "main-sqlite";

	public static final String[] config_xmls = new String[] {
			"config-0.xml", "config-1.xml", "config-2.xml", "config-3.xml"};
	
	public static SynotierJettyApp[] jetties;

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
		setVolumeEnv("v-");

		Configs.init(webinf);
		Connects.init(webinf);
		YellowPages.load("$VOLUME_HOME");

		ck = new Docheck[servs_conn.length];
		
		int port = 8090;
		String[] nodes = new String[] { "X", "Y", "Z" };

		for (int i = 0; i < nodes.length; i++) {
			if (jetties[i] != null)
				jetties[i].stop();
			
			SyncUser me = new SyncUser(syrskyi, slava, syrskyi, "#-" + i).orgId(ura);
			ArrayList<SyncUser> robots = new ArrayList<SyncUser>() { {add(me);} };

			SynodeConfig config = new SynodeConfig(nodes[i], SynodeMode.peer);
			config.synconn = servs_conn[i];
			config.sysconn = f("main-sqlite-%s", i);
			// config.port    = port++;
			config.mode    = SynodeMode.peer;
			config.org     = new SynOrg();
			config.org.orgId= ura;
			config.org.meta = "io.oz.jserv.docs.meta.DocOrgMeta";
			config.domain  = eq(nodes[i], "X") ? zsu : null;
			config.peers   = new Synode[] {new Synode(nodes[i], nodes[i] + "," + nodes[i], ura, config.domain)};

			Syngleton.setupSysRecords(config, robots);
			
			Syngleton.setupSyntables(config,
					new ArrayList<SyntityMeta>() {{add(docm);}},
					webinf, "config.xml", ".", "ABCDEF0123465789", true);

			Syngleton.cleanDomain(config);

			Syngleton.cleanSynssions(config);

			// DB is dirty when testing again
			String top = config.domain;
			config.domain = zsu;
			Syngleton.cleanDomain(config);
			config.domain = top;
			
			jetties[i] = startSyndoctier(config, robots.get(0),
					f("config-%s.xml", i), f("$VOLUME_%s/syntity.json", i));

			ck[i] = new Docheck(azert, zsu, servs_conn[i],
								config.synode(), SynodeMode.peer, docm, null, config.debug);
		}
	}

	@Test
	void testSynodetierJoin() throws Exception {
		setupDomain();
		// Utils.pause("Press Enter...");
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
		
		int no = 0;

		printChangeLines(ck);
		printNyquv(ck, true);
		Utils.logrst("X <- join - Y", ++no);

		waiting(lights, Y);
		joinby(lights, X, Y); // no subscription of Z
		awaitAll(lights, -1);

		printChangeLines(ck);
		printNyquv(ck, true);
		ck[X].synodes(X, Y);
		ck[Y].synodes(X, Y);

		Utils.logrst("X <- join - Z", ++no);
		waiting(lights, Z);
		joinby(lights, X, Z);
		awaitAll(lights, -1);

		printChangeLines(ck);
		printNyquv(ck, true);
		ck[X].synodes(X, Y, Z);
		ck[Z].synodes(X, -1, Z);

		Utils.logrst("Z sync domain", ++no);
		waiting(lights, Z);
		syncdomain(lights, Z);
		awaitAll(lights, -1);

		printChangeLines(ck);
		printNyquv(ck, true);
		ck[X].synodes(X, Y, Z);
		ck[Y].synodes(X, Y, -1);
		ck[Z].synodes(X, -1, Z); // Z joined latter, no subs or Y's joining 

		Utils.logrst("Y sync domain", ++no);
		waiting(lights, Y);
		syncdomain(lights, Y);
		awaitAll(lights, -1);

		printChangeLines(ck);
		printNyquv(ck, true);
		ck[X].synodes(X, Y, Z);
		ck[Y].synodes(X, Y, Z);
		ck[Z].synodes(X, -1, Z);
	}
	
	void joinby(boolean[] lights, int to, int by) throws Exception {
		SynotierJettyApp hub = jetties[to];
		SynotierJettyApp prv = jetties[by];

		Set<String> hubdoms = hub.syngleton().domains();
		if (len(prv.syngleton().domains()) > 1)
			fail("Multiple synchronizing domain schema is an issue not handled in v 2.0.0.");
		
		for (String dom : hubdoms) {
			SynDomanager hubmanger = hub.syngleton().domanager(dom);
			SynDomanager prvmanger = prv.syngleton().domanager(null);

			Utils.logi("%s Joining By %s\n''''''''''''''", prvmanger.synode, hubmanger.synode);

			prvmanger.joinDomain(prvmanger.org, dom, hubmanger.synode, hub.jserv(), syrskyi, slava,
					(rep) -> { lights[by] = true; });
			
			break;
		}
	}

	public static void syncdomain(boolean[] lights, int tx, Docheck... ck)
			throws SemanticException, SsException, IOException {

		SynotierJettyApp t = jetties[tx];

		for (String dom : t.syngleton().domains()) {
			t.syngleton().domanager(dom).asyUpdomains(
				(domain, mynid, peer, xp) -> {
					if (!isNull(ck) && !isblank(peer))
						try {
							Utils.logi("On domain updated: %s : %s <-> %s", dom, mynid, peer);
							Utils.logi("===============================\n");
							printChangeLines(ck);
							printNyquv(ck);
						} catch (TransException | SQLException e) {
							e.printStackTrace();
						}

					if (isblank(peer)) {
						// finished domain, with or without errors
						lights[tx] = true; 

						if (eq(domain, dom)) 
							Utils.logi("lights[%s] (%s) = true", tx, mynid);
						else if (isblank(dom)) 
							Utils.warnT(new Object() {},
										"This can only be reached while joining (updating) a domain, to %s",
										domain);
						else {
							throw new NullPointerException(f(
								"While updating domain, there is an error on unexpected domain: %s, my-synode-id: %s, to peer: %s, synconn: %s",
								domain, mynid, peer,
								isNull(xp) || xp[0].trb == null ? "N/A" : xp[0].trb.syndomx.synconn));
						}
					}
				}, (blockby) -> {
					Utils.logi("Synode thread is blocked by %s, expiring in %s", blockby, -1);
					return 2000;
				});
		}
	}

	/**
	 * Start a Jetty app with system print stream for logging.
	 * 
	 * @return the Jetty App, with a servlet server.
	 * @throws Exception
	 */
	public static SynotierJettyApp startSyndoctier(SynodeConfig cfg, SyncUser admin,
			String cfg_xml, String syntity_json) throws Exception {

		return SynotierJettyApp 
			.createSyndoctierApp(cfg, new DocUser(((ArrayList<SyncUser>) YellowPages.robots()).get(0)),
					"/", webinf, cfg_xml, syntity_json)
			.start(() -> System.out, () -> System.err)
			;
	}
}
