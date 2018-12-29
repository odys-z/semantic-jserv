package io.odysz.semantic.jserv.user;

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.semantic.jserv.helper.ServletAdapter;
import io.odysz.semantics.SemanticObject;

@WebServlet(description = "querying db via Semantic.DA", urlPatterns = { "/user.serv" })
public class SUser <T> extends HttpServlet {
	private static final long serialVersionUID = 1L;


	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		resp.setCharacterEncoding("UTF-8");
		resp.getWriter().write(Html.ok(("suser.serv: to be overriden...")));
		resp.flushBuffer();
	}
	
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.setCharacterEncoding("UTF-8");
		OutputStream os = resp.getOutputStream();

		ServletAdapter.write(resp, JProtocol.ok(Port.user,
				new SemanticObject().put("msg", "suser.serv: to be overriden...")));
		resp.flushBuffer();
		os.close();
	}

}
