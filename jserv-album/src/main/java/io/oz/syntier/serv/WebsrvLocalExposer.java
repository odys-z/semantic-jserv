package io.oz.syntier.serv;

import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.ifnull;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;

import io.odysz.anson.JsonOpt;
import io.oz.jserv.docs.syn.singleton.AppSettings;
import io.oz.jserv.docs.syn.singleton.ISynodeLocalExposer;

public class WebsrvLocalExposer implements ISynodeLocalExposer {

	@Override
	public AppSettings onExpose(AppSettings settings, String jserv) {
		if (settings.envars == null)
			settings.envars = new HashMap<String, String>(1);

		try {
			String ip = AppSettings.getLocalIp();
			settings.envars.put(settings.startHandler[2],
					f("%s:%s", ifnull(ip,  "localhost"), settings.startHandler[3]));
			settings.toFile(settings.json, JsonOpt.beautify());

		} catch (IOException e) {
			e.printStackTrace();
		}

		try (FileOutputStream host = new FileOutputStream(settings.startHandler[1])) {
			host.write(f("{\n  \"host\": \"%s\"\n}", jserv).getBytes());
		} catch (Exception e) {
			e.printStackTrace();
		}

		return settings;
	}
}
