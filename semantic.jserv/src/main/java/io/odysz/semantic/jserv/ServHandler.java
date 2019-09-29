package io.odysz.semantic.jserv;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.Anson;
import io.odysz.anson.JsonOpt;
import io.odysz.anson.x.AnsonException;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantics.x.SemanticException;

public abstract class ServHandler<T extends AnsonBody> extends HttpServlet {
	protected IPort p;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		InputStream in;
		String headstr = req.getParameter("header");
		if (headstr != null && headstr.length() > 1) {
			byte[] b = headstr.getBytes();
			in = new ByteArrayInputStream(b);
		}
		else {
			if (req.getContentLength() == 0)
				return ;
			in = req.getInputStream();
		}
		try {
			@SuppressWarnings("unchecked")
			AnsonMsg<T> msg = (AnsonMsg<T>) Anson.fromJson(in);
			onGet(msg, resp);
		} catch (AnsonException | SemanticException e) {
			if (ServFlags.query)
				e.printStackTrace();
			write(resp, JProtocol.err(p, MsgCode.exSemantic, e.getMessage()));
		}
		in.close();
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			InputStream in = req.getInputStream(); 
			@SuppressWarnings("unchecked")
			AnsonMsg<T> msg = (AnsonMsg<T>) Anson.fromJson(in);
			onPost(msg, resp);
		} catch (SemanticException | AnsonException e) {
			if (ServFlags.query)
				e.printStackTrace();
			write(resp, JProtocol.err(p, MsgCode.exSemantic, e.getMessage()));
		}
	}

	protected void write(HttpServletResponse resp, AnsonMsg<? extends AnsonResp> msg, JsonOpt... opts) {
		try {
			msg.toBlock(resp.getOutputStream(), opts);
		} catch (AnsonException | IOException e) {
			e.printStackTrace();
		}
	}

	/**Response with OK message.
	 * @param rs
	 * @return 
	 */
	protected AnsonMsg<AnsonResp> ok(SResultset rs) {
		AnsonMsg<AnsonResp> msg = new AnsonMsg<AnsonResp>(p, MsgCode.ok);
		AnsonResp bd = new AnsonResp(msg);
		msg.body(bd.rs(rs));
		return msg;
	}

	protected <U extends AnsonResp> AnsonMsg<U> ok(U body) {
		AnsonMsg<U> msg = new AnsonMsg<U>(p, MsgCode.ok);
		msg.body(body);
		return msg;
	}
	
	abstract protected void onGet(AnsonMsg<T> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException;

	abstract protected void onPost(AnsonMsg<T> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException;

}
