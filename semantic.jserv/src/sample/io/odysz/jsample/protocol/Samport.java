package io.odysz.jsample.protocol;

import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantics.x.SemanticException;

public enum Samport implements IPort {
	menu("menu.sample"), cheapflow("cheapflow.sample"); 

	private String url;
	Samport(String v) { url = v; };
	public String url() { return url; }
	@Override
	public IPort valof(String pname) throws SemanticException {
		try {
			IPort p = Port.valueOf(pname);
			return p;
		} catch (Exception e) {
			try { return valueOf(pname); }
			catch (IllegalArgumentException ex) {
				throw new SemanticException(ex.getMessage());
			}
			
		}
	}	
}
