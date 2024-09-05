package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.len;
import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.logi;
import static io.odysz.semantic.meta.SemanticTableMeta.setupSqliTables;
import static io.oz.jserv.docs.syn.DoclientierTest.devs;
import static io.oz.jserv.docs.syn.SynoderTest.*;
import static io.oz.jserv.test.JettyHelperTest.*;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Configs;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.JUser;
import io.odysz.semantic.jsession.JUser.JOrgMeta;
import io.odysz.semantic.jsession.JUser.JRoleMeta;
import io.odysz.semantic.meta.AutoSeqMeta;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.meta.PeersMeta;
import io.odysz.semantic.meta.SynChangeMeta;
import io.odysz.semantic.meta.SynSessionMeta;
import io.odysz.semantic.meta.SynSubsMeta;
import io.odysz.semantic.meta.SynchangeBuffMeta;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.syn.Docheck;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.DoclientierTest.Dev;
import io.oz.jserv.docs.syn.jetty.SynotierJettyApp;

class SynodetierJoinTest {
	static int bsize;

	static ExpDocTableMeta docm;
	static ErrorCtx errLog;
	
	static final String clientconn = "main-sqlite";

	static final String[] servs_conn  = new String[] {
			"no-jserv.00", "no-jserv.01", "no-jserv.02", "no-jserv.03"};

	static final String[] config_xmls = new String[] {
			"config-0.xml", "config-1.xml", "config-2.xml", "config-3.xml"};
	
	// static Dev[] devs; // = new Dev[4];
	static SynotierJettyApp[] jetties;
	private static Docheck[] ck;
	
//	static final int X_0 = 0;
//	static final int X_1 = 1;
//	static final int Y_0 = 2;
//	static final int Y_1 = 3;

	static {
		try {
			jetties = new SynotierJettyApp[4];
//			devs = new Dev[4];
//			devs[X_0] = new Dev("client-at-00", "syrskyi", "слава україні", "X-0", zsu,
//								"src/test/res/anclient.java/1-pdf.pdf");
//
//			devs[X_1] = new Dev("client-at-00", "syrskyi", "слава україні", "X-1", zsu,
//								"src/test/res/anclient.java/2-ontario.gif");
//
//			devs[Y_0] = new Dev("client-at-01", "odyz", "8964", "Y-0", zsu,
//								"src/test/res/anclient.java/3-birds.wav");
//
//			devs[Y_1] = new Dev("client-at-01", "syrskyi", "слава україні", "Y-1", zsu,
//								"src/test/res/anclient.java/Amelia Anisovych.mp4");

			bsize = 72 * 1024;
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

		ck = new Docheck[servs_conn.length];
		
		int port = 8090;
		for (int i = 0; i < servs_conn.length; i++) {
			if (jetties[i] != null)
				jetties[i].stop();
			jetties[i] = startSyndoctier(servs_conn[i], config_xmls[i], port++);
//			devs[i].jserv = jetties[i].jserv();
			DoclientierTest.initRecords(servs_conn[i]);

			ck[i] = new Docheck(azert, zsu, servs_conn[i], zsu, SynodeMode.peer, docm);
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
		joinby(lights, X, Y);
		joinby(lights, X, Z);

		awaitAll(lights, 36000);
		syncdomain(Z);
		syncdomain(Y);
	}
	
	void joinby(boolean[] lights, int to, int by) throws Exception {
		SynotierJettyApp hub = jetties[to];
		SynotierJettyApp prv = jetties[by];
		Dev dev = devs[by];
		for (String servpattern : hub.synodetiers.keySet()) {
			if (len(hub.synodetiers.get(servpattern)) > 1 || len(prv.synodetiers.get(servpattern)) > 1)
				fail("Multiple synchronizing domain schema is an issue not handled in v 2.0.0.");
			
			for (String dom : hub.synodetiers.get(servpattern).keySet()) {
				SynDomanager hubmanger = hub.synodetiers.get(servpattern).get(dom);
				SynDomanager prvmanger = prv.synodetiers.get(servpattern).get(dom);
	
				prvmanger.joinDomain(dom, hubmanger.synode, hub.jserv(), dev.uid, dev.psw,
						(rep) -> { lights[by] = true; });
			}
		}
	}

	static void syncdomain(int tx) throws SemanticException, AnsonException, SsException, IOException {
		SynotierJettyApp t = jetties[tx];

		for (String servpattern : t.synodetiers.keySet()) {
			if (len(t.synodetiers.get(servpattern)) > 1)
				fail("Multiple synchronizing domainschema is an issue not handled in v 2.0.0.");

			for (String dom : t.synodetiers.get(servpattern).keySet()) {
				t.synodetiers.get(servpattern).get(dom).updomains();
			}
		}
	}

	static SynotierJettyApp startSyndoctier(String serv_conn, String config_xml, int port) throws Exception {
		AutoSeqMeta asqm = new AutoSeqMeta();
		JRoleMeta arlm = new JUser.JRoleMeta();
		JOrgMeta  aorgm = new JUser.JOrgMeta();
	
		SynChangeMeta chm = new SynChangeMeta();
		SynSubsMeta sbm = new SynSubsMeta(chm);
		SynchangeBuffMeta xbm = new SynchangeBuffMeta(chm);
		SynSessionMeta ssm = new SynSessionMeta();
		PeersMeta prm = new PeersMeta();
	
		SynodeMeta snm = new SynodeMeta(serv_conn);
		docm = new T_PhotoMeta(serv_conn);
		setupSqliTables(serv_conn, asqm, arlm, aorgm, snm, chm, sbm, xbm, prm, ssm, docm);

		return SynotierJettyApp .createSyndoctierApp(serv_conn, config_xml, null, port, webinf, ura, zsu)
								.start(() -> System.out, () -> System.err);
	}

}
