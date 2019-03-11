package io.odysz.semantic.jprotocol;

import io.odysz.semantics.x.SemanticException;

public interface IPort {

//		String url = null;
		default public String url() { return "echo.jserv"; }

		public String name();

		/**Equivalent of enum.valueOf(), except for subclass returning instance of jserv.Port.
		 * @throws SemanticException */
		public IPort valof(String pname) throws SemanticException;

//		public Port valueOf(String pport); // { return null; }
}
