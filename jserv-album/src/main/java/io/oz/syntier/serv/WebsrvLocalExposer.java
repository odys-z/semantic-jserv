package io.oz.syntier.serv;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.ifnull;

import java.io.IOException;
import java.util.HashMap;

import io.odysz.anson.Anson;
import io.odysz.anson.JsonOpt;
import io.odysz.semantics.x.SemanticException;
import io.oz.jserv.docs.syn.singleton.AppSettings;
import io.oz.jserv.docs.syn.singleton.ISynodeLocalExposer;

public class WebsrvLocalExposer implements ISynodeLocalExposer {

	@Override
	public AppSettings onExpose(AppSettings settings, String domain, String synode)
			throws IOException, SemanticException {

		if (settings.envars == null)
			settings.envars = new HashMap<String, String>(1);

		String ip = AppSettings.getLocalIp(2);
		try {
			settings.envars.put(settings.startHandler[2],
					f("%s:%s", ifnull(ip,  "localhost"), settings.startHandler[3]));
			settings.toFile(settings.json, JsonOpt.beautify());
		} catch (IOException e) {
			e.printStackTrace();
		}

		ExternalHosts hosts = Anson.fromPath(settings.startHandler[1]); 
		if (hosts.syndomx.get("domain") == null)
			throw new SemanticException("Cannot find domain %s's jserv per synode configuration.", domain);
		if (!eq(domain, hosts.syndomx.get("domain")))
			throw new SemanticException("Exposing target domain %s != %s, the current domain.", domain);

		String jserv = settings.jserv(synode);

		hosts.host = synode;
		hosts.localip = ip;
		hosts.syndomx.put(synode, jserv);
		// leaving resources untouched
		hosts.toFile(settings.startHandler[1], JsonOpt.beautify());
		
		return settings;
	}
}
