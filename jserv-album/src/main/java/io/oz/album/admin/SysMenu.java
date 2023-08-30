package io.oz.album.admin;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.common.Utils;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.ext.AnDatasetReq;
import io.odysz.semantic.ext.SemanticTree;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.album.AlbumFlags;
import io.oz.album.AlbumPort;

@WebServlet(description = "Load Sample App's Functions", urlPatterns = { "/menu.serv" })
public class SysMenu extends SemanticTree {
	private static final long serialVersionUID = 1L;

	@Override
	public void init() throws ServletException {
		super.init();
		p = AlbumPort.menu;
	}

	/**sk in dataset.xml: menu tree */
	private static final String defltSk = "sys.menu";

	@Override
	protected void onGet(AnsonMsg<AnDatasetReq> msg, HttpServletResponse resp)
			throws ServletException, IOException {
		if (AlbumFlags.admenu)
			Utils.logi("---------- menu.serv get ----------");

		try {
			String connId = msg.body(0).uri();
			connId = Connects.uri2conn(connId);
			String sk = msg.body(0).sk();

			List<?> lst = DatasetCfg.loadStree(connId,
					sk == null ? defltSk : sk, 0, -1, "admin");

			resp.getWriter().write(Html.listAnson(lst));
		} catch (TransException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	@Override
	protected void onPost(AnsonMsg<AnDatasetReq> msg, HttpServletResponse resp)
			throws IOException, SemanticException {
		if (AlbumFlags.admenu)
			Utils.logi("========== menu.serv post ==========");

		resp.setCharacterEncoding("UTF-8");
		try {
			IUser usr = verifier.verify(msg.header());

			AnDatasetReq jreq = msg.body(0);

			// String sk = jreq.sk();
			// or configured with xml?
			jreq.sqlArgs = new String[] {usr.uid()};

			List<?> lst = DatasetCfg.loadStree(Connects.defltConn(),
							defltSk, jreq.page(), jreq.size(), jreq.sqlArgs);

			write(resp, ok(lst.size(), lst));
		} catch (SQLException e) {
			if (AlbumFlags.admenu)
				e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} catch (ClassCastException e) {
			write(resp, err(MsgCode.exGeneral, e.getMessage()));
		} catch (TransException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

}
