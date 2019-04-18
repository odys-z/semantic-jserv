package io.odysz.semantic.ext;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.common.Utils;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JMessage.MsgCode;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.semantic.jserv.helper.ServletAdapter;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.ISessionVerifier;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Transcxt;
import io.odysz.transact.x.TransException;

/**CRUD read service extension: dataset.
 * @author odys-z@github.com
 */
@WebServlet(description = "load dataset configured in dataset.xml", urlPatterns = { "/ds.serv" })
public class Dataset extends HttpServlet {
	private static final long serialVersionUID = 1L;

	protected static ISessionVerifier verifier;
	protected static Transcxt st;

	protected static JHelper<DatasetReq> jhelperReq;

	static {
		st = JSingleton.defltScxt;
		jhelperReq  = new JHelper<DatasetReq>();
		verifier = JSingleton.getSessionVerifier();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		if (ServFlags.query)
			Utils.logi("---------- query (ds.jserv) get <- %s ----------", req.getRemoteAddr());
		resp.setCharacterEncoding("UTF-8");
		try {
			String conn = req.getParameter("conn");
			if (conn == null || conn.trim().length() == 0)
				conn = Connects.defltConn();

			JMessage<DatasetReq> msg = ServletAdapter.<DatasetReq>read(req, jhelperReq, DatasetReq.class);
			verifier.verify(msg.header());

			SemanticObject rs = dataset(conn, msg);
			resp.getWriter().write(Html.rs((SResultset)rs.get("rs")));
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (TransException e) {
			e.printStackTrace();
		} catch (SsException e) {
			e.printStackTrace();
		} catch (ReflectiveOperationException e) {
			e.printStackTrace();
		} finally {
			resp.flushBuffer();
		}
	}
	
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws IOException {
		resp.setCharacterEncoding("UTF-8");
		if (ServFlags.query)
			Utils.logi("========== query (ds.jserv) post <= %s ==========", req.getRemoteAddr());
		try {
			String conn = req.getParameter("conn");
			if (conn == null || conn.trim().length() == 0)
				conn = Connects.defltConn();

			JMessage<DatasetReq> msg = ServletAdapter.<DatasetReq>read(req, jhelperReq, DatasetReq.class);
			
			SemanticObject rs = dataset(conn, msg);
			

			ServletAdapter.write(resp, rs);
		} catch (SemanticException e) {
			ServletAdapter.write(resp, JProtocol.err(Port.dataset, MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(Port.dataset, MsgCode.exTransct, e.getMessage()));
		} catch (ReflectiveOperationException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(Port.dataset, MsgCode.exGeneral, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}
	
	/**
	 * @param msgBody
	 * @return {code: "ok", port: {@link JMessage.Port}.query, rs: [{@link SResultset}, ...]}
	 * @throws SQLException
	 * @throws TransException
	 */
	protected SemanticObject dataset(String conn, JMessage<DatasetReq> msgBody)
			throws SQLException, TransException {
		DatasetReq msg = msgBody.body().get(0);
		// List<SemanticObject> ds = DatasetCfg.loadStree(conn, msg.sk, msg.page(), msg.size(), msg.sqlArgs);		
		SResultset ds = DatasetCfg.select(conn, msg.sk, msg.page(), msg.size(), msg.sqlArgs);		

		// Shall be moved to Protocol?
		SemanticObject respMsg = new SemanticObject();
		respMsg.rs(ds, 100);
		// FIXME bug here, not 100!
		// FIXME bug here, not 100!
		// FIXME bug here, not 100!
		// FIXME bug here, not 100!
		// FIXME bug here, not 100!
		return JProtocol.ok(Port.dataset, respMsg);
	}

}
