package io.odysz.jquiz.protocol;

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
public enum Quizport implements IPort {
	echo("echo.serv"),
	heartbeat("ping.serv11"), session("login.serv11"),
	/**port provided by {@link io.odysz.jsample.SysMenu} */
	menu("menu.serv"),
	file("file.serv11"),
	quiz("quiz.serv");

	static {
		JSONAnsonListener.registFactory(Quizport.class, 
			(s) -> {
					return Quizport.valueOf(s);
			});
	}
	
	private String url;
	Quizport(String v) { url = v; };
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
