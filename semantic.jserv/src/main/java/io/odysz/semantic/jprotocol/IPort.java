package io.odysz.semantic.jprotocol;

import static io.odysz.common.LangExt.f;

import io.odysz.anson.IJsonable;
import io.odysz.semantics.x.SemanticException;

public interface IPort extends IJsonable {

	/**
	 * Get port url surfix, e.g. "echo.jserv".
	 * @return url surfix, the servlet pattern
	 */
	default public String url() { return "echo.jserv"; }

	/**
	 * @since 1.5.16
	 * @param jservroot
	 * @return jservroot/{@link #url()}
	 */
	default public String url(String jservroot) { return f("%s/%s", jservroot, url()); }

	public String name();

	/**
	 * Equivalent of enum.valueOf(), except for subclass returning instance of jserv.Port.
	 * @throws SemanticException port name not found
	 */
	public IPort valof(String pname) throws SemanticException;
}
