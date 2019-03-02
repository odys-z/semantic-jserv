package io.odysz.semantic.jserv.R;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.common.Utils;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DA.Connects;
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
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Transcxt;
import io.odysz.transact.sql.Query.Ix;
import io.odysz.transact.sql.parts.select.JoinTabl.join;
import io.odysz.transact.x.TransException;

/**CRUD read service.
 * @author odys-z@github.com
 */
@WebServlet(description = "querying db via Semantic.DA", urlPatterns = { "/r.serv" })
public class SQuery extends HttpServlet {
	private static final long serialVersionUID = 1L;

	protected static ISessionVerifier verifier;
	protected static Transcxt st;

	protected static JHelper<QueryReq> jhelperReq;

	static {
		st = JSingleton.defltScxt;
		jhelperReq  = new JHelper<QueryReq>();
		verifier = JSingleton.getSessionVerifier();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		if (ServFlags.query)
			Utils.logi("---------- query (r.serv) get <- %s ----------", req.getRemoteAddr());
		resp.setCharacterEncoding("UTF-8");
		try {
			JMessage<QueryReq> msg = ServletAdapter.<QueryReq>read(req, jhelperReq, QueryReq.class);
			verifier.verify(msg.header());

			SemanticObject rs = query(msg.body(0));
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
	
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (ServFlags.query)
			Utils.logi("========== query (r.serv) post <= %s ==========", req.getRemoteAddr());
		try {
			JMessage<QueryReq> msg = ServletAdapter.<QueryReq>read(req, jhelperReq, QueryReq.class);
			
			SemanticObject rs = query(msg.body(0));
			
			resp.setCharacterEncoding("UTF-8");

			ServletAdapter.write(resp, rs);
			resp.flushBuffer();
		} catch (SemanticException e) {
			ServletAdapter.write(resp, JProtocol.err(Port.query, MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(Port.query, MsgCode.exTransct, e.getMessage()));
		} catch (ReflectiveOperationException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(Port.query, MsgCode.exGeneral, e.getMessage()));
		}
	}
	
	/**
	 * @param msgBody
	 * @return {code: "ok", port: {@link JMessage.Port}.query, rs: [{@link SResultset}, ...]}
	 * @throws SQLException
	 * @throws TransException
	 */
	protected SemanticObject query(QueryReq msg) throws SQLException, TransException {
		ArrayList<String> sqls = new ArrayList<String>();
//		QueryReq msg = msgBody.body().get(0);
		Query selct = st.select(msg.mtabl, msg.mAlias)
						.page(msg.page, msg.pgsize);
		if (msg.exprs != null && msg.exprs.size() > 0)
			for (Object[] col : msg.exprs)
				selct.col((String)col[Ix.exprExpr], (String)col[Ix.exprAlais]);
		
		if (msg.joins != null && msg.joins.size() > 0) {
			for (Object[] j : msg.joins)
				 selct.j(join.parse((String)j[Ix.joinType]),
						 			(String)j[Ix.joinTabl],
						 			(String)j[Ix.joinAlias],
						 			(String)j[Ix.joinOnCond]);
		}
		
		if (msg.where != null && msg.where.size() > 0) {
			for (Object[] cond : msg.where)
				selct.where((String)cond[Ix.predicateOper],
							(String)cond[Ix.predicateL],
							(String)cond[Ix.predicateR]);
		}
		
		// TODO: GROUP
		// TODO: ORDER
		selct.commit(sqls);
		
		if (ServFlags.query)
			Utils.logi(sqls);

		// Using semantic-DA to query from default connection.
		SemanticObject respMsg = new SemanticObject();
		for (String sql : sqls) {
			SResultset rs = Connects.select(sql);
			respMsg.add("rs", rs);

			if (ServFlags.query)
				try {rs.printSomeData(false, 1, rs.getColumnName(1), rs.getColumnName(2)); }
				catch (Exception e) {e.printStackTrace();}
		}
		respMsg.put("code", "ok");
		respMsg.put("port", Port.query);
		return respMsg;
	}

}
