package io.oz.jserv.docs.syn.singleton;

import java.io.IOException;

import io.odysz.semantics.x.SemanticException;

public interface ISynodeLocalExposer {
	/**
	 * Note: this method is not allowed to throw exceptions, as 
	 * the synode is already running without errors.
	 * 
	 * @param settings
	 * @param configArgs settings.json/onload: args
	 * e.g. "onload": ["io.oz.jserv.docs.syn.singleton.WebsrvLocalHander", "WEBROOT_HUB", "ip:port"]
	 * @return  settings
	 * @throws IOException File IO errors
	 * @throws SemanticException Handling configuration errors
	 */
	AppSettings onExpose(AppSettings settings, String domain, String synode);
//			throws IOException, SemanticException;
}
