package io.oz.jserv.docs.syn.singleton;

import java.io.IOException;

public interface ISynodeLocalExposer {
	/**
	 * Note: this method is not allowed to throw exceptions, as 
	 * the synode is already running without errors.
	 * 
	 * @param settings
	 * @param configArgs settings.json/onload: args
	 * e.g. "onload": ["io.oz.jserv.docs.syn.singleton.WebsrvLocalHander", "WEBROOT_HUB", "ip:port"]
	 * @return  settings
	 * @throws IOException 
	 */
	AppSettings onExpose(AppSettings settings, String localJserv) ;
}
