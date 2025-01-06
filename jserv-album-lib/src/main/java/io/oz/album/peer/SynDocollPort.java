package io.oz.album.peer;

import java.io.IOException;
import java.io.OutputStream;

import io.odysz.anson.IJsonable;
import io.odysz.anson.JSONAnsonListener;
import io.odysz.anson.JsonOpt;
import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantics.x.SemanticException;

/**
 * <p>Sample project's prots extension.</p>
 * 
 * This enum replaces jserv {@link io.odysz.semantic.jprotocol.AnsonMsg.Port}.
 */
public enum SynDocollPort implements IPort {
	heartbeat("ping.serv"),
	session("login.serv"),
	/** users.less */
	userstier("users.less"),
	/** editor.less */
	editor("editor.less"),
	/** docoll.syn */
	docoll("docoll.syn"),
	/** docs.tier : {@link io.oz.jserv.docs.syn.ExpDoctier} */
	docstier("docs.tier"),
	
	menu("menu.serv"), settings("settings.less");

	static {
		JSONAnsonListener.registFactory(SynDocollPort.class, 
			(s) -> {
				return SynDocollPort.valueOf(s);
			});
	}
	
	private String url;
	SynDocollPort(String v) { url = v; };
	public String url() { return url; }
	@Override
	public IPort valof(String pname) throws SemanticException {
		try {
			return Port.valueOf(pname);
		} catch (Exception e) {
			try { return valueOf(pname); }
			catch (IllegalArgumentException ex) {
				throw new SemanticException("Error of IllegalArgumentException: %s", ex.getMessage());
			}
		}
	}

	@Override
	public IJsonable toBlock(OutputStream stream, JsonOpt... opts) throws AnsonException, IOException {
		stream.write('\"');
		stream.write(name().getBytes());
		stream.write('\"');
		return this;
	}

	@Override
	public IJsonable toJson(StringBuffer buf) throws IOException, AnsonException {
		buf.append('\"');
		buf.append(name());
		buf.append('\"');
		return this;
	}	
}
