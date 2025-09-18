package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.len;
import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.repeat;
import static io.odysz.common.Utils.waiting;
import static io.oz.jserv.docs.syn.Dev.docm;
import static io.oz.jserv.docs.syn.singleton.SynotierJettyApp.webinf;
import static io.oz.jserv.docs.syn.singleton.SynotierJettyApp.zsu;
import static io.oz.syn.Docheck.ck;
import static io.oz.syn.Docheck.printChangeLines;
import static io.oz.syn.Docheck.printNyquv;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import io.odysz.anson.AnsonException;
import io.odysz.anson.JsonOpt;
import io.odysz.common.Configs;
import io.odysz.common.EnvPath;
import io.odysz.common.FilenameUtils;
import io.odysz.common.IAssert;
import io.odysz.common.Utils;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.meta.SynChangeMeta;
import io.odysz.semantic.meta.SynSubsMeta;
import io.odysz.semantic.meta.SynchangeBuffMeta;
import io.odysz.semantic.meta.SyntityMeta;
import io.odysz.semantics.IUser;
import io.odysz.transact.sql.parts.Logic.op;
import io.odysz.transact.sql.parts.condition.ExprPart;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.AssertImpl;
import io.oz.jserv.docs.syn.singleton.AppSettings;
import io.oz.jserv.docs.syn.singleton.Syngleton;
import io.oz.jserv.docs.syn.singleton.SynotierJettyApp;
import io.oz.syn.DBSynTransBuilder;
import io.oz.syn.Docheck;
import io.oz.syn.SynodeMode;
import io.oz.syn.registry.SynodeConfig;
import io.oz.syn.registry.YellowPages;

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
	
	public static final int chsize = 480;
	public static final int X = 0;
	public static final int Y = 1;
	public static final int Z = 2;
	public static final int W = 3;

	public static IAssert azert = new AssertImpl();

	public static ErrorCtx errLog;
	
	public static final String servpath = "jserv-album";
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

	public static void setVolumeEnv(String vol_prefix) {
		String p = new File("src/test/res").getAbsolutePath();
    	System.setProperty("VOLUME_HOME", p + "/volume");
    	logi("VOLUME_HOME : %s", System.getProperty("VOLUME_HOME"));

		for (int c = 0; c < 4; c++) {
			System.setProperty(f("VOLUME_%s", c), f("%s/%s%s", p, vol_prefix, c));
			logi("VOLUME %s : %s\n", c, System.getProperty(f("VOLUME_%s", c)));
		}
	}

	@SuppressWarnings({ "deprecation" })
	@BeforeAll
	static void init() throws Exception {
		setVolumeEnv("v-");

		Configs.init(webinf);
		Connects.init(webinf);
		YellowPages.load(EnvPath.concat(webinf, "$VOLUME_HOME"));

		ck = new Docheck[servs_conn.length];
		
		String[] nodes = new String[] { "X", "Y", "Z" };

		for (int i = 0; i < nodes.length; i++) {
			if (jetties[i] != null)
				jetties[i].stop();
			
			String $vol_home = f("$VOLUME_%s", i);
			logi("[...] load dictionary configuration %s/* ...", $vol_home); 
			YellowPages.load(FilenameUtils.concat(
					new File(".").getAbsolutePath(),
					webinf,
					EnvPath.replaceEnv($vol_home)));


			String cfgxml = f("config-%s.xml", i);
			String stjson = f("settings-%s.json", i);

			SynodeConfig config = YellowPages.synconfig().replaceEnvs();
			Syngleton.defltScxt = new DATranscxt(config.sysconn);

			AppSettings settings = AppSettings.load(webinf, stjson);
			settings.installkey = "0123456789ABCDEF";	
			settings.rootkey = null;
			settings.toFile(FilenameUtils.concat(webinf, stjson), JsonOpt.beautify());

			Syngleton.setupSysRecords(config, YellowPages.robots());
			
			Syngleton.setupSyntables(config,
					// new ArrayList<SyntityMeta>() {{add(docm);}}, // 0.7.6
					new SyntityMeta[] {docm},
					webinf, "config.xml", ".", "ABCDEF0123465789", true);

			// Syngleton.cleanSynssions(config);

			// DB is dirty when testing again
			cleanDomain(config);

			settings = AppSettings.checkInstall(SynotierJettyApp.servpath, webinf, cfgxml, stjson, true);

			jetties[i] = SynotierJettyApp.boot(webinf, cfgxml, stjson)
						.print("\n. . . . . . . . Synodtier Jetty Application is running . . . . . . . ");
			
			// checker
			ck[i] = new Docheck(azert, zsu, servs_conn[i], jetties[i].syngleton().domanager(zsu).synode,
							SynodeMode.peer, config.chsize, docm, null, true);
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
		syncdomain(Z);

		printChangeLines(ck);
		printNyquv(ck, true);
		ck[X].synodes(X, Y, Z);
		ck[Y].synodes(X, Y, -1);
		ck[Z].synodes(X, -1, Z); // Z joined latter, no subs or Y's joining 

		Utils.logrst("Y sync domain", ++no);
		syncdomain(Y);

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
			
			// 2025-03-09 decision:
			// To join a domain, it means notifying other nodes in the domain, with prerequisite registration.
			// SynDomanager prvmanger = prv.syngleton().domanager(null);
			   SynDomanager prvmanger = prv.syngleton().domanager(dom);

			Utils.logi("%s Joining By %s\n''''''''''''''", hubmanger.synode, prvmanger.synode);

			prvmanger.joinDomain(prvmanger.org, dom, hubmanger.synode, hub.jserv(), syrskyi, slava,
					(rep) -> { lights[by] = true; });
			
			break;
		}
	}

	@SuppressWarnings("deprecation")
	public static void syncdomain(int tx, Docheck... ck)
			throws SsException, IOException, AnsonException, TransException, SQLException, SAXException {

		SynotierJettyApp t = jetties[tx];

		for (String dom : t.syngleton().domains()) {
			Utils.logi("Updating/synchronizing domain %s", dom);
			SynDomanager mgr = t.syngleton().domanager(dom);
			mgr.loadSynclients(new DATranscxt(mgr.synconn));
			mgr.updomain(
				(domain, mynid, peer, xp) -> {
					if (!isNull(ck) && !isblank(peer))
						try {
							int len = len(Utils.logi("On domain updated: %s : %s <-> %s", dom, mynid, peer));
							Utils.logi(repeat("=", len));

							printChangeLines(ck);
							printNyquv(ck);
						} catch (TransException | SQLException e) {
							e.printStackTrace();
						}

					if (isblank(peer)) {
						// finished domain, with or without errors
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
				});
		}
	}

	/**
	 * @param cfg
	 * @throws Exception
	 */
	public static void cleanDomain(SynodeConfig cfg)
			throws Exception {
		IUser usr = DATranscxt.dummyUser();

		SynChangeMeta chgm = new SynChangeMeta (cfg.synconn);
		SynSubsMeta   subm = new SynSubsMeta (chgm, cfg.synconn);
		SynchangeBuffMeta xbfm = new SynchangeBuffMeta(chgm, cfg.synconn);

		DATranscxt.initConfigs(cfg.synconn, 
				(c) -> new DBSynTransBuilder.SynmanticsMap(cfg.synode(), c));
		DATranscxt tb0 = new DATranscxt(cfg.synconn);
		
		tb0.delete(chgm.tbl, usr)
			.whereEq(chgm.domain, cfg.domain)
			.post(tb0.delete(subm.tbl)
					.where(op.isNotnull, subm.changeId, new ExprPart()))
			.post(tb0.delete(xbfm.tbl)
					.where(op.isNotnull, xbfm.changeId, new ExprPart()))
			.d(tb0.instancontxt(cfg.synconn, usr));
	}
}
