package io.odysz.semantic.jserv.echo;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jprotocol.JMessage.MsgCode;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantic.jserv.helper.ServletAdapter;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;

public class Echo extends HttpServlet {
	/** * */
	private static final long serialVersionUID = 1L;

	JHelper<EchoReq> jhelperReq;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		resp(req, resp);
	}

	private void resp(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		try {
			resp.setCharacterEncoding("UTF-8");

			JMessage<EchoReq> msg = ServletAdapter.<EchoReq>read(req, jhelperReq, EchoReq.class);
			ServletAdapter.write(resp, new SemanticObject().put("echo", msg.toStringEx()));
			resp.flushBuffer();
		} catch (SemanticException | IOException | ReflectiveOperationException e) {
			ServletAdapter.write(resp, JProtocol.err(Port.echo, MsgCode.exGeneral, e.getMessage()));
			e.printStackTrace();
		}
	}
}
