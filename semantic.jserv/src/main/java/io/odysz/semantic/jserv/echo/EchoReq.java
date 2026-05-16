package io.odysz.semantic.jserv.echo;


import io.odysz.anson.AnsonCtor;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

public class EchoReq extends AnsonBody {

	public static class A {
		public static final String echo = "echo";

		/**
		 * Query interfaces, only response to localhost
		 */
		public static final String inet = "inet";
	}
	
	/**
	 * Not used in Java
	 */
	String echo;

	@AnsonCtor(base={"na"}, initialist="string echo = m")
	public EchoReq(String m) {
		super(null, null);
		echo = m;
	}

	/**
	 * JSON:
	 * [[], [""]]
	 * 
	 * c++: 
	 * EchoReq() : AnsonBody("", EchoReq::_type_) {}
	 */
	@AnsonCtor(base= {""} )
	public EchoReq() {
		super(null, null);
	}

	public EchoReq(AnsonMsg<? extends AnsonBody> parent) {
		super(parent, null);
	}
}
