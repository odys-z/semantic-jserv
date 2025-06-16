package io.oz.jserv.docs.syn.singleton;

import java.io.IOException;

import io.odysz.semantics.x.SemanticException;

public interface ISynodeLocalExposer {

	/**
	 * Expose jserv-root to local [path to web-dist]/private/host.json.
	 * <p>Note: this method is not allowed to throw exceptions, as 
	 * the synode is already running without errors.</p>
	 * 
	 * @param settings
	 * @param configArgs settings.json/onload: args
	 * e.g. "startHandler": ["io.oz.syntier.serv.WebsrvLocalExposer", "web-dist", "WEBROOT_HUB", "8901"]
	 * @return  settings
	 * @throws IOException File IO errors
	 * @throws SemanticException Handling configuration errors
	 */
	AppSettings onExpose(AppSettings settings, String domain, String synode);
}
