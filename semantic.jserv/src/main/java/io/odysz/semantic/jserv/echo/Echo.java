package io.odysz.semantic.jserv.echo;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.ServPort;

public class Echo extends ServPort<EchoReq> {
	public Echo() { super(Port.echo); }

	/** * */
	private static final long serialVersionUID = 1L;

	@Override
	protected void onGet(AnsonMsg<EchoReq> req, HttpServletResponse resp)
			throws ServletException, IOException {
		resp(req, resp);
	}

	@Override
	protected void onPost(AnsonMsg<EchoReq> req, HttpServletResponse resp)
			throws ServletException, IOException {
		resp(req, resp);
	}

	private void resp(AnsonMsg<EchoReq> req, HttpServletResponse resp) throws IOException {
		try {
			resp.setCharacterEncoding("UTF-8");

			write(resp, ok(req.toString()), req.opts());
			resp.flushBuffer();
		} catch (IOException e) {
			write(resp, err(MsgCode.exGeneral, e.getMessage()));
			e.printStackTrace();
		}
	}
}
