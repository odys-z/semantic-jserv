package io.odysz.semantic.jserv;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.Anson;
import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jprotocol.JOpts;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantics.x.SemanticException;

public abstract class ServHandler<T extends AnsonBody> extends HttpServlet {
	protected IPort p;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// TODO Auto-generated method stub
		super.doGet(req, resp);
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		InputStream in = null; 
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
		
		AnsonMsg<T> msg;
		try {
			msg = (AnsonMsg<T>) Anson.fromJson(in);
			onPost(msg, resp);
		} catch (SemanticException | AnsonException e) {
			// response error;
			write(resp, JProtocol.err(p, MsgCode.exTransct, e.getMessage()));
		}
		in.close();
	}

	private void write(HttpServletResponse resp, AnsonMsg<? extends AnsonBody> err, JOpts opts) {
		try {
			err.toBlock(resp.getOutputStream(), opts);
		} catch (AnsonException | IOException e) {
			e.printStackTrace();
		}
	}

	abstract protected void onGet(AnsonMsg<T> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException;

	abstract protected void onPost(AnsonMsg<T> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException;

}
