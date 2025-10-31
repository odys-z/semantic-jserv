package io.odysz.jsample.servs;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.AnsonException;
import io.odysz.jsample.protocol.Samport;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantics.x.SemanticException;

/**
 * <h4>ServPort Template </h4>
 * 
 * @author odys-z@github.com
 */
@WebServlet(description = "jserv.sample example: extend serv handler", urlPatterns = { "/custom.serv11" })
public class CustomServ extends ServPort<SampleReq> {
	public CustomServ() {
		super(null);
		p = Samport.example;
	}

	private static final long serialVersionUID = 1L;

	@Override
	protected void onGet(AnsonMsg<SampleReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
	}

	@Override
	protected void onPost(AnsonMsg<SampleReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
	}
}
