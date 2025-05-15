package io.oz.syntier.serv;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.ifnull;
import static io.odysz.common.Utils.warn;

import java.io.IOException;
import java.util.HashMap;

import io.odysz.anson.Anson;
import io.odysz.anson.JsonOpt;
import io.oz.jserv.docs.syn.singleton.AppSettings;
import io.oz.jserv.docs.syn.singleton.ISynodeLocalExposer;

public class WebsrvLocalExposer implements ISynodeLocalExposer {

	@Override
	public AppSettings onExpose(AppSettings settings, String domain, String synode) {

		if (settings.envars == null)
			settings.envars = new HashMap<String, String>(1);

		String ip;
		try {
			ip = AppSettings.getLocalIp(2);
			settings.envars.put(settings.startHandler[2],
					f("%s:%s", ifnull(ip,  "localhost"), settings.startHandler[3]));
			settings.toFile(settings.json, JsonOpt.beautify());
		} catch (IOException e) {
			e.printStackTrace();
			ip = "127.0.0.1";
		}

		try {
			ExternalHosts hosts = Anson.fromPath(settings.startHandler[1]);

			if (hosts.syndomx.get("domain") == null)
				warn("Cannot find domain %s's jserv per synode configuration.", domain);
			if (!eq(domain, hosts.syndomx.get("domain")))
				warn("Exposing target domain %s != %s, the current domain.", domain);

			String jserv = settings.jserv(synode);

			hosts.host = synode;
			hosts.localip = ip;
			hosts.syndomx.put(synode, jserv);
			// leaving resources untouched
			hosts.toFile(settings.startHandler[1], JsonOpt.beautify());
		} catch (IOException e) {
			e.printStackTrace();
		} 
		
		return settings;
	}
}
