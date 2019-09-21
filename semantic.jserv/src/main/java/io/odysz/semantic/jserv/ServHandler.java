package io.odysz.semantic.jserv;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.R.AnQueryReq;

public abstract class ServHandler<T extends AnsonBody> extends HttpServlet {

	protected void onGet(AnsonMsg<T> msg, HttpServletResponse resp) throws ServletException, IOException {
	}

	protected void onPost(AnsonMsg<AnQueryReq> msg, HttpServletResponse resp) throws IOException {
	}

}
