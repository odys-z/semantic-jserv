//package io.oz.album.tier;
//
//import java.io.IOException;
//import java.sql.SQLException;
//
//import javax.servlet.ServletException;
//import javax.servlet.http.HttpServlet;
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
//
//import io.odysz.anson.x.AnsonException;
//import io.odysz.common.Utils;
//import io.odysz.semantic.jprotocol.AnsonMsg;
//import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
//import io.odysz.semantic.jprotocol.AnsonResp;
//import io.odysz.semantic.jprotocol.IPort;
//import io.odysz.semantics.x.SemanticException;
//import io.odysz.transact.x.TransException;
//import io.oz.album.AlbumFlags;
//import io.oz.album.AlbumPort;
//import io.oz.album.photo.tier.PhotoReq.A;
//
//public class AlbumStream extends HttpServlet {
//
//	protected IPort p = AlbumPort.stream;
//
//	@Override
//    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
//			throws ServletException, IOException {
//
//		if (AlbumFlags.album)
//			Utils.logi("========== ever-connect /album.less POST ==========");
//
//		try {
//	
//			if (A.upload.equals(a))
//				upload(jmsg.body(0), usr);
//			else if (A.download.equals(a))
//				download(jmsg.body(0), usr, resp);
//			write(resp, ok(p));
//		} catch (SemanticException e) {
//			write(resp, err(MsgCode.exSemantic, e.getMessage()));
//		} catch (SQLException | TransException e) {
//			if (AlbumFlags.album)
//				e.printStackTrace();
//			write(resp, err(MsgCode.exTransct, e.getMessage()));
//		} catch (SsException e) {
//			write(resp, err(MsgCode.exSession, e.getMessage()));
//		} finally {
//			resp.flushBuffer();
//		}
//	}
//
//}
