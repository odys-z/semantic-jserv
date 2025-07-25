package io.oz.jsample.semantier;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.AnsonException;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.tier.Relations;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Update;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;
import io.oz.jsample.semantier.UserstReq.A;

import static io.odysz.common.LangExt.isblank;

@WebServlet(description = "Semantic tier: users", urlPatterns = { "/users.tier" })
public class UsersTier extends ServPort<UserstReq> {

	private static final long serialVersionUID = 1L;

	protected static DATranscxt st;

	public static String mtabl = "a_users";

	static {
		try {
			st = new DATranscxt(null);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public UsersTier() {
		super(Port.userstier);
	}

	@Override
	protected void onGet(AnsonMsg<UserstReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		
		if (ServFlags.jsample)
			Utils.logi("---------- ever-connect / users.tier GET ----------");
	}

	@Override
	protected void onPost(AnsonMsg<UserstReq> jmsg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		
		resp.setCharacterEncoding("UTF-8");
		try {
			IUser usr = JSingleton.getSessionVerifier().verify(jmsg.header());

			UserstReq jreq = jmsg.body(0);

			AnsonResp rsp = null;
			if (A.records.equals(jreq.a()))
				rsp = records(jreq, usr);
			else if (A.rec.equals(jreq.a()))
				rsp = rec(jreq, usr);
			else if (A.insert.equals(jreq.a()))
				rsp = ins(jreq, usr);
			else if (A.update.equals(jreq.a()))
				rsp = upd(jreq, usr);
			else if (A.del.equals(jreq.a()))
				rsp = del(jreq, usr);
			else throw new SemanticException(String.format(
				"request.body.a can not handled: %s\\n" +
				"Only a = [%s, %s, %s, %s, %s] are supported.",
				jreq.a(), A.records, A.rec, A.insert, A.update, A.del));

			write(resp, ok(rsp));
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			if (ServFlags.jsample) e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	protected AnsonResp del(UserstReq jreq, IUser usr)
			throws SemanticException, TransException, SQLException {

		if (jreq.deletings == null && jreq.deletings.length > 0)
			throw new SemanticException("Failed on deleting null ids.");

		SemanticObject res = (SemanticObject) st.delete(mtabl, usr)
			.whereIn("userId", jreq.deletings)
			.d(st.instancontxt(Connects.uri2conn(jreq.uri()), usr));

		return new AnsonResp().msg(res.msg());
	}

	protected AnsonResp upd(UserstReq jreq, IUser usr)
			throws SemanticException, TransException, SQLException {
		if (jreq.record == null && jreq.relations == null)
			throw new SemanticException("Failed on inserting null record.");

		Update u = st.update(mtabl, usr);
		jreq.nvs(u);
		
		if (jreq.relations != null && jreq.relations.size() > 0) {
			// shouldn't happen for Anclient/test/jsample/users.jsx
			for (Relations r : jreq.relations)
				u.post(r.update(st));
		}

		SemanticObject res = (SemanticObject)u
				.whereEq("userId", isblank(jreq.pk) ? jreq.userId : jreq.pk)
				// .whereEq("userId", jreq.pk)
				.u(st.instancontxt(Connects.uri2conn(jreq.uri()), usr));

		return new AnsonResp().msg(res.msg());
	}

	protected AnsonResp ins(UserstReq jreq, IUser usr)
			throws SemanticException, TransException, SQLException {
		if (jreq.record == null)
			throw new SemanticException("Inserting a null record ...");
		
		ISemantext stx = st.instancontxt(Connects.uri2conn(jreq.uri()), usr);

		AnResultset rs = (AnResultset) st.select(mtabl, "u")
				.col(Funcall.count("userId"), "c")
				.whereEq("userId", jreq.record.get("userId"))
				.rs(stx)
				.rs(0);

		rs.beforeFirst().next();
		if (rs.getInt("c") > 0)
			throw new SemanticException("User id already exists.");

		SemanticObject res = (SemanticObject)
				((Insert) jreq.nvs(st.insert(mtabl, usr)))
				.ins(stx);

		return new AnsonResp().data(res.props());
	}

	protected AnsonResp rec(UserstReq jreq, IUser usr) throws TransException, SQLException {
		AnResultset rs = (AnResultset) st
			.select(mtabl, "u")
			.col("userId").col("userName").col("roleId").col("orgId").col("nationId").col("counter").col("birthday")
			.col("''", "pswd")
			.whereEq("userId", jreq.userId)
			.rs(st.instancontxt(Connects.uri2conn(jreq.uri()), usr))
			.rs(0);

		return new AnsonResp().rs(rs);
	}

	protected AnsonResp records(UserstReq jreq, IUser usr)
			throws SemanticException, TransException, SQLException {
		Query q = st.select(mtabl, "u")
				.page(jreq.page)
				.col("userId").col("userName").col("orgName").col("roleName")
				.l("a_orgs", "o", "o.orgId = u.orgId")
				.l("a_roles", "r", "r.roleId = u.roleId");

		if (!LangExt.isEmpty(jreq.userName))
			q.whereLike("userName", jreq.userName);

		if (!LangExt.isEmpty(jreq.userId))
			q.whereEq("userId", jreq.userId);

		if (!LangExt.isEmpty(jreq.roleId))
			q.whereEq("u.roleId", jreq.roleId);

		if (!LangExt.isEmpty(jreq.orgId))
			q.whereEq("u.orgId", jreq.orgId);

		AnResultset rs = (AnResultset) q
			.rs(st.instancontxt(Connects.uri2conn(jreq.uri()), usr))
			.rs(0);

		return new AnsonResp().rs(rs);
	}

}
