package io.odysz.semantic.jserv.R;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.common.Utils;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.IPort;
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
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Query.Ix;
import io.odysz.transact.sql.parts.select.JoinTabl.join;
import io.odysz.transact.x.TransException;

/**CRUD read service.
 * @author odys-z@github.com
 */
@WebServlet(description = "querying db via Semantic.DA", urlPatterns = { "/r.serv" })
public class JQuery extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private static final IPort p = Port.query;

	protected static ISessionVerifier verifier;
	protected static DATranscxt st;

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
			Utils.logi("---------- squery (r.serv) get <- %s ----------", req.getRemoteAddr());
		resp.setCharacterEncoding("UTF-8");
		try {
			JMessage<QueryReq> msg = ServletAdapter.<QueryReq>read(req, jhelperReq, QueryReq.class);
			IUser usr = verifier.verify(msg.header());

			SemanticObject rs = query(msg.body(0), usr);
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
			Utils.logi("========== squery (r.serv) post <= %s ==========", req.getRemoteAddr());

		resp.setCharacterEncoding("UTF-8");
		try {
			JMessage<QueryReq> msg = ServletAdapter.<QueryReq>read(req, jhelperReq, QueryReq.class);
			IUser usr = verifier.verify(msg.header());
			SemanticObject rs = query(msg.body(0), usr);

			ServletAdapter.write(resp, rs);
		} catch (SemanticException e) {
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exTransct, e.getMessage()));
		} catch (ReflectiveOperationException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exGeneral, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}
	
	/**
	 * @param msg
	 * @param usr 
	 * @return {code: "ok", port: {@link JMessage.Port}.query, rs: [{@link SResultset}, ...]}
	 * @throws SQLException
	 * @throws TransException
	 */
	protected SemanticObject query(QueryReq msg, IUser usr) throws SQLException, TransException {
//		ArrayList<String> sqls = new ArrayList<String>();
		Query selct = st.select(msg.mtabl, msg.mAlias)
						.page(msg.page, msg.pgsize);
		if (msg.exprs != null && msg.exprs.size() > 0)
			for (Object[] col : msg.exprs)
				selct.col((String)col[Ix.exprExpr], (String)col[Ix.exprAlais]);
		
		// FIXME bug of join on parsing:
//		0	l
//		1	a_roles
//		2	r
//		3	u.roleId=r.roleId or u.roleId = 'admin' and u.orgId in ('Mossad', 'MI6', 'CIA', 'SVR', 'ChaoYang People')
//
//		select userId userId, userName userName, mobile mobile, dept.orgId orgId, o.orgName orgName, 
//		dept.departName departName, dept.departId departId, r.roleId roleId, r.roleName roleName, notes notes 
//		from a_user u 
//		join a_reg_org o on u.orgId = o.orgId 
//		left outer join a_org_depart dept on u.departId = dept.departId 
//		left outer join a_roles r on u.roleId = r.roleId OR u.roleId = 'admin' AND u.orgId in ('Mossad', 'MI6', 'CIA', 'SVR', 'ChaoYang People') 
//		where u.userName like '%张%'
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
		/*
		selct.commit(sqls);
		
		if (ServFlags.query)
			Utils.logi(sqls);

		// Using semantic-DA to query from default connection.
		SemanticObject respMsg = new SemanticObject();
		for (String sql : sqls) {
			// Shall be moved to Protocol?
			SResultset rs = Connects.select(sql);
			respMsg.rs(rs, 100);
			// bug here, not 100!

			if (ServFlags.query)
				try {rs.printSomeData(false, 1, rs.getColumnName(1), rs.getColumnName(2)); }
				catch (Exception e) {e.printStackTrace();}
		}

		return JProtocol.ok(p, respMsg);
		*/
		SemanticObject s = selct.rs(st.instancontxt(usr));
		SResultset rs = (SResultset) s.rs(0);
		SemanticObject respMsg = new SemanticObject();
		respMsg.rs(rs, rs.total());
		return JProtocol.ok(p, respMsg);
	}
}
