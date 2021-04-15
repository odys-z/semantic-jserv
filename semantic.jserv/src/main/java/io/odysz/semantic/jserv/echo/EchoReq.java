package io.odysz.semantic.jserv.echo;


import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

public class EchoReq extends AnsonBody {

	public EchoReq(AnsonMsg<? extends AnsonBody> parent) {
		super(parent, null);
	}
}
