package io.oz.syntier.serv;

import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.touchFile;
import static io.odysz.common.Utils.turnred;
import static io.odysz.common.LangExt.musteq;
import static org.junit.jupiter.api.Assertions.*;

import static io.oz.syntier.serv.T_WebservExposer.hub;
import static io.oz.syntier.serv.T_WebservExposer.prv;
import static io.oz.syntier.serv.T_WebservExposer.mob;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.HashMap;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.common.DateFormat;
import io.odysz.common.FilenameUtils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.JServUrl;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.util.DAHelper;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.SynDomanager;
import io.oz.jserv.docs.syn.singleton.AppSettings;
import io.oz.registier.central.meta.CentSynodemeta;
import io.oz.syn.SynodeMode;

class SynodeNetworkTest {
	static final String zsu = "zsu";
	
	static final String backup_hub = "src/test/resources/submitjserv-hub.backup.json";
	static final String backup_prv = "src/test/resources/submitjserv-prv.backup.json";
	static final String backup_mob = "src/test/resources/submitjserv-mob.backup.json";
	
	boolean[] light = new boolean[] {false};

	static String hubs = "settings.hub.json"; 
	static String prvs = "settings.prv.json"; 
	static String mobs = "settings.mob.json"; 

	static String hubpath = "settings.hub.json"; 
	static String prvpath = "settings.prv.json"; 
	static String mobpath = "settings.mob.json"; 

	static String vol_hub = "../../../../volumes-0.7/volume-hub";
	static String vol_prv = "../../../../volumes-0.7/volume-prv";
	static String vol_mob = "../../../../volumes-0.7/volume-mob";

	static boolean[] central_quit = new boolean[] {false};

	private static Thread centralThread;
	private static final String central_conn = "t-central-sqlite";

	@BeforeAll
	static void initEnv() throws IOException, InterruptedException {
		// -DWEB-INF=src/main/webapp/WEB-INF
		System.setProperty("WEB-INF", "src/main/webapp/WEB-INF");

		System.setProperty("VOLUME_HUB", vol_hub);
		System.setProperty("VOLUME_PRV", vol_prv);
		System.setProperty("VOLUME_MOB", vol_mob);
		
		// settings.json
		hubpath = FilenameUtils.rel2abs(SynotierSettingsTest.webinf, hubs);
		prvpath = FilenameUtils.rel2abs(SynotierSettingsTest.webinf, prvs);
		mobpath = FilenameUtils.rel2abs(SynotierSettingsTest.webinf, mobs);

		Files.copy(Paths.get(backup_hub), Paths.get(hubpath), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(Paths.get(backup_prv), Paths.get(prvpath), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(Paths.get(backup_mob), Paths.get(mobpath), StandardCopyOption.REPLACE_EXISTING);
		
		for (String p : new String[] {vol_hub, vol_prv, vol_mob}) {
			String doc_db = FilenameUtils.rel2abs(SynotierSettingsTest.webinf, p, "doc-jserv.db"); 
			Files.delete(Paths.get(doc_db));
			touchFile(doc_db);

			String main_db = FilenameUtils.rel2abs(SynotierSettingsTest.webinf, p, "jserv-main.db");
			Files.delete(Paths.get(main_db));
			touchFile(main_db);
		}
		
		turnred(central_quit);
		centralThread = T_CentralApp.startCentral(central_quit);
	}
	
	@AfterAll
	static void clean() throws IOException, InterruptedException {
		// centralThread.join();
		Files.delete(Paths.get(hubpath));
		Files.delete(Paths.get(prvpath));
		Files.delete(Paths.get(mobpath));
	}
	
	/**
	 * <pre>
	--------------------------------+----------------------+-----------------------------------------
							  X whorker 0              X whorker 1
	--------------------------------+----------------------+-----------------------------------------
			 settings.json          |     X:docsync        |      peers
	AppSettings[jservs, jserv_utc]  -> syn_node.jserv, utc |
	  [jservs.key != X]             |                      |
	--------------------------------+----------------------+-----------------------------------------
									|     X:docsync        |      peer Y
									|  syn_node[Y].jserv  <-> synode[Y].jserv
									| * only persist working version
									| * in case X cannot visite Hub
	--------------------------------+----------------------+-----------------------------------------
										  X:docsync         
									   syn_node[X].jserv  <-  onIpChange() * (optional?)
			  onIpChange()          -> syn_node[X].jserv    
	--------------------------------+----------------------+-----------------------------------------
									|     X:docsync        |      peer Y & Z
									|  syn_node[X].jserv   -> synode[X].jserv
	--------------------------------+----------------------+-----------------------------------------
			   central              |     X:docsync        |
		cynodes[X].jserv, utc      <-  syn_node[X].jserv   |
	--------------------------------+----------------------+-----------------------------------------
			   central              |     X:docsync        |
	[Central] cynodes[Z].jserv      -> syn_node[Z].jserv, utc
	[Central] cynodes[Y].jserv      x> syn_node[Y].jserv, utc
    * requires verifying since jservs at central may or may not be working (not true as its utc is staled?)
	 * </pre>
	 * @throws Exception
	 */
	@Test
	void testSynodes() throws Exception {

		// hub
		turnred(T_WebservExposer.lights.get(hub));

		AppSettings settings_hub0 = AppSettings.load(SynotierSettingsTest.webinf, hubs);
		assertNull(settings_hub0.localIp());
		// settings_hub0.jserv_utc = DateFormat.now();
		settings_hub0.regiserv = T_CentralApp.central_jserv;
		settings_hub0.save();
		
		SynotierJettyApp jhub = SynotierJettyApp._main(new String[] {hubs});
		musteq(hub, jhub.syngleton.synode());
		
		
		assertNull(queryJserv(jhub, prv));
		assertNull(queryJserv(jhub, mob));
		
		String  hubconn = jhub.syngleton.domanager(zsu).synconn;
		SynodeMeta synm = jhub.syngleton.domanager(zsu).synm;
		
		Thread.sleep(1000);
		assertEquals(SynodeMode.hub.name(),
			DAHelper.getValstr(new DATranscxt(hubconn), hubconn, synm, synm.remarks, synm.pk, hub));
	
		// jhub exposed;
		awaitAll(T_WebservExposer.lights.get(hub), -1);

		assertNotNull(jhub.syngleton.settings.localIp());

		String cj_hub = queryCentral(
				jhub.syngleton.syncfg.org.orgId, jhub.syngleton.syncfg.domain, hub);
		assertTrue(JServUrl.valid(cj_hub));
		assertEquals(JServUrl.valid(queryJserv(jhub, hub)), cj_hub);
	
		// stop central
		centralThread.join();

		// prv
		turnred(T_WebservExposer.lights.get(prv));

		AppSettings settings_prv0 = AppSettings.load(SynotierSettingsTest.webinf, prvs);
		assertNull(settings_prv0.localIp());
		assertTrue(JServUrl.valid(settings_prv0.jservs.get(hub)));
		// settings_prv0.jserv_utc = DateFormat.now();
		settings_prv0.regiserv = T_CentralApp.central_jserv;
		settings_prv0.save();
		
		SynotierJettyApp jprv = SynotierJettyApp._main(new String[] {prvs});
		musteq(prv, jprv.syngleton.synode());
		
		// jprv.afterboot();
		awaitAll(T_WebservExposer.lights.get(prv), -1);
		
		assertTrue(JServUrl.valid(queryJserv(jprv, hub)), queryJserv(jprv, hub));
		assertNotNull(jprv.syngleton.settings.localIp());
		
		assertEquals(queryJserv(jhub, prv), jprv.jserv());
		assertTrue(JServUrl.valid(queryJserv(jhub, prv)));

		assertNull(queryJserv(jhub, mob));
	
		// mobile
		turnred(T_WebservExposer.lights.get(mob));

		AppSettings settings_mob0 = AppSettings.load(SynotierSettingsTest.webinf, mobs);
		assertNull(settings_mob0.localIp());
		// assertTrue(JServUrl.valid(settings_mob0.jservs.get(hub)));
		settings_mob0.jserv_utc = DateFormat.now();
		settings_mob0.regiserv = T_CentralApp.central_jserv;
		settings_mob0.save();
		
		SynotierJettyApp jmob = SynotierJettyApp._main(new String[] {mobs});
		musteq(mob, jmob.syngleton.synode());
		
		// jmob.afterboot();
		awaitAll(T_WebservExposer.lights.get(mob), -1);
		
		assertNotNull(jmob.syngleton.settings.localIp());
		assertNotNull(queryJserv(jhub, prv));
		assertNotNull(queryJserv(jhub, mob));

		assertEquals(queryJserv(jhub, mob), jmob.jserv());
		assertEquals(queryJserv(jmob, prv), jprv.jserv());
		assertTrue(JServUrl.valid(queryJserv(jmob, prv)));
		assertNull(queryJserv(jprv, mob));
		
		// prv
		// SynDomanager domprv = jprv.syngleton.domanager(zsu);

		// domprv.submitPersistDBserv(null);
		// DATranscxt syntb = new DATranscxt(domprv.synconn);
		// ?? domprv.updbservs_byHub(syntb);

		assertEquals(queryJserv(jprv, mob), jmob.jserv());
	}

	/**
	 * Load db/syn_synode.jserv.
	 * @return the peer's jserv, from db.
	 * @throws Exception 
	 */
	private String queryJserv(SynotierJettyApp synapp, String peer) throws Exception {
		SynDomanager dom = synapp.syngleton.domanager(zsu);
		HashMap<String, String[]> jservs = loadJservs(dom);
		return jservs == null || jservs.get(peer) == null ? null : jservs.get(peer)[0];
	}

	private String queryCentral(String org, String domx, String peer) throws Exception {
		CentSynodemeta m = new CentSynodemeta(central_conn);
		DATranscxt tb = new DATranscxt(central_conn);
		AnResultset rs = ((AnResultset) tb.select(m.tbl)
		  .cols(m.jserv, m.domx, m.optime)
		  .whereEq(m.domx, domx)
		  .whereEq(m.orgid, org)
		  .rs(tb.instancontxt(central_conn, DATranscxt.dummyUser()))
		  .rs(0));
		
		if (rs.next())
			return rs.getString(m.jserv);
		return null;
	}

	public HashMap<String, String[]> loadJservs(SynDomanager dom)
			throws SQLException, TransException {
		DATranscxt tb = new DATranscxt(dom.synconn);
		return dom.synm.loadJservs(tb, dom.domain(), rs -> JServUrl.valid(rs.getString(dom.synm.jserv)));
	}
}
