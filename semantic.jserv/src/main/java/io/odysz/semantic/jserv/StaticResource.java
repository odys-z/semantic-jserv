package io.odysz.semantic.jserv;

import io.odysz.anson.Anson;

/**
 * @since 0.2.2
 */
public class StaticResource extends Anson {
	public String path;
	public String resource;

	/** Allow show path's directory content */
	boolean allowDir;
	
	public StaticResource() {}
}
