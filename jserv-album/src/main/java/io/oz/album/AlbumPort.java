package io.oz.album;

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
public enum AlbumPort implements IPort {
	/** users.less */
	userstier("users.less"),
	/** editor.less */
	editor("editor.less"),
	/** album.less */
	album("album.less");

	static {
		JSONAnsonListener.registFactory(AlbumPort.class, 
			(s) -> {
				return AlbumPort.valueOf(s);
			});
	}
	
	private String url;
	AlbumPort(String v) { url = v; };
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
		// stream.write(url.getBytes());
		stream.write(name().getBytes());
		stream.write('\"');
		return this;
	}

	@Override
	public IJsonable toJson(StringBuffer buf) throws IOException, AnsonException {
		buf.append('\"');
		// buf.append(url);
		buf.append(name());
		buf.append('\"');
		return this;
	}	
}
