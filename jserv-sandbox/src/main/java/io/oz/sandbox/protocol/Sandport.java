package io.oz.sandbox.protocol;

import java.io.IOException;
import java.io.OutputStream;

import io.odysz.anson.IJsonable;
import io.odysz.anson.JSONAnsonListener;
import io.odysz.anson.JsonOpt;
import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantics.x.SemanticException;

/**Sample project's prots extension
 * This enum replaced jserv {@link io.odysz.semantic.jprotocol.AnsonMsg.Port}. */
public enum Sandport implements IPort {
	/** sample servlet tools.serv */
	tools("tools.serv"),
	vec3("vec3.less"),
	/** The new experimental serv, extending semantics to the client side */
	userstier("users.less"),
	/** editor.less */
	editor("editor.less");

	static {
		JSONAnsonListener.registFactory(Sandport.class, 
			(s) -> {
				return Sandport.valueOf(s);
			});
	}
	
	private String url;
	Sandport(String v) { url = v; };
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

	@Override
	public IJsonable toBlock(OutputStream stream, JsonOpt... opts) throws AnsonException, IOException {
		stream.write('\"');
		stream.write(url.getBytes());
		stream.write('\"');
		return this;
	}

	@Override
	public IJsonable toJson(StringBuffer buf) throws IOException, AnsonException {
		buf.append('\"');
		buf.append(url);
		buf.append('\"');
		return this;
	}	
}
