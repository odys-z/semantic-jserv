package io.oz.syntier.serv;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.odysz.common.Utils;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.JServUrl;
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

	static {
		errCtx = new ErrorCtx() {
			@Override
			public void err(MsgCode c, String rep, String...args) {
				fail(String.format("code %s, msg: %s", c.name(), rep));
			}
		};
	}
	
	@Test
	void testAppSettings() throws Exception {
		AppSettings hset = AppSettings.load(webinf, "settings.json");

		assertEquals("../../../../volumes-0.7/volume-hub", hset.volume);

		Utils.logi(JServUrl.getLocalIp());
	}
}
