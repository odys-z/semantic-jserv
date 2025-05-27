package io.oz.syntier.serv;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.Utils.logi;
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

//		try {
//			String ip = AppSettings.getLocalIp(2);
//			settings.envars.put(settings.startHandler[2],
//					f("%s:%s", ifnull(ip,  "localhost"), settings.webport));
//			settings.toFile(settings.json, JsonOpt.beautify());

//		} catch (IOException e) {
//			e.printStackTrace();
//			ip = "127.0.0.1";
//		}

		try {
			ExternalHosts hosts = Anson.fromPath(settings.startHandler[1]);

			if (hosts.syndomx.get("domain") == null)
				warn("Cannot find domain %s's jserv per synode configuration.", domain);
			if (!eq(domain, hosts.syndomx.get("domain")))
				warn("Exposing target domain %s != %s, the current domain.", domain);

			// FIXME No! should get from syn_db.syn_node[jserv]
			String jserv = settings.jserv(synode);

			hosts.host = synode;
			hosts.localip = settings.localIp;
			hosts.syndomx.put(synode, jserv);
			
			String host_json = settings.getLocalHostJson();
			// save to file by leaving resources untouched
			hosts.toFile(host_json, JsonOpt.beautify());
			
			logi("Exposed service to %s", host_json);
			logi(hosts.toBlock(JsonOpt.beautify()));
		} catch (IOException e) {
			e.printStackTrace();
		} 
		
		return settings;
	}
}
