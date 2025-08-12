package io.oz.syntier.serv;

import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.turnred;
import static org.junit.jupiter.api.Assertions.*;

import static io.oz.syntier.serv.T_WebservExposer.hub;
import static io.oz.syntier.serv.T_WebservExposer.prv;
import static io.oz.syntier.serv.T_WebservExposer.mob;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.common.FilenameUtils;
import io.oz.jserv.docs.syn.SynDomanager;
import io.oz.jserv.docs.syn.singleton.AppSettings;

class SyngletonTest {
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

	@BeforeAll
	static void initEnv() throws IOException {
		System.setProperty("VOLUME_HUB", "../../../../volumes-0.7/volume-hub");
		System.setProperty("VOLUME_PRV", "../../../../volumes-0.7/volume-prv");
		
		// settings.json
		hubpath = FilenameUtils.rel2abs(SynotierSettingsTest.webinf, hubs);
		prvpath = FilenameUtils.rel2abs(SynotierSettingsTest.webinf, prvs);
		mobpath = FilenameUtils.rel2abs(SynotierSettingsTest.webinf, mobs);

		Files.copy(Paths.get(backup_hub), Paths.get(hubpath), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(Paths.get(backup_prv), Paths.get(prvpath), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(Paths.get(backup_mob), Paths.get(mobpath), StandardCopyOption.REPLACE_EXISTING);
	}
	
	@AfterAll
	static void clean() throws IOException {
		Files.delete(Paths.get(hubpath));
		Files.delete(Paths.get(prvpath));
		Files.delete(Paths.get(mobpath));
	}
	
	@Test
	void testExposeIP() throws Exception {

		// hub
		turnred(T_WebservExposer.lights.get(hub));

		AppSettings settings_hub0 = AppSettings.load(SynotierSettingsTest.webinf, hubs);
		assertNull(settings_hub0.localIp);
		
		SynotierJettyApp jhub = SynotierJettyApp._main(new String[] {hubs});
		
		jhub.afterboot();
		awaitAll(T_WebservExposer.lights.get(hub), -1);
		
		assertNotNull(jhub.syngleton.settings.localIp);
		
		// prv
		turnred(T_WebservExposer.lights.get(prv));

		AppSettings settings_prv0 = AppSettings.load(SynotierSettingsTest.webinf, prvs);
		assertNull(settings_prv0.localIp);
		
		SynotierJettyApp jprv = SynotierJettyApp._main(new String[] {prvs});
		
		jprv.afterboot();
		awaitAll(T_WebservExposer.lights.get(prv), -1);
		
		assertNotNull(jprv.syngleton.settings.localIp);
		
		assertEquals(queryJserv(jhub, jprv.syngleton.synode()), jprv.jserv());
		
		// prv.syngleton.domanager(zsu).updateJservs(new DATranscxt(prv.syngleton.domanager(zsu).synconn));
		assertEquals(queryJserv(jprv, jhub.syngleton.synode()), jhub.jserv());
	
		// mob
		turnred(T_WebservExposer.lights.get(mob));

		AppSettings settings_mob0 = AppSettings.load(SynotierSettingsTest.webinf, mobs);
		assertNull(settings_mob0.localIp);
		
		SynotierJettyApp jmob = SynotierJettyApp._main(new String[] {mobs});
		
		jmob.afterboot();
		awaitAll(T_WebservExposer.lights.get(mob), -1);
		
		assertNotNull(jmob.syngleton.settings.localIp);

		assertEquals(queryJserv(jhub, jmob.syngleton.synode()), jmob.jserv());
		assertEquals(queryJserv(jprv, jmob.syngleton.synode()), jmob.jserv());

		// prv.syngleton.domanager(zsu).updateJservs(new DATranscxt(prv.syngleton.domanager(zsu).synconn));
		// mob.syngleton.domanager(zsu).updateJservs(new DATranscxt(mob.syngleton.domanager(zsu).synconn));
		assertEquals(queryJserv(jmob, jprv.syngleton.synode()), jprv.jserv());
		assertEquals(queryJserv(jmob, jhub.syngleton.synode()), jhub.jserv());
	}

	private String queryJserv(SynotierJettyApp synapp, String peer) throws Exception {
		SynDomanager dom = synapp.syngleton.domanager(zsu);
		// jserv of peer's in my db
		return AppSettings.getJserv(dom.synm, dom.synconn, dom.domain(), peer);
	}

}
