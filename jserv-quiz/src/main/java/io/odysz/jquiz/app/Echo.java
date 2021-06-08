package io.odysz.jquiz.app;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.common.Utils;
import io.odysz.jquiz.utils.JquizFlags;
import io.odysz.semantic.jserv.helper.Html;

@WebServlet(description = "Sample Case: echo", urlPatterns = { "/echo.serv" })
public class Echo  extends HttpServlet {
	private static final long serialVersionUID = 1L;

	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		if (JquizFlags.echo)
			Utils.logi("---------- echo.sample get <- %s ----------", req.getRemoteAddr());

		resp.getWriter().write(Html.ok(req.getRequestURL().toString()));
		resp.flushBuffer();
	}
}
