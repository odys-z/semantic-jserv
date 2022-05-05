package io.odysz.semantic.jserv.echo;


import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

public class EchoReq extends AnsonBody {

	public static class A {
		public static final String echo = "echo";
		/** query interfaces, only response to localhost */
		public static final String inet = "inet";
	}

	public EchoReq(AnsonMsg<? extends AnsonBody> parent) {
		super(parent, null);
	}
}
