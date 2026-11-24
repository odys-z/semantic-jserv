package io.odysz.semantic.jserv;

import static io.odysz.common.LangExt.isNull;

import java.util.ArrayList;

//import org.eclipse.jetty.server.Server;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonField;

public class WebConfig extends Anson {

	public StaticResource[] paths;
	public int port;

//	@AnsonField(ignoreTo=true, ignoreFrom=true)
//	Server server;

	@AnsonField(ignoreTo=true, ignoreFrom=true)
	public String[] error;

	/**
	 * Resource handler's class name and its args,
	 * which implements {@link io.oz.srv.IResUpdater}.
	 */
	public String[] startHandler;

	/**
	 * Optional. The handlers run when the service starts, in this order, each a resource handler's
	 * class name and its args, as {@link #startHandler}, e. g.<pre>
	 * "startHandlers": [["io.oz.srv.WebAssetsHandler"],
	 *                   ["io.oz.srv.JservLocalHandler", "private/host.json"]]</pre>
	 * {@link #startHandler}, the only one before this, is still run, first.
	 * @since 0.2.2
	 */
	String[][] startHandlers;

	/**
	 * @return the start handlers' configurations, each [class name, args...], in the order to run:
	 * {@link #startHandler}, then {@link #startHandlers}
	 */
	public ArrayList<String[]> startHandlers() {
		ArrayList<String[]> handlers = new ArrayList<String[]>();
		if (!isNull(startHandler))
			handlers.add(startHandler);
		if (!isNull(startHandlers))
			for (String[] h : startHandlers)
				if (!isNull(h))
					handlers.add(h);
		return handlers;
	}

	/**
	 * JSON-PICKING-0.8.2 WEB-INF/html-service.json#welcomepages
	 */
	public String[] welcomepages;

	/**
	 * Optional. Dynamic resources: paths published / removed while the server is running, by
	 * {@link DynaResource#handler}, an {@link io.oz.srv.IResUpdater} only loaded if this is configured,
	 * e. g. for doctree.jserv's io.oz.sdoc.htmlsrv.ResPathsTier<pre>
	 * { "handler": "io.oz.sdoc.htmlsrv.ResPathsTier",
	 *   "syn_jserv": "http://127.0.0.1:8964/jserv-album", "pollInterval_snd": 3600,
	 *   "resource": "respaths", "danyroot": "pub", "allowDir": false }</pre>
	 * The published docs are listed at /pub/ (or /pub/index.html), see {@link ResIndex}.
	 * @since 0.2.2
	 */
	public DynaResource dynaResource;

	/**
	 * Optional. The page of every 404, a path of this server, e.g. "/404.html", in the web-dist root,
	 * built by synode.py (ISSUE sdoc-sphinx-venv). The status stays 404. Null: Jetty's page.
	 * @since 0.2.1
	 */
	public String notfound;
	
	public WebConfig() {}

	public void error(String name, String message) {
		this.error = new String[] {name, message};
	}
}
