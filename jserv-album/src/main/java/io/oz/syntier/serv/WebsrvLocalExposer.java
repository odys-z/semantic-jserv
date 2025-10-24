package io.oz.syntier.serv;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.Utils.logT;
import static io.odysz.common.Utils.warn;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;

import io.odysz.anson.Anson;
import io.odysz.anson.JsonOpt;
import io.odysz.common.FilenameUtils;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.syn.SynDomanager;
import io.oz.jserv.docs.syn.singleton.AppSettings;
import io.oz.jserv.docs.syn.singleton.ISynodeLocalExposer;

public class WebsrvLocalExposer implements ISynodeLocalExposer {

	@Override
	public AppSettings onExpose(AppSettings settings, SynDomanager domanager) {

		String domain = domanager.domain();
		if (settings.envars == null)
			settings.envars = new HashMap<String, String>(1);

		try {
			ExternalHosts hosts = Anson.fromPath(FilenameUtils.rel2abs(settings.startHandler[1]));

			String host_dom = hosts.syndomx.get("domain");
			if (host_dom == null)
				warn("Cannot find domain %s's jserv per synode configuration.", domain);
			if (!eq(domain, host_dom))
				warn("Exposing target domain %s != %s, the current domain.", domain, host_dom);

			hosts.host = domanager.synode;

			settings.loadDBLaterservs(domanager.syngleton.syncfg, domanager.synm);
			
			for (String n : settings.jservs.keySet()) {
				String jserv = settings.jservs.get(n);

				logT(new Object(){}, "Exposing jservs");
				logT("%s: %s", n, jserv);
				hosts.syndomx.put(n, jserv);
			}
			
			String host_json = settings.getLocalHostJson();
			// save to file by leaving resources untouched
			hosts.toFile(host_json, JsonOpt.beautify());
			
			logT(new Object(){}, "Exposed service to %s", host_json);
			logT(new Object(){}, hosts.toBlock(JsonOpt.beautify()));
		} catch (IOException | SQLException | TransException e) {
			e.printStackTrace();
		} 
		
		return settings;
	}
}
