package io.odysz.jsample.protocol;

import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jprotocol.JMessage.Port;

public enum Samport implements IPort {
	menu("menu.sample"); 

	private String url;
	Samport(String v) { url = v; };
	public String url() { return url; }
	@Override
	public IPort valof(String pname) {
		Samport p = valueOf(pname);
		if (p == null)
			return Port.valueOf(pname);
		else return p;
	}	
}
