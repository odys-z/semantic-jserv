//package io.odysz.semantic.ext;
//
//import java.io.IOException;
//
//import javax.servlet.ServletException;
//import javax.servlet.annotation.WebServlet;
//import javax.servlet.http.HttpServletResponse;
//
//import io.odysz.anson.x.AnsonException;
//import io.odysz.common.Utils;
//import io.odysz.semantic.DATranscxt;
//import io.odysz.semantic.jprotocol.AnsonMsg;
//import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
//import io.odysz.semantic.jprotocol.AnsonMsg.Port;
//import io.odysz.semantic.jserv.JSingleton;
//import io.odysz.semantic.jserv.ServFlags;
//import io.odysz.semantic.jserv.ServPort;
//import io.odysz.semantics.x.SemanticException;
//
///**
// * @deprecated Only for backward compatibility support to deprecated port name.
// * 
// * @author odys-z@github.com
// */
//@WebServlet(description = "Abstract Tree Data Service", urlPatterns = { "/s-tree.serv11" })
//public class STree11 extends ServPort<AnDatasetReq> {
//	public STree11() {
//		super(Port.stree11);
//	}
//
//	private static final long serialVersionUID = 1L;
//
//	protected static DATranscxt st;
//
//	static {
//		st = JSingleton.defltScxt;
//	}	
//
//	@Override
//	protected void onGet(AnsonMsg<AnDatasetReq> msg, HttpServletResponse resp)
//			throws ServletException, IOException, AnsonException, SemanticException {
//		if (ServFlags.extStree)
//			Utils.logi("---------- squery (s-tree.serv11) get ----------");
//		resp.setCharacterEncoding("UTF-8");
//		try {
//			jsonResp(msg, resp);
//		} catch (Exception e) {
//			e.printStackTrace();
//			write(resp, err(MsgCode.exGeneral, e.getMessage()));
//		} finally {
//			resp.flushBuffer();
//		}
//	}
//	
//	@Override
//	protected void onPost(AnsonMsg<AnDatasetReq> msg, HttpServletResponse resp)
//			throws ServletException, IOException, AnsonException, SemanticException {
//		if (ServFlags.extStree)
//			Utils.logi("========== squery (s-tree.serv11) post ==========");
//
//		resp.setCharacterEncoding("UTF-8");
//		try {
//			jsonResp(msg, resp);
//		} catch (Exception e) {
//			e.printStackTrace();
//			write(resp, err(MsgCode.exGeneral, e.getMessage()));
//		} finally {
//			resp.flushBuffer();
//		}
//	}
//
//	protected void jsonResp(AnsonMsg<AnDatasetReq> jmsg, HttpServletResponse resp)
//			throws SemanticException {
//		throw new SemanticException("Since 1.5.0, port pattern 's-tree.serv11' is renamed to 's-tree.serv'.");
//	}
//}
