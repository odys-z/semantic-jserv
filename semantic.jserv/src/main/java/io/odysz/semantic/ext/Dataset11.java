//package io.odysz.semantic.ext;
//
//import java.io.IOException;
//import java.sql.SQLException;
//
//import javax.servlet.ServletException;
//import javax.servlet.annotation.WebServlet;
//import javax.servlet.http.HttpServletResponse;
//
//import io.odysz.common.Utils;
//import io.odysz.module.rs.AnResultset;
//import io.odysz.semantic.DA.Connects;
//import io.odysz.semantic.jprotocol.AnsonMsg;
//import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
//import io.odysz.semantic.jprotocol.AnsonMsg.Port;
//import io.odysz.semantic.jprotocol.AnsonResp;
//import io.odysz.semantic.jprotocol.IPort;
//import io.odysz.semantic.jprotocol.JOpts;
//import io.odysz.semantic.jserv.JSingleton;
//import io.odysz.semantic.jserv.ServFlags;
//import io.odysz.semantic.jserv.ServPort;
//import io.odysz.semantic.jserv.helper.Html;
//import io.odysz.semantic.jsession.ISessionVerifier;
//import io.odysz.semantics.x.SemanticException;
//import io.odysz.transact.sql.Transcxt;
//import io.odysz.transact.x.TransException;
//
///**
// * 
// * @deprecated Replaced by {@link Dataset}, and only for protocol backward compatibility.
// * @since 1.4.25
// * @author odys-z@github.com
// */
//@WebServlet(description = "load dataset configured in dataset.xml", urlPatterns = { "/ds.serv11" })
//public class Dataset11 extends ServPort<AnDatasetReq> {
//	public Dataset11() {
//		super(Port.dataset11);
//	}
//
//	private static final long serialVersionUID = 1L;
//
//	protected static ISessionVerifier verifier;
//	protected static Transcxt st;
//
//	static IPort p = Port.dataset11;
//	static JOpts _opts = new JOpts();
//
//	static {
//		st = JSingleton.defltScxt;
//	}
//
//	@Override
//	protected void onGet(AnsonMsg<AnDatasetReq> msg, HttpServletResponse resp)
//			throws ServletException, IOException {
//		if (ServFlags.extStree)
//			Utils.logi("---------- query (ds.jserv) get ----------");
//		resp.setCharacterEncoding("UTF-8");
//		try {
//			String conn = msg.body(0).uri();
//			conn = Connects.uri2conn(conn);
//
//			verifier.verify(msg.header());
//
//			AnsonResp rs = dataset(conn, msg);
//			resp.getWriter().write(Html.rs((AnResultset)rs.rs(0)));
//		} catch (Exception e) {
//			e.printStackTrace();
//		} finally {
//			resp.flushBuffer();
//		}
//	}
//	
//	protected void onPost(AnsonMsg<AnDatasetReq> msg, HttpServletResponse resp)
//			throws IOException {
//		resp.setCharacterEncoding("UTF-8");
//		if (ServFlags.extStree)
//			Utils.logi("========== query (ds.jserv) post ==========");
//		try {
//			String uri = msg.body(0).uri();
//			if (uri == null)
//				write(resp, err(MsgCode.exSemantic, "Since v1.3.0, Dataset request must specify an uri."));
//			else {
//				String conn = Connects.uri2conn(uri);
//				AnsonResp rs = dataset(conn, msg);
//				write(resp, ok(rs));
//			}
//		} catch (SemanticException e) {
//			write(resp, err(MsgCode.exSemantic, e.getMessage()));
//		} finally {
//			resp.flushBuffer();
//		}
//	}
//	
//	/**
//	 * @param msgBody
//	 * @return {code: "ok", port: {@link AnsonMsg.Port#query}, rs: [{@link AnResultset}, ...]}
//	 * @throws SQLException
//	 * @throws TransException
//	 */
//	protected AnsonResp dataset(String conn, AnsonMsg<AnDatasetReq> msgBody)
//			throws SemanticException {
//		throw new SemanticException("Since 1.5.0, port url 'ds.jserv11' is renamed to 'ds.jserv11'.");
//	}
//
//
//}
