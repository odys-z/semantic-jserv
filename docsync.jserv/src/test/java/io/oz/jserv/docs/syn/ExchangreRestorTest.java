package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.musteqs;
import static io.odysz.common.Utils.awaitAll;
import static io.odysz.common.Utils.logi;
import static io.odysz.common.Utils.touchFile;
import static io.odysz.common.Utils.turngreen;
import static io.odysz.common.Utils.turnred;
import static io.oz.jserv.docs.syn.singleton.SynodetierJoinTest.errLog;
import static org.junit.jupiter.api.Assertions.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.common.FilenameUtils;
import io.odysz.common.IAssert;
import io.odysz.jclient.syn.IFileProvider;
import io.odysz.semantic.tier.docs.DeviceTableMeta;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.ShareFlag;
import io.oz.jserv.docs.AssertImpl;
import io.oz.jserv.docs.syn.singleton.SynotierJettyApp;
import io.oz.syn.Docheck;
import io.oz.syn.SynodeMode;

class ExchangreRestorTest {

	private static final String zsu = "zsu";
	private static final String admin = "admin";
	private static final String _8964 = "8964";

	static final String web_inf = "WEB-INF";
	static final String webinf = "./src/test/res/" + web_inf;
	static final String config_xml = "config.xml";

	static final String backup_hub = "src/test/resources/exbreak-hub.backup.json";
	static final String backup_prv = "src/test/resources/exbreak-prv.backup.json";
	// static final String backup_mob = "src/test/resources/exbreak-mob.backup.json";
	
	boolean[] light = new boolean[] {false};
	private static SynotierJettyApp jhub;
	private static SynotierJettyApp jprv;

	static String hubs = "settings.hub.json"; 
	static String prvs = "settings.prv.json"; 
	// static String mobs = "settings.mob.json"; 

	static String hubpath = "settings.hub.json"; 
	static String prvpath = "settings.prv.json"; 
	// static String mobpath = "settings.mob.json"; 
	static String connect_xml = "connects.xml";
	static String connect_bak = "connects.xml-backup";
	static String connect_breaks = "src/test/res/exbreak-vols/connects-exbreaks.xml";

	static String vol_hub = "../../../test/res/exbreak-vols/volume-hub";
	static String vol_prv = "../../../test/res/exbreak-vols/volume-prv";
	// static String vol_mob = "vol-breaks/volume-mob";

	static boolean[] boot_lights = new boolean[] { false, false };
	static Thread thr_synodes;

	static Dev dev_x;
	static Dev dev_y;
	static Docheck ck_hub;
	static Docheck ck_prv;
	static IAssert azert = new AssertImpl();

	private static String[] dev_reses;

	static T_PhotoMeta docm; // = new T_PhotoMeta("clientconn-x");
	static DeviceTableMeta devm; // = new DeviceTableMeta("clientconn-x");

	@BeforeAll
	static void installSynodes() throws Exception {
		System.setProperty("WEB-INF", webinf);

		System.setProperty("VOLUME_HUB", vol_hub);
		System.setProperty("VOLUME_PRV", vol_prv);
		// System.setProperty("VOLUME_MOB", vol_mob);
		

		connect_xml = FilenameUtils.rel2abs(webinf, connect_xml);
		connect_bak = FilenameUtils.rel2abs(webinf, connect_bak);
		Files.move(Paths.get(connect_xml), Paths.get(connect_bak), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(Paths.get(connect_breaks), Paths.get(connect_xml), StandardCopyOption.REPLACE_EXISTING);

		// settings.json
		hubpath = FilenameUtils.rel2abs(webinf, hubs);
		prvpath = FilenameUtils.rel2abs(webinf, prvs);
		// mobpath = FilenameUtils.rel2abs(SynotierSettingsTest.webinf, mobs);

		Files.copy(Paths.get(backup_hub), Paths.get(hubpath), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(Paths.get(backup_prv), Paths.get(prvpath), StandardCopyOption.REPLACE_EXISTING);
		// Files.copy(Paths.get(backup_mob), Paths.get(mobpath), StandardCopyOption.REPLACE_EXISTING);
		
		String[] vols = new String[] { vol_hub, vol_prv }; // , vol_mob
		for (String p : vols) {
			String doc_db = FilenameUtils.rel2abs(webinf, p, "doc-jserv.db"); 
			try {Files.delete(Paths.get(doc_db)); } catch (NoSuchFileException | FileNotFoundException e) {}
			touchFile(doc_db);

			String main_db = FilenameUtils.rel2abs(webinf, p, "jserv-main.db");
			try {Files.delete(Paths.get(main_db)); } catch (NoSuchFileException | FileNotFoundException e) {}
			touchFile(main_db);
		}
		
		dev_x = new Dev("sys-X", "dev-y", admin, _8964, "X-0", zsu,
								"src/test/res/anclient.java/Amelia Anisovych.mp4");
		dev_y = new Dev("sys-X", "dev-x", admin, _8964, "X-0", zsu,
								"src/test/res/anclient.java/Amelia Anisovych.mp4");
		
		dev_reses = new String[] {
				"src/test/res/anclient.java/Amelia Anisovych.mp4",
				"src/test/res/anclient.java/3-birds.wav",
				"src/test/res/anclient.java/2-ontario.gif",
				"src/test/res/anclient.java/1-pdf.pdf",
		};

		docm = new T_PhotoMeta("clientconn-x");
		devm = new DeviceTableMeta("clientconn-x");
		
	}
	
	@AfterAll
	static void clean() throws IOException, InterruptedException {
		Files.move(Paths.get(connect_bak), Paths.get(connect_xml), StandardCopyOption.REPLACE_EXISTING);
		Files.delete(Paths.get(hubpath));
		Files.delete(Paths.get(prvpath));
		// Files.delete(Paths.get(mobpath));
	}

	@SuppressWarnings("unused")
	@Test
	void testBreakResume() throws Exception {
		final boolean[] lights = new boolean[2];
//		logrst("[DoclientierTest] Starting synode-tiers", 0);

		turnred(lights);
		thr_synodes = new Thread(() -> {
			try {
				jhub = SynotierJettyApp._main(new String[] {hubs}, false);
//				((T_SynDomanager)jhub.syngleton().domnger0()).domUpdater((d, s, p, xp)->{
//					logi("%s->%s", s, p);
//					turngreen(lights, 0);
//				});
				jprv = SynotierJettyApp._main(new String[] {prvs}, true);
				((T_SynDomanager)jprv.syngleton().domnger0()).domUpdater((d, s, p, xp)->{
					logi("%s->%s", s, p);
					turngreen(lights, 0);
					turngreen(lights, 1);
				});

				ck_hub = new Docheck(azert, zsu, jhub.syngleton().syncfg.synconn,
							jhub.syngleton().domanager(zsu).synode,
							SynodeMode.hub, 3, docm, devm, true);

				ck_prv = new Docheck(azert, zsu, jprv.syngleton().syncfg.synconn,
							jhub.syngleton().domanager(zsu).synode,
							SynodeMode.peer, 3, docm, devm, true);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}, "Hub & Prv by ExchangeBreakResumeTest");
		thr_synodes.start();

		while (jhub == null || jprv == null)
			Thread.sleep(500);

		musteqs(jhub.syngleton().syncfg.domain, zsu);
		musteqs(jprv.syngleton().syncfg.domain, zsu);
		
		String hub_jserv = null;
		String prv_jserv = null;
		while (isblank(hub_jserv) || isblank(prv_jserv)) {
			hub_jserv = jhub.jserv();
			prv_jserv = jprv.jserv();
		}

		ExpSyncDoc dx = clientPush(dev_x, hub_jserv, dev_reses);
		ExpSyncDoc dy = clientPush(dev_y, prv_jserv, dev_reses);
		
		awaitAll(lights, -1);

		assertEquals(ck_hub.docs(), 4);
		assertEquals(ck_prv.docs(), 4);

		// must reach broken cases
		T_SynDomanager m = (T_SynDomanager) jprv.syngleton().domnger0();
		assertTrue(m.breakpoints[0]);
		assertTrue(m.breakpoints[1]);
		assertTrue(m.breakpoints[2]);
	}

	/////////////////// helpers
	///
	static ExpSyncDoc clientPush(Dev dev, String to_jserv, String... reses) throws Exception {

		dev.login(to_jserv, errLog);
		dev.client.fileProvider(new IFileProvider() {});
		
		ExpSyncDoc xdoc = null; 
		for (String res : reses) {
			logi("client pushing: uid %s, device %s",
					dev.client.client.ssInfo().uid(), dev.client.client.ssInfo().device);
			logi(reses);

			xdoc = DoclientierTest.videoUpByApp(dev.client, dev.device, res, docm.tbl, ShareFlag.publish);
			assertEquals(dev.device.id, xdoc.device());
			assertEquals(res, xdoc.fullpath());

			DoclientierTest.verifyPathsPage(dev.client, docm.tbl, res);
		}
		return xdoc;
	}

}
