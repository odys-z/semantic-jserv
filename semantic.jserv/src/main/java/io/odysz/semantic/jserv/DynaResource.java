package io.odysz.semantic.jserv;

import io.odysz.anson.Anson;

/**
 * @since 0.2.2
 */
public class DynaResource extends Anson {
	/**
	 * Add a comment, the usage of each field to the json file.
	 */
	public String help;
	
	/**
	 * Handler. For 0.2.2, always "io.oz.sdoc.htmlsrv.ResPathsTier" (doctree.jserv).
	 */
	public String handler;
	
	/**
	 * OPTIONAL
	 * The local synode's jserv root, e.g. http://127.0.0.1:8964/jserv-album, for the docs it published.
	 * Named synode before 0.2.1 was released.
	 */
	public String syn_jserv;
	
	/**
	 * Not null. The folder of the published docs: packages are unzipped here, each in a sub folder,
	 * and served as static htmls at {@link #danyroot}, the same as {@link StaticResource#resource}.
	 * Kept when the server restarts: a sub folder of a doc the synode no longer publishes is removed when
	 * the synode is queried. It's the user's responsibility to check the folder is not used by other
	 * static resources.
	 * 
	 * JSON-PICKING-0.8.2 WEB-INF/html-service.json#dynaResource.resource
	 */
	public String resource;
	
	/**
	 * 0 for no polling back to the synode 
	 * 
	 * JSON-PICKING-0.8.2 WEB-INF/html-service.json#dynaResource.pollInterval_snd
	 */
	public int pollInterval_snd;

	/**
	 * Allow show path's directory content, of the folders in a published doc. The docs are
	 * always listed at {@link #danyroot}, e. g. /pub/, a page written for each request
	 * ({@link ResIndex}, since 0.2.2).
	 * 
	 * JSON-PICKING-0.8.2 WEB-INF/html-service.json#dynaResource.allowDir
	 */
	public boolean allowDir;
	
	/**
	 * No null, and it's the user's resonsibility to checke the root is not used by other static resources
	 * 
	 * JSON-PICKING-0.8.2 WEB-INF/html-service.json#dynaResource.danyroot
	 */
	public String danyroot;

	/**
	 * STUB (media story), OPTIONAL, reserved: how many stories' media maps are kept in memory
	 * ({@link StoryMedia}). 0, the default: none, a story's map is read from its json file for
	 * each media request. A future extension, an LRU of this size.
	 * TODO: html-service.json, and its picking item, when the cache is implemented.
	 */
	public int storyMediaCache;

	public DynaResource() {}
}
