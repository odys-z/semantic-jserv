package io.oz.syntier.serv;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.odysz.common.Utils;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.oz.jserv.docs.syn.singleton.AppSettings;
import io.oz.jserv.docs.syn.singleton.Syngleton;

class SynotierSettingsTest {
	static final String web_inf = "WEB-INF";
	static final String webinf = "./src/main/webapp/" + web_inf;
	static final String config_xml = "config.xml";

	static final String settings_hub = "settings.hub.json";
	static final String settings_prv = "settings.prv.json";

	static ErrorCtx errCtx;
	
	Syngleton syngleton;

	// boolean[] light = new boolean[] {false};

	static {
		errCtx = new ErrorCtx() {
			@Override
			public void err(MsgCode c, String rep, String...args) {
				fail(String.format("code %s, msg: %s", c.name(), rep));
			}
		};
	}
	
//	@BeforeAll
//	static void initEnv() throws IOException {
//		System.setProperty("VOLUME_HUB", "../../../../volumes-0.7/volume-hub");
//		System.setProperty("VOLUME_PRV", "../../../../volumes-0.7/volume-prv");
//		String backup = FilenameUtils.rel2abs(web_inf, "settings-test-app.json");
//		String tsting = FilenameUtils.rel2abs(web_inf, "settings.json");
//		Files.copy(Paths.get(backup), Paths.get(tsting), StandardCopyOption.REPLACE_EXISTING);
//	}
	
	@Test
	void testAppSettings() throws Exception {
		AppSettings hset = AppSettings.load(webinf, "settings.json");

		assertEquals("../../../../volumes-0.7/volume-hub", hset.volume);

		Utils.logi(AppSettings.getLocalIp());
	}

}
