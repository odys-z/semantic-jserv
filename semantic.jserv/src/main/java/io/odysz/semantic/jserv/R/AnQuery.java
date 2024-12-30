package io.odysz.semantic.jserv.R;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.ISessionVerifier;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Query.Ix;
import io.odysz.transact.sql.parts.JoinTabl.join;
import io.odysz.transact.x.TransException;

import static io.odysz.common.LangExt.len;

/**
 * CRUD read service.
 * @author odys-z@github.com
 */
@WebServlet(description = "querying db via Semantic.DA", urlPatterns = { "/r.serv" })
public class AnQuery extends ServPort<AnQueryReq> {

	public AnQuery() { super(Port.query); }

	protected static ISessionVerifier verifier;
	// protected static DATranscxt st;

	static {
		// st = JSingleton.defltScxt;
		verifier = JSingleton.getSessionVerifier();
	}
	
	@Override
	protected void onGet(AnsonMsg<AnQueryReq> msg, HttpServletResponse resp)
			throws ServletException, IOException {
		if (ServFlags.query)
			Utils.logi("---------- squery (r.serv) get ----------");
		try {
			IUser usr = verifier.verify(msg.header());
			AnResultset rs = query(msg.body(0), usr, st);
			resp.getWriter().write(Html.rs(rs));
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (TransException e) {
			e.printStackTrace();
		} catch (SsException e) {
			e.printStackTrace();
		} finally {
			resp.flushBuffer();
		}
	}
	
	@Override
	protected void onPost(AnsonMsg<AnQueryReq> msg, HttpServletResponse resp) throws IOException {
		if (ServFlags.query)
			Utils.logi("========== squery (r.serv) post ==========");

		try {
			IUser usr = verifier.verify(msg.header());
			AnResultset rs = query(msg.body(0), usr, st);

			write(resp, ok(rs), msg.opts());
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exGeneral, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	/**
	 * @param msg
	 * @param usr 
	 * @param st0 
	 * @return {code: "ok", port: {@link AnsonMsg.Port#query}, rs: [{@link AnResultset}, ...]}
	 * @throws SQLException
	 * @throws TransException
	 */
	protected static Query buildSelct(AnQueryReq msg, IUser usr, DATranscxt st0) throws SQLException, TransException {
		Query selct = st0.select(msg.mtabl, msg.mAlias);
		
		selct.page(msg.page, msg.pgsize);

		if (msg.exprs != null && msg.exprs.size() > 0)
			 for (String[] col : msg.exprs)
				selct.col((String)col[Ix.exprExpr], (String)col[Ix.exprAlais]);
		
		/* Sample of join on parsing:
		0	l
		1	a_roles
		2	R
		3	U.roleId=R.roleId or U.roleId = 'admin' and U.orgId in ('Mossad', 'MI6', 'CIA', 'SVR', 'ChaoYang People')

		select userId userId, userName userName, mobile mobile, dept.orgId orgId, o.orgName orgName, 
		dept.departName departName, dept.departId departId, R.roleId roleId, R.roleName roleName, notes notes 
		from a_user U 
		join a_reg_org o on U.orgId = o.orgId 
		left outer join a_org_depart dept on U.departId = dept.departId 
		left outer join a_roles R on U.roleId = R.roleId OR U.roleId = 'admin' AND U.orgId in ('Mossad', 'MI6', 'CIA', 'SVR', 'ChaoYang People') 
		where U.userName like '%Uz%'
		*/

		if (msg.joins != null && msg.joins.size() > 0) {
			for (Object[] j : msg.joins)
				if (j[Ix.joinTabl] instanceof AnQueryReq) {
					Query q = buildSelct((AnQueryReq)j[Ix.joinTabl], usr, st0);
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
		
		if (msg.where != null && msg.where.size() > 0)
			for (Object[] cond : msg.where)
				if (len(cond) == 2)
					selct.whereEq((String)cond[0],
								  (String)cond[1]);
				else
					selct.where((String)cond[Ix.predicateOper],
								(String)cond[Ix.predicateL],
								(String)cond[Ix.predicateR]);
		// GROUP BY
		selct.groupby(msg.groups);
		
		// HAVING
		if (msg.havings != null && msg.havings.size() > 0)
			for(Object[] havin : msg.havings)
				selct.having((String)havin[Ix.predicateOper],
							(String)havin[Ix.predicateL],
							(String)havin[Ix.predicateR]);

		// ORDER BY
		selct.orderby(msg.orders);
		
		if (msg.limt != null)
			selct.limit(msg.limt[0], msg.limt.length > 1 ? msg.limt[1] : null);
		
		return selct;
	}

	/**
	 * Query with help of {@link #buildSelct(AnQueryReq, IUser, DATranscxt)}.
	 * 
	 * @param msg
	 * @param usr
	 * @param st 
	 * @return result set
	 * @throws SQLException
	 * @throws TransException
	 */
	public static AnResultset query(AnQueryReq msg, IUser usr, DATranscxt st) throws SQLException, TransException {
		Query selct = buildSelct(msg, usr, st);
		SemanticObject s = selct.rs(st.instancontxt(Connects.uri2conn(msg.uri()), usr));
		return (AnResultset) s.rs(0);
	}
}
