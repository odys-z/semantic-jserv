package io.oz.jserv.dbsync;

import static io.odysz.common.LangExt.isblank;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.dbsync.DBSyncReq.A;

@WebServlet(description = "Cleaning tasks manager", urlPatterns = { "/sync.db" })
public class DBSyncer extends ServPort<DBSyncReq> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private boolean verbose;

	public DBSyncer() {
		super(Port.dbsyncer);
	}

	@Override
	protected void onGet(AnsonMsg<DBSyncReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onPost(AnsonMsg<DBSyncReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		// 
		resp.setCharacterEncoding("UTF-8");
		try {
			IUser usr = JSingleton.getSessionVerifier().verify(msg.header());

			DBSyncReq dbr = msg.body(0);

			AnsonResp rsp = null;
			String a = dbr.a();

			// requires meta for operations
			if (isblank(dbr.tabl))
				throw new SemanticException("To push/update a doc via Docsyncer, docTable name can not be null.");

			if (A.open.equals(a))
				rsp = onOpenClean(dbr, usr);
			else if (A.records.equals(a))
				;

			else throw new SemanticException(String.format(
				"request.body<DBSyncReq>.a can not be handled: %s",
				a));

			if (resp != null)
				write(resp, ok(rsp));
		} catch (SemanticException e) {
			if (verbose) e.printStackTrace();
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			if (verbose) e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} catch (InterruptedException e) {
			write(resp, err(MsgCode.exIo, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	/**
	 * Open a clean session.
	 * @param req
	 * @param usr
	 * @return
	 * @throws SQLException
	 * @throws TransException
	 */
	private AnsonResp onOpenClean(DBSyncReq req, IUser usr) throws SQLException, TransException {
		return null;
	}

}
