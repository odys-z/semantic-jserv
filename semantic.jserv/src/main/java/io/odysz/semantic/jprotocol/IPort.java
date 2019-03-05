package io.odysz.semantic.jprotocol;

public interface IPort {

//		String url = null;
		default public String url() { return "echo.jserv"; }

		public String name();

		/**Equivalent of enum.valueOf(), except for subclass returning instance of jserv.Port.*/
		public IPort valof(String pname);

//		public Port valueOf(String pport); // { return null; }
}
