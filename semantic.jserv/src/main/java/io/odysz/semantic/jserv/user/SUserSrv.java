//package io.odysz.semantic.jserv.user;
//
//import java.io.IOException;
//
//import javax.servlet.ServletException;
//import javax.servlet.annotation.WebServlet;
//import javax.servlet.http.HttpServletResponse;
//
//import io.odysz.anson.x.AnsonException;
//import io.odysz.semantic.jprotocol.AnsonMsg;
//import io.odysz.semantic.jprotocol.AnsonMsg.Port;
//import io.odysz.semantic.jserv.ServPort;
//import io.odysz.semantic.jserv.helper.Html;
//import io.odysz.semantics.x.SemanticException;
//
///**Abstract base class for user's serverlet extension.
// * @author odys-z@github.com
// * @param <T>
// */
//@WebServlet(description = "querying db via Semantic.DA", urlPatterns = { "/user.serv" })
//public class SUserSrv <T extends UserReq> extends ServPort<UserReq> {
//	public SUserSrv() {
//		super(Port.user);
//	}
//
//	private static final long serialVersionUID = 1L;
//
//
//	@Override
//	protected void onGet(AnsonMsg<UserReq> msg, HttpServletResponse resp)
//			throws ServletException, IOException, AnsonException, SemanticException {
//		resp.setCharacterEncoding("UTF-8");
//		resp.getWriter().write(Html.ok(("suser.serv: to be overriden...")));
//		resp.flushBuffer();
//	}
//
//	@Override
//	protected void onPost(AnsonMsg<UserReq> msg, HttpServletResponse resp)
//			throws ServletException, IOException, AnsonException, SemanticException {
//		resp.setCharacterEncoding("UTF-8");
//		write(resp, ok("suser.serv: to be overriden..."));
//		resp.flushBuffer();
//	}
//
//}
