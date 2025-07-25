package io.odysz.jsample.protocol;

import java.io.IOException;
import java.io.OutputStream;

import io.odysz.anson.AnsonException;
import io.odysz.anson.IJsonable;
import io.odysz.anson.JSONAnsonListener;
import io.odysz.anson.JsonOpt;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantics.x.SemanticException;

/**Sample project's prots extension
 * This enum replaced jserv {@link io.odysz.semantic.jprotocol.AnsonMsg.Port}. */
public enum Samport implements IPort {
	heartbeat("ping.serv"), session("login.serv"),
	/**port provided by {@link io.odysz.jsample.SysMenu} */
	menu("menu.serv"),
	example("example.serv11"),
	file("file.serv11"),
	/**workflow port, see {@link io.odysz.sworkflow.CheapEngin} */
	cheapflow("cheapflow.samplev11"), 
	/** sample servlet tools.serv */
	tools("tools.serv"),
	vec3("vec3.serv");

	static {
		JSONAnsonListener.registFactory(Samport.class, 
			(s) -> {
				return Samport.valueOf(s);
			});
	}
	
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

	@Override
	public IJsonable toBlock(OutputStream stream, JsonOpt... opts)
			throws AnsonException, IOException {
		stream.write('\"');
		stream.write(name().getBytes());
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
