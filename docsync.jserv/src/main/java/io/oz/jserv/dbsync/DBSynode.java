package io.oz.jserv.dbsync;

import static io.odysz.common.LangExt.isblank;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Configs;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.IUser;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.dbsync.ClobEntity.OnChainOk;
import io.oz.jserv.dbsync.ClobEntity.OnChainStart;
import io.oz.jserv.dbsync.DBSyncReq.A;
import io.oz.jserv.docsync.SyncRobot;
import io.oz.jserv.docsync.SynodeMeta;

@WebServlet(description = "Cleaning tasks manager", urlPatterns = { "/sync.db" })
public class DBSynode extends ServPort<DBSyncReq> {
	private static final long serialVersionUID = 1L;
	private static boolean verbose;

	static DATranscxt st;

	static HashMap<String, TableMeta> metas;
	static {
		try {
			st = new DATranscxt(null);
			metas = new HashMap<String, TableMeta>();
			new SynodeMeta();

			new SyncRobot("TODO: synode.xml/id");

			verbose = Configs.getBoolean("docsync.debug");
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	private final SynodeMode mode;
	private OnChainOk onBlocksFinish;
	private OnChainStart onClobstart;

	public DBSynode() {
		super(Port.dbsyncer);
		mode = SynodeMode.hub; // FIXME, load syndoe.xml
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
				; // rsp = onOpenClean(dbr, usr);
			else if (A.cleans.equals(a))
				rsp = onQuryCleans(dbr, usr);
			else if (A.pushDRE.equals(a))
				; // rsp = onMergedCleans(dbr, usr);
			else {
				ClobEntity chain = new ClobEntity((DocTableMeta) metas.get(dbr.tabl), st);
				if (A.pushClobStart.equals(a)) {
					/*
					if (isblank(msg.subFolder, " - - "))
						throw new SemanticException("Folder of managed doc can not be empty - which is important for saving file. It's required for creating media file.");
					*/
					rsp = chain.startChain(msg.body(0), usr, onClobstart);
				}
				else if (A.pushCloblock.equals(a))
					rsp = chain.uploadClob(msg.body(0), usr);
				else if (A.pushClobEnd.equals(a))
					rsp = chain.endChain(msg.body(0), usr, mode, onBlocksFinish);
				else if (A.pushClobAbort.equals(a))
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
		} catch (InterruptedException e) {
			write(resp, err(MsgCode.exIo, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	/**
	 * Open a clean session by reply with a time window
	 * [syn_stamp.cleanstamp, current_stamp).
	 * @param dbr
	 * @param usr
	 * @return response
	 */
	protected DBSyncResp onQuryCleans(DBSyncReq dbr, IUser usr) {
		DBSyncResp resp = new DBSyncResp().cleanWindow(dbr.tabl);
		return resp;
	}

//	/**
//	 * Handle rejects, erased and deleted, of which closed are also collected.
//	 * 
//	 * @param dbr
//	 * @param usr
//	 * @return reply
//	 */
//	protected AnsonResp onMergedCleans(DBSyncReq dbr, IUser usr) {
//		DBSyncResp resp = new DBSyncResp().cleanWindow(dbr.window)
//				; // and collect closing entities of this session
//		return resp;
//	}
//
//	/**
//	 * Open a clean session by reply with a time window.
//	 * @deprecated
//	 * @param req
//	 * @param usr
//	 * @return response
//	 * @throws SQLException
//	 * @throws TransException
//	 */
//	protected DBSyncResp onOpenClean(DBSyncReq req, IUser usr)
//			throws SQLException, TransException {
//		return null;
//	}

}
