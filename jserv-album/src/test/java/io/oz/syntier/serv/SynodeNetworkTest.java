package io.oz.syntier.serv;

import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.touchFile;
import static io.odysz.common.Utils.turnred;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.musteq;
import static io.odysz.common.Utils.logi;

import static org.junit.jupiter.api.Assertions.*;

import static io.oz.syntier.serv.T_CentralApp.zsu;
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

	@SuppressWarnings("unused")
	private static Thread centralThread;
	private static final String central_conn = "t-central-sqlite";

	@BeforeAll
	static void initEnv() throws IOException, InterruptedException, SQLException, TransException {
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
	 * @see AppSettings#merge_ip_json2db(io.oz.syn.registry.SynodeConfig, SynodeMeta, io.oz.syn.SyncUser, io.odysz.semantic.jprotocol.JProtocol.OnError)
	 * merge_ip_json2db()
	 */
	@Test
	void testSynodes() throws Exception {

		String maintenant = DateFormat.now();

		// hub
		turnred(T_WebservExposer.lights.get(hub));

		AppSettings settings_hub0 = AppSettings.load(SynotierSettingsTest.webinf, hubs);
		assertNull(settings_hub0.localIp());
		settings_hub0.regiserv = T_CentralApp.central_jserv;
		settings_hub0.centralPswd = T_CentralApp.admin_pswd;
		settings_hub0.jserv_utc = maintenant;
		settings_hub0.save();
		
		SynotierJettyApp jhub = SynotierJettyApp._main(new String[] {hubs});
		musteq(hub, jhub.syngleton.synode());
		
		// DateFormat.early(maintenant, "?");
		
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
		assertTrue(JServUrl.valid(queryJserv(jhub, hub)), cj_hub);
	
		// stop central
		T_CentralApp.app.stop();
		logi("This url shouldn't work: %s", T_CentralApp.central_jserv);

		// prv
		turnred(T_WebservExposer.lights.get(prv));

		AppSettings settings_prv0 = AppSettings.load(SynotierSettingsTest.webinf, prvs);
		assertNull(settings_prv0.localIp());
		// assertTrue(JServUrl.valid(settings_prv0.jservs.get(hub)));
		assertTrue(JServUrl.valid(settings_prv0.jserv(hub)));
		settings_prv0.regiserv = T_CentralApp.central_jserv;
		settings_prv0.centralPswd = T_CentralApp.admin_pswd;
		settings_prv0.jserv_utc = maintenant;
		settings_prv0.save();
		
		SynotierJettyApp jprv = SynotierJettyApp._main(new String[] {prvs});
		musteq(prv, jprv.syngleton.synode());
		
		// jprv.afterboot();
		awaitAll(T_WebservExposer.lights.get(prv), -1);
		
		assertTrue(JServUrl.valid(queryJserv(jprv, hub)), queryJserv(jprv, hub));
		assertNotNull(jprv.syngleton.settings.localIp());
		
		// wait for prv worker 1
		while(isblank(queryJserv(jhub, prv)))
			Thread.sleep(1000);
		assertEquals(queryJserv(jhub, prv), jprv.jserv());
		assertTrue(JServUrl.valid(queryJserv(jhub, prv)));

		assertNull(queryJserv(jhub, mob));
	
		// mobile
		turnred(T_WebservExposer.lights.get(mob));

		AppSettings settings_mob0 = AppSettings.load(SynotierSettingsTest.webinf, mobs);
		assertNull(settings_mob0.localIp());
		settings_mob0.jserv_utc = DateFormat.now();
		settings_mob0.regiserv = T_CentralApp.central_jserv;
		settings_mob0.centralPswd = T_CentralApp.admin_pswd;
		settings_mob0.save();
		
		SynotierJettyApp jmob = SynotierJettyApp._main(new String[] {mobs});
		musteq(mob, jmob.syngleton.synode());
		
		// jmob.afterboot();
		awaitAll(T_WebservExposer.lights.get(mob), -1);
		
		assertNotNull(jmob.syngleton.settings.localIp());
		assertNotNull(queryJserv(jhub, prv));

		// assertNotNull(queryJserv(jhub, mob));
		// wait for mob worker 1
		while(isblank(queryJserv(jhub, mob)))
			Thread.sleep(1000);

		assertEquals(queryJserv(jhub, mob), jmob.jserv());
		assertEquals(queryJserv(jmob, prv), jprv.jserv());
		assertTrue(JServUrl.valid(queryJserv(jmob, prv)));
		assertNull(queryJserv(jprv, mob));
		
		// wait for mob worker 1
		while(isblank(queryJserv(jprv, mob)))
			Thread.sleep(1000);
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
		return dom.synm.loadJservs(tb, dom.syngleton.syncfg.org.orgId, dom.domain(),
				rs -> JServUrl.valid(rs.getString(dom.synm.jserv)));
	}
}
