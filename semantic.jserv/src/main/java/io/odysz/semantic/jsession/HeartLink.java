package io.odysz.semantic.jsession;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.x.SemanticException;

/**
 * 
 * url pattern: /ping.serv
 * 
 * @author odys-z@github.com
 *
 */
@WebServlet(description = "session manager", urlPatterns = { "/ping.serv" })
public class HeartLink extends ServPort<HeartBeat> {

	/** url pattern: /ping.serv */
	public HeartLink() {
		super(Port.heartbeat);
	}

	@Override
	protected void onGet(AnsonMsg<HeartBeat> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		Utils.logi("msg.addr()");
		Utils.logi(msg.addr());
		jsonResp(msg, resp);
	}

	@Override
	protected void onPost(AnsonMsg<HeartBeat> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		jsonResp(msg, resp);
	}

	void jsonResp(AnsonMsg<HeartBeat> msg, HttpServletResponse resp) {
		try {
			verifier().verify(msg.header());
			AnsonMsg<AnsonResp> rep = new AnsonMsg<AnsonResp>(p, MsgCode.ok).body(new AnsonResp());
			write(resp, rep);
		} catch (SsException e) {
			AnsonMsg<AnsonResp> rep = new AnsonMsg<AnsonResp>(p, MsgCode.exSession).body(new AnsonResp());
			write(resp, rep);
		}
	}
}
