package io.oz.album.tier;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.album.AlbumFlags;
import io.oz.album.AlbumPort;
import io.oz.album.AlbumSingleton;
import io.oz.album.tier.AlbumReq.A;

@WebServlet(description = "Album tier: albums", urlPatterns = { "/album.less" })
public class Albums extends ServPort<AlbumReq> {

	private static final long serialVersionUID = 1L;

	public Albums() {
		super(AlbumPort.album);
	}

	@Override
	protected void onGet(AnsonMsg<AlbumReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {

		if (AlbumFlags.album)
			Utils.logi("---------- ever-connect /album.less GET  ----------");
	}

	@Override
	protected void onPost(AnsonMsg<AlbumReq> jmsg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {

		if (AlbumFlags.album)
			Utils.logi("========== ever-connect /album.less POST ==========");

		try {
			AlbumReq jreq = jmsg.body(0);
			String a = jreq.a();
			AnsonMsg<? extends AnsonResp> rsp = null;

			IUser usr = AlbumSingleton.getSessionVerifier().verify(jmsg.header());

			if (A.records.equals(a)) // load
				rsp = album(jmsg.body(0), usr);
			else if (A.collect.equals(a))
				rsp = photos(jmsg.body(0), usr);
			else if (A.rec.equals(a))
				rsp = photo(jmsg.body(0), usr);
			else
				throw new SemanticException(
						"request.body.a can not handled: %s\\n" +
						"Only a = [%s, %s, %s, %s, %s, %s] are supported.",
						jreq.a(), A.records, A.collect, A.rec, A.insert, A.update, A.del );
			write(resp, rsp);
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			if (AlbumFlags.album)
				e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	private AnsonMsg<? extends AnsonResp> photo(AlbumReq body, IUser usr) {
		// TODO Auto-generated method stub
		return null;
	}

	private AnsonMsg<? extends AnsonResp> photos(AlbumReq body, IUser usr) {
		// TODO Auto-generated method stub
		return null;
	}

	private AnsonMsg<? extends AnsonResp> album(AlbumReq body, IUser usr) {
		// TODO Auto-generated method stub
		return null;
	}

}
