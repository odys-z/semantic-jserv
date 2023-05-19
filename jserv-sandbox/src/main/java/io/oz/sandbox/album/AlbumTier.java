package io.oz.sandbox.album;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.sandbox.protocol.Sandport;
import io.oz.spreadsheet.SpreadsheetReq;
import io.oz.spreadsheet.SpreadsheetResp;
import io.oz.spreadsheet.SpreadsheetReq.A;

@WebServlet(description = "Semantic sessionless: Album", urlPatterns = { "/album.less" })
public class AlbumTier extends ServPort<AlbumReq> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public AlbumTier() {
		super(Sandport.album);
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
			DocsResp rsp = null;
			if (A.insert.equals(jreq.a()))
				rsp = insert(jreq);
			else if (A.update.equals(jreq.a()))
				rsp = update(jreq);
			else if (A.records.equals(jreq.a()))
				rsp = records(jreq);
			else
				throw new SemanticException("Request (request.body.a = %s) can not be handled", jreq.a());

			write(resp, ok(rsp));
		} catch (TransException | SQLException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	private DocsResp insert(AlbumReq jreq) throws TransException, SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	private DocsResp update(AlbumReq jreq) {
		// TODO Auto-generated method stub
		return null;
	}

	private DocsResp records(AlbumReq jreq) {
		// TODO Auto-generated method stub
		return null;
	}

}
