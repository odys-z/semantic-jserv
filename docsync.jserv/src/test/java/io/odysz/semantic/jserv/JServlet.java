//package io.odysz.semantic.jserv;
//
//import java.io.IOException;
//import javax.servlet.ServletException;
//import javax.servlet.http.HttpServlet;
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
//
//import io.odysz.common.Utils;
//import io.odysz.semantic.jprotocol.AnsonBody;
//import io.odysz.semantic.jserv.ServPort;
//
//public class JServlet<T extends ServPort<R>, R extends AnsonBody> extends HttpServlet {
//	private static final long serialVersionUID = 1L;
//
//	private T jserv;
//
//	public JServlet(T jserv) {
//		this.jserv = jserv;
//	}
//
//	@Override
//	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
//			throws ServletException, IOException {
//		Utils.logi("Jservlet.doGet()");
//		jserv.doGet(req, resp);
//	}
//
//	@Override
//	protected void doHead(HttpServletRequest arg0, HttpServletResponse arg1)
//			throws ServletException, IOException {
//		Utils.logi("Jservlet.doHead()");
//		jserv.doHead(arg0, arg1);
//	}
//
//	@Override
//	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
//			throws ServletException, IOException {
//		Utils.logi("Jservlet.doPost()");
//		jserv.doPost(req, resp);
//	}
//}
