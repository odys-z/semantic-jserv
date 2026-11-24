package io.odysz.semantic.jserv;

import java.io.IOException;

/** 
 * Resources updater, e. g. update private/host.json, as
 * the service can start up on a very different IP address.
 * 
 * Static web server like Html-service call this for be acknowledged with resources updating. 
 * @since 1.6.1
 */
public interface IResUpdater {

	void onUpdate(WebConfig wcfg) throws IOException;

	/**
	 * Called for a start handler of {@link WebConfig#startHandlers}, with its own configuration.
	 * Default: {@link #onUpdate(WebConfig)}.
	 * 
	 * @param wcfg
	 * @param handler the handler's configuration, [class name, args...]
	 * @since 0.2.2
	 */
	default void onUpdate(WebConfig wcfg, String[] handler) throws IOException {
		onUpdate(wcfg);
	}
}
