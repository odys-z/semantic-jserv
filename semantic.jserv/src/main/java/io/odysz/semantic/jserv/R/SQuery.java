package io.odysz.semantic.jserv.R;

import java.io.IOException;
import java.io.OutputStream;
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

@WebServlet(description = "querying db via Semantic.DA", urlPatterns = { "/r.serv" })
public class SQuery extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private static ISessionVerifier verifier;
	private static Transcxt st;

	static JHelper<QueryReq> jhelperReq;
//	static JHelper<QueryResp> jhelperResp;
	static {
		st = JSingleton.st;
		jhelperReq  = new JHelper<QueryReq>();
//		jhelperResp = new JHelper<QueryResp>();
		verifier = JSingleton.getSessionVerifier();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		if (ServFlags.query)
			Utils.logi("---------- query.serv get ----------");
		resp.setCharacterEncoding("UTF-8");
		try {
//			InputStream in = req.getInputStream();
//			QueryReq msg = jhelperReq.readJson(in, QueryReq.class);
//			in.close();
			JMessage<QueryReq> msg = ServletAdapter.<QueryReq>read(req, jhelperReq, QueryReq.class);
			
			verifier.verify(msg.header());
			
//			QueryResp rs = query((QueryReq) msg.body().get(0));
			SemanticObject rs = query(msg);
			
			
//			int size = msg.body().size();
//			if (size > 1)
//				resp.getWriter().write(Html.rs((SResultset)rs.get("rs"),
//						String.format("%s more query results ignored.", size - 1)));
//			else 
				resp.getWriter().write(Html.rs((SResultset)rs.get("rs")));
			resp.flushBuffer();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (TransException e) {
			e.printStackTrace();
		} catch (SsException e) {
			e.printStackTrace();
		} catch (ReflectiveOperationException e) {
			e.printStackTrace();
		}
	}
	
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		if (ServFlags.query)
			Utils.logi("========== query.serv post ==========");
		try {
			JMessage<QueryReq> msg = ServletAdapter.<QueryReq>read(req, jhelperReq, QueryReq.class);
			
			SemanticObject rs = query(msg);
			
			resp.setCharacterEncoding("UTF-8");
			OutputStream os = resp.getOutputStream();

			ServletAdapter.write(resp, rs);
			resp.flushBuffer();
			os.close();
		} catch (SemanticException e) {
			 ServletAdapter.write(resp, JProtocol.err(Port.query, MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (TransException e) {
			e.printStackTrace();
		} catch (ReflectiveOperationException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(Port.query, MsgCode.exGeneral, e.getMessage()));
		}
	}
	
	SemanticObject query(JMessage<QueryReq> msgBody) throws SQLException, TransException {
		// TODO let's use stream mode
		ArrayList<String> sqls = new ArrayList<String>();
		QueryReq msg = msgBody.body().get(0);
		Query selct = st.select(msg.mtabl, msg.mAlias);
		if (msg.exprs != null && msg.exprs.size() > 0)
			for (String[] col : msg.exprs)
				selct.col(col[Ix.exprExpr], col[Ix.exprAlais]);
		
		if (msg.joins != null && msg.joins.size() > 0) {
			for (String[] j : msg.joins)
				 selct.j(join.parse(j[Ix.joinType]),
						 			j[Ix.joinTabl],
						 			j[Ix.joinAlias],
						 			j[Ix.joinOnCond]);
		}
		
		if (msg.where != null && msg.where.size() > 0) {
			for (String[] cond : msg.where)
				selct.where(cond[Ix.predicateOper], cond[Ix.predicateL], cond[Ix.predicateR]);
		}
		
		// TODO: GROUP
		// TODO: ORDER
		selct.commit(sqls);

		if (ServFlags.query)
			Utils.logi(sqls);

		// Using semantic-DA to query from default connection.
		// If the connection is not default, use another overloaded function select(connId, ...).
//		QueryResp respMsg = new QueryResp();

		SemanticObject respMsg = new SemanticObject();
		for (String sql : sqls) {
			SResultset rs = Connects.select(sql);
			respMsg.add("rs", rs);

			if (ServFlags.query)
				try {rs.printSomeData(false, 1, rs.getColumnName(1), rs.getColumnName(2)); }
				catch (Exception e) {e.printStackTrace();}
		}

		return respMsg;
	}

}
