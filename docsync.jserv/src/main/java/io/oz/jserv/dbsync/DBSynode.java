package io.oz.jserv.dbsync;

import static io.odysz.common.LangExt.isblank;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantics.IUser;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.dbsync.DBSyncReq.A;

@WebServlet(description = "Cleaning tasks manager", urlPatterns = { "/sync.db" })
public class DBSynode extends ServPort<DBSyncReq> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private boolean verbose;

	static HashMap<String, TableMeta> metas;

	public DBSynode() {
		super(Port.dbsyncer);
	}

	@Override
	protected void onGet(AnsonMsg<DBSyncReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
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
			else if (A.cleans.equals(a))
				;
			else {
				ClobChain chain = new ClobChain((DocTableMeta) metas.get(dbr.tabl), st);
				if (A.pushExtStart.equals(a)) {
					/*
					if (isblank(msg.subFolder, " - - "))
						throw new SemanticException("Folder of managed doc can not be empty - which is important for saving file. It's required for creating media file.");
					*/
					rsp = chain.startBlocks(msg.body(0), usr, entityResolver);
				}
				else if (A.pushExtBlock.equals(a))
					rsp = chain.uploadBlock(msg.body(0), usr);
				else if (A.pushExtEnd.equals(a))
					rsp = chain.endBlock(msg.body(0), usr, onBlocksFinish);
				else if (A.pushExtAbort.equals(a))
					rsp = chain.abortBlock(msg.body(0), usr);

				else throw new SemanticException(String.format(
					"request.body<DBSyncReq>.a can not be handled: %s",
					a));
			}

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
		} finally {
			resp.flushBuffer();
		}
	}

	/**
	 * Open a clean session by reply with a time window.
	 * @param req
	 * @param usr
	 * @return
	 * @throws SQLException
	 * @throws TransException
	 */
	private DBSyncResp onOpenClean(DBSyncReq req, IUser usr) throws SQLException, TransException {
		return null;
	}

}
