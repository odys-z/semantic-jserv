package io.odysz.semantic.jserv.R;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.common.Utils;
import io.odysz.common.dbtype;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
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
			Utils.logi("========== squery (R.serv) post <= %s ==========", req.getRemoteAddr());

		resp.setCharacterEncoding("UTF-8");
		try {
			JMessage<QueryReq> msg = ServletAdapter.<QueryReq>read(req, jhelperReq, QueryReq.class);
			IUser usr = verifier.verify(msg.header());
			SemanticObject rs = query(msg.body(0), usr);

			ServletAdapter.write(resp, rs, msg.opts());
		} catch (SsException e) {
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSemantic, e.getMessage()));
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
	protected Query buildSelct(QueryReq msg, IUser usr) throws SQLException, TransException {
//		ArrayList<String> sqls = new ArrayList<String>();
		Query selct = st.select(msg.mtabl, msg.mAlias);
		
		// exclude sqlite paging
		if (msg.page >= 0 && msg.pgsize > 0
			&& dbtype.sqlite == Connects.driverType(
				msg.conn() == null ? Connects.defltConn() : msg.conn())) {
			Utils.warn("JQuery#buildSelct(): Requesting data from sqlite, but it's not easy to page in sqlite. So page and size are ignored: %s, %s.",
					msg.page, msg.pgsize);
		}
		else selct.page(msg.page, msg.pgsize);

		if (msg.exprs != null && msg.exprs.size() > 0)
			for (Object[] col : msg.exprs)
				selct.col((String)col[Ix.exprExpr], (String)col[Ix.exprAlais]);
		
		// Sample of join on parsing:
//		0	l
//		1	a_roles
//		2	R
//		3	U.roleId=R.roleId or U.roleId = 'admin' and U.orgId in ('Mossad', 'MI6', 'CIA', 'SVR', 'ChaoYang People')
//
//		select userId userId, userName userName, mobile mobile, dept.orgId orgId, o.orgName orgName, 
//		dept.departName departName, dept.departId departId, R.roleId roleId, R.roleName roleName, notes notes 
//		from a_user U 
//		join a_reg_org o on U.orgId = o.orgId 
//		left outer join a_org_depart dept on U.departId = dept.departId 
//		left outer join a_roles R on U.roleId = R.roleId OR U.roleId = 'admin' AND U.orgId in ('Mossad', 'MI6', 'CIA', 'SVR', 'ChaoYang People') 
//		where U.userName like '%张%'

		if (msg.joins != null && msg.joins.size() > 0) {
			for (Object[] j : msg.joins)
				if (j[Ix.joinTabl] instanceof QueryReq) {
					Query q = buildSelct((QueryReq)j[Ix.joinTabl], usr);
					selct.j(join.parse((String)j[Ix.joinType]),
							q,
							(String)j[Ix.joinAlias],
							(String)j[Ix.joinOnCond]);
				}
				else
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
		// GROUP BY
		selct.groupby(msg.groups);
		// ORDER BY
		selct.orderby(msg.orders);
		
		if (msg.limt != null)
			selct.limit(msg.limt[0], msg.limt.length > 1 ? msg.limt[1] : null);
		
		return selct;
	}

	protected SemanticObject query(QueryReq msg, IUser usr) throws SQLException, TransException {
		Query selct = buildSelct(msg, usr);
		SemanticObject s = selct.rs(st.instancontxt(msg.conn(), usr));
		SResultset rs = (SResultset) s.rs(0);
		SemanticObject respMsg = new SemanticObject();
		respMsg.rs(rs, rs.total());
		return JProtocol.ok(p, respMsg);
	}
}
