package io.odysz.semantic.jserv;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.Anson;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jserv.R.AnQueryReq;

public abstract class ServHandler<T extends AnsonBody> extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		// TODO Auto-generated method stub
		super.doGet(req, resp);
	}

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
		
		AnsonMsg<AnQueryReq> msg;
		try {
			msg = (AnsonMsg<AnQueryReq>) Anson.fromJson(in);
			onPost(msg, resp);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			e.printStackTrace();
		}
		in.close();

	}

	protected void onGet(AnsonMsg<T> msg, HttpServletResponse resp) throws ServletException, IOException {
	}

	protected void onPost(AnsonMsg<AnQueryReq> msg, HttpServletResponse resp) throws IOException {
	}

}
