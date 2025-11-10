package io.oz.jserv.docs.syn.singleton;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;

import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.mustnonull;
import static io.odysz.common.LangExt.mustnull;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.common.Configs;
import io.odysz.common.DateFormat;
import io.odysz.common.FilenameUtils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jprotocol.JProtocol.OnError;
import io.odysz.semantic.jprotocol.JServUrl;
import io.odysz.semantic.meta.SynodeMeta;
import io.odysz.semantic.util.DAHelper;
import io.oz.syn.SyncUser;
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

	private SyncUser synadmin;
	private SynodeMeta synmeta;
	static private OnError err;
	
	static int changes = 10;
	@BeforeAll
	static void init() throws Exception {
		JProtocol.setup("jserv-album", Port.dataset);

		JServUrl.localIpFinder = 
			(int... retries) -> {
				return f("%1$s.%1$s.%1$s.%1$s", changes);
			};
			
		err = (c, m, a) -> { fail(m); };
		
	}

	@AfterAll
	static void clean() throws IOException, InterruptedException {
		try {
			Files.move(Paths.get(connect_bak), Paths.get(connect_xml), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {}
	}
	
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

		settings.reverseProxy = false;
		settings.port = 8964;
		
		test_userConfig();
		test_noproxy();
	}

	void test_userConfig() throws Exception {
		mustnull(settings.localIp);
		String jserv_hub = f("http://%1$s.%1$s.%1$s.%1$s:8964/jserv-album", changes);
		// test-jserv-hub

		settings
			.jserv(cfg.synid, jserv_hub)
			.reverseProxy = false;
		
		DATranscxt tb = new DATranscxt(cfg.synconn);
		synadmin = new SyncUser();
		synmeta  = new SynodeMeta(cfg.synconn);

		assertTrue(settings.merge_ip_json2db(cfg, synmeta, synadmin, err));

		assertEquals(jserv_hub,
					DAHelper.getValstr(tb, cfg.synconn, synmeta, synmeta.jserv,
						synmeta.domain, cfg.domain, synmeta.org, cfg.org.orgId,
						synmeta.synoder, cfg.synid));

		assertEquals(jserv_hub, settings.jserv(cfg.synid));
	}
	
	void test_noproxy() {
		settings.reverseProxy = false;
	}

}
