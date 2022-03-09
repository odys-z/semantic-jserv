package io.odysz.semantic.jsession;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.x.SemanticException;

@WebServlet(description = "session manager", urlPatterns = { "/ping.serv" })
public class HeartLink extends ServPort<HeartBeat> {

	public HeartLink() {
		super(Port.heartbeat);
	}

	@Override
	protected void onGet(AnsonMsg<HeartBeat> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		jsonResp(msg, resp);
	}

	@Override
	protected void onPost(AnsonMsg<HeartBeat> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		jsonResp(msg, resp);
	}

	void jsonResp(AnsonMsg<HeartBeat> msg, HttpServletResponse resp) {
		try {
			verifier.verify(msg.header());
			AnsonMsg<AnsonResp> rep = new AnsonMsg<AnsonResp>(p, MsgCode.ok);
			write(resp, rep);
		} catch (SsException e) {
			AnsonMsg<AnsonResp> rep = new AnsonMsg<AnsonResp>(p, MsgCode.exSession);
			write(resp, rep);
		}
	}
}
