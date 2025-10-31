package io.oz.jserv.docs.syn.singleton;

import io.oz.jserv.docs.syn.SynDomanager;

public interface ISynodeLocalExposer {

	/**
	 * Expose jserv-root to local [path to web-dist]/private/host.json.
	 * <p>Note: this method is not allowed to throw exceptions, as 
	 * the synode is already running without errors.</p>
	 * 
	 * @param settings
	 *  settings.startHandler: ["io.oz.syntier.serv.WebsrvLocalExposer", "web-dist/private/host.json"]
	 * @return  settings
	 * @since 0.7.6 All newest versions locally will be exposed to local host.json
	 */
	AppSettings onExpose(AppSettings settings, SynDomanager domx);
}
