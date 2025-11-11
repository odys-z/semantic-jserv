package io.oz.jserv.docs.syn.singleton;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;

import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.musteqs;
import static io.odysz.common.LangExt.mustnonull;
import static io.odysz.common.LangExt.mustnull;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.common.Configs;
import io.odysz.common.DateFormat;
import io.odysz.common.FilenameUtils;
import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jprotocol.JProtocol.OnError;
import io.odysz.semantic.jprotocol.JServUrl;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.util.DAHelper;
import io.odysz.transact.sql.Delete;
import io.oz.album.peer.SynDocollPort;
import io.oz.syn.SyncUser;
import io.oz.syn.Synode;
import io.oz.syn.SynodeMode;
import io.oz.syn.registry.SynodeConfig;
import io.oz.syn.registry.YellowPages;

class AppSettingsTest {
	static final String vol_diction_json = "src/test/res/settings-merge-jservs-vol"; 
	static final String web_inf = "WEB-INF";
	static final String webinf = "./src/test/res/" + web_inf;
	static final String config_xml = "config.xml";

	static final String backup_hub = "src/test/resources/exbreak-hub.backup.json";

	static String connect_xml = "connects.xml";
	static String connect_merges = vol_diction_json + "/connects-merge.xml";
	static String connect_bak = "connects.xml-settings-merge-backup";

	AppSettings settings; 
	SynodeConfig cfg;
	static String hub_ip = "19.89.06.04";

	private SyncUser synadmin;
	private SynodeMeta synmeta;

	static private OnError err;

	DATranscxt tb; 
	
	static String get_ip(int n) {
		return f("%1$s.%1$s.%1$s.%1$s", n);
	}

	static int changes = 10;
	@BeforeAll
	static void init() throws Exception {
		JProtocol.setup("jserv-album", SynDocollPort.docoll);
		JServUrl.localIpFinder = 
			(int... retries) -> {
				// return f("%1$s.%1$s.%1$s.%1$s", changes);
				return get_ip(changes);
			};
		err = (c, m, a) -> { fail(m); };
	}

	@AfterAll
	static void clean() throws IOException, InterruptedException {
	}
	
	/**
	 * Requires the Portofolio setup program use current time to save settings.json.
	 * @throws Exception
	 */
	@Test
	void testMerge_ip_json2db() throws Exception {
		String p = new File(vol_diction_json).getAbsolutePath();
    	System.setProperty("VOLUME_HOME", p);

		connect_xml = FilenameUtils.rel2abs(webinf, connect_xml);
		connect_bak = f("%s-%s", connect_bak, DateFormat.formatime_utc(new Date()).replaceAll(":", "-"));
		connect_bak = FilenameUtils.rel2abs(webinf, connect_bak);
		Files.move(Paths.get(connect_xml), Paths.get(connect_bak), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(Paths.get(connect_merges), Paths.get(connect_xml), StandardCopyOption.REPLACE_EXISTING);

		YellowPages.load(vol_diction_json);
		cfg = YellowPages.synconfig().replaceEnvs();
		
		Configs.init(webinf);
		Connects.init(webinf);

		settings = new AppSettings();
		settings.centralPswd = System.getProperty("central_pswd");
		mustnonull(settings.centralPswd);
		settings.regiserv = "http://182.150.29.34:1989/regist-central";

		settings.reverseProxy = false;
		settings.port = 8964;
		settings.json = vol_diction_json + "/ignore-by-merge-settings.json";
		
		tb = new DATranscxt(cfg.synconn);

		install_peers();
		
		test_userConfig();
//		settings.localIp = get_ip(changes);
//		settings.jserv_utc = "1989-06-04";

		mustnonull(settings.localIp);
		mustnonull(settings.jserv_utc); 

		test_ipchange_after_config();

		try {
			Files.move(Paths.get(connect_bak), Paths.get(connect_xml), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {}
	}

	void install_peers() throws Exception {
		synmeta  = new SynodeMeta(cfg.synconn);
		synadmin = new SyncUser("admin", settings.centralPswd);
		Delete d = tb
		  .delete(synmeta.tbl, synadmin)
		  .whereEq(synmeta.domain, cfg.domain)
		  .whereEq(synmeta.org,  cfg.org.orgId);
		
		for (Synode p : cfg.peers) 
			d.post(AppSettings.insert_synode(
				  tb, synmeta, synadmin, cfg.synconn,
				  cfg.org.orgId, cfg.domain, p.synid,
				  isblank(p.remarks) ? null : SynodeMode.valueOf(p.remarks),
				  settings.jserv(p.synid), settings.jserv_utc,
				  cfg.synid))
		  .d(tb.instancontxt(cfg.synconn, synadmin));
	}	
	
	/**
	 * Requires the Portofolio setup program use current time to save settings.json.
	 * @throws Exception
	 */
	void test_userConfig() throws Exception {
		mustnull(settings.localIp);
		String jserv_hub = f("http://%1$s:8964/jserv-album", hub_ip);
		// test-jserv-hub
		
		musteqs(cfg.peers[0].remarks, SynodeMode.hub.name());
		String hub = cfg.peers[0].synid;
		settings
			.jserv(hub, jserv_hub) // user configure
			.jserv_utc("1989-06-04")
			.reverseProxy = false;
		
		assertTrue(settings.merge_ip_json2db(cfg, synmeta, synadmin, err));

		assertEquals(jserv_hub,
					DAHelper.getValstr(tb, cfg.synconn, synmeta, synmeta.jserv,
						synmeta.domain, cfg.domain, synmeta.org, cfg.org.orgId,
						synmeta.synoder, hub));

		assertEquals(jserv_hub, settings.jserv(hub));
	}
	
	void test_ipchange_after_config() throws Exception {
		settings.reverseProxy = false;

		String my_ip = get_ip(changes);
		String jserv_me = f("http://%1$s:8964/jserv-album", my_ip);
		Utils.logrst(jserv_me, changes);

		assertFalse(settings.merge_ip_json2db(cfg, synmeta, synadmin, err));
		assertEquals(jserv_me, settings.jserv(cfg.synid));
		assertEquals(my_ip, settings.localIp);
		assertEquals(jserv_me, 
					DAHelper.getValstr(tb, cfg.synconn, synmeta, synmeta.jserv,
						synmeta.domain, cfg.domain, synmeta.org, cfg.org.orgId,
						synmeta.synoder, cfg.synid));

		Thread.sleep(4000); // minimal jserv-worker interval

		my_ip = get_ip(++changes);
		jserv_me = f("http://%1$s:8964/jserv-album", my_ip);
		Utils.logrst(jserv_me, changes);

		assertTrue(settings.merge_ip_json2db(cfg, synmeta, synadmin, err));
		assertEquals(jserv_me, settings.jserv(cfg.synid));
		assertEquals(my_ip, settings.localIp);

		assertEquals(jserv_me, 
					DAHelper.getValstr(tb, cfg.synconn, synmeta, synmeta.jserv,
						synmeta.domain, cfg.domain, synmeta.org, cfg.org.orgId,
						synmeta.synoder, cfg.synid));
	}

}
