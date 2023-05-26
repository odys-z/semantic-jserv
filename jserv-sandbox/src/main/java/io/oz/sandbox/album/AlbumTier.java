package io.oz.sandbox.album;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.eq;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jsession.JUser;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.PageInf;
import io.odysz.transact.x.TransException;
import io.oz.sandbox.protocol.Sandport;
import io.oz.spreadsheet.SpreadsheetReq.A;

@WebServlet(description = "Semantic sessionless: Album", urlPatterns = { "/album.less" })
public class AlbumTier extends ServPort<AlbumReq> {
	static JRobot robot;

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public AlbumTier() {
		super(Sandport.album);
		
		robot = new JRobot();
	}

	@Override
	protected void onGet(AnsonMsg<AlbumReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
	}

	@Override
	protected void onPost(AnsonMsg<AlbumReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		AlbumReq jreq = msg.body(0);
		
		try {
			AlbumResp rsp = null;
			if (A.insert.equals(jreq.a()))
				rsp = insert(jreq);
			else if (A.update.equals(jreq.a()))
				rsp = update(jreq);
			else if (A.records.equals(jreq.a()))
				rsp = records(jreq, robot);
			else
				throw new SemanticException("Request (request.body.a = %s) can not be handled", jreq.a());

			write(resp, ok(rsp));
		} catch (TransException | SQLException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	private AlbumResp insert(AlbumReq jreq) throws TransException, SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	private AlbumResp update(AlbumReq jreq) {
		// TODO Auto-generated method stub
		return null;
	}

	private AlbumResp records(AlbumReq jreq, IUser usr) throws SQLException, TransException {
		if (isblank(jreq.sk))
			throw new SemanticException("AlbumReq.sk is required.");

		String conn = Connects.uri2conn(jreq.uri());
		// force org-id as first arg
		PageInf page = isNull(jreq.page)
				? new PageInf(0, -1, usr.orgId())
				: eq(jreq.page.condts.get(0), usr.orgId())
				? jreq.page
				: jreq.page.insertCondt(usr.orgId());

		List<?> lst = DatasetCfg.loadStree(conn, jreq.sk, page);
		return (AlbumResp) new AlbumResp().forest(lst);
	}

}
