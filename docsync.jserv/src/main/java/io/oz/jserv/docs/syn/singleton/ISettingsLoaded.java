package io.oz.jserv.docs.syn.singleton;

import java.io.IOException;

public interface ISettingsLoaded {
	/**
	 * @param settings
	 * @param configArgs settings.json/onload: args
	 * e.g. "onload": ["io.oz.jserv.docs.syn.singleton.WebsrvLocalHander", "WEBROOT_HUB", "ip:port"]
	 * @throws IOException 
	 */
	void onload(AppSettings settings) throws IOException;
}
