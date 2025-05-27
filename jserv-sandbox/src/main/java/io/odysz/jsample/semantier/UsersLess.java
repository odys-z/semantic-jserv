package io.odysz.jsample.semantier;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.AnsonException;
import io.odysz.common.LangExt;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.helper.Html;
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
import io.oz.jsample.semantier.UserstReq;
import io.oz.jsample.semantier.UserstReq.A;
import io.oz.sandbox.SandRobot;
import io.oz.sandbox.protocol.Sandport;
import io.oz.sandbox.utils.StrRes;

@WebServlet(description = "Semantic tier: users", urlPatterns = { "/users.less" })
public class UsersLess extends ServPort<UserstReq> {

	private static final long serialVersionUID = 1L;

	protected static DATranscxt st;

	private static String mtabl = "a_users";

	static {
		try {
			st = new DATranscxt(null);
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	public UsersLess() {
		super(Sandport.userstier);
	}

	@Override
	protected void onGet(AnsonMsg<UserstReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {

			UserstReq jreq = msg.body(0);
			IUser usr = new SandRobot(msg.addr());

			try {
				AnsonMsg<AnsonResp> rsp = null;
				if (UserstReq.A.records.equals(jreq.a()))
					rsp = records(jreq, usr);
				else if (UserstReq.A.rec.equals(jreq.a()))
					rsp = rec(jreq, usr);
				else if (UserstReq.A.avatar.equals(jreq.a()))
					throw new SemanticException("Please use POST to update database!");
				else if (UserstReq.A.insert.equals(jreq.a()))
					throw new SemanticException("Please use POST to update database!");
				else throw new SemanticException(String.format(
							"request.body.a can not handled: %s\\n" +
							"Only a = [%s, %s, %s, %s] are supported.",
							jreq.a(), A.records, A.rec, A.avatar, A.insert));

				resp.getWriter().write(Html.rs((AnResultset)((AnsonResp) rsp.body()).rs(0)));
			} catch (TransException | SQLException e) {
				e.printStackTrace();
			}
	}

	@Override
	protected void onPost(AnsonMsg<UserstReq> jmsg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		
		resp.setCharacterEncoding("UTF-8");
		try {
			// IUser usr = JSingleton.getSessionVerifier().verify(jmsg.header());
			IUser usr = new SandRobot(jmsg.addr());

			UserstReq jreq = jmsg.body(0);

			AnsonMsg<AnsonResp> rsp = null;
			if (UserstReq.A.records.equals(jreq.a()))
				rsp = records(jreq, usr);
			else if (UserstReq.A.rec.equals(jreq.a()))
				rsp = rec(jreq, usr);
			else if (UserstReq.A.avatar.equals(jreq.a()))
				rsp = avatar(jreq, usr);
			else if (UserstReq.A.insert.equals(jreq.a()))
				rsp = ins(jreq, usr);
			else throw new SemanticException(String.format(
						"request.body.a can not handled: %s\\n" +
						"Only a = [%s, %s, %s, %s] are supported.",
						jreq.a(), A.records, A.rec, A.avatar, A.insert));

			write(resp, rsp);
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) { e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	protected AnsonMsg<AnsonResp> avatar(UserstReq jreq, IUser usr)
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

		if (LangExt.isblank(jreq.pk()))
			throw new TransException("FIXME");
		SemanticObject res = (SemanticObject)u
				.whereEq("userId", jreq.pk())// FIXME jreq.userId?
				.u(st.instancontxt(Connects.uri2conn(jreq.uri()), usr));

		return ok(new AnsonResp().msg(res.msg()));
	}

	protected AnsonMsg<AnsonResp> ins(UserstReq jreq, IUser usr)
			throws SemanticException, TransException, SQLException {
		if (jreq.record == null)
			throw new SemanticException(StrRes.insert_null_record);
		
		ISemantext stx = st.instancontxt(Connects.uri2conn(jreq.uri()), usr);

		AnResultset rs = (AnResultset) st.select(mtabl, "u")
				.col(Funcall.count("userId"), "c")
				.whereEq("userId", jreq.userId())
				.rs(stx)
				.rs(0);

		rs.beforeFirst().next();
		if (rs.getInt("c") > 0)
			throw new SemanticException(StrRes.logid_exits);

		SemanticObject res = (SemanticObject)
				((Insert) jreq.nvs(st.insert(mtabl, usr)))
				.ins(stx);

		return ok(new AnsonResp().data(res.props()));
	}

	protected AnsonMsg<AnsonResp> rec(UserstReq jreq, IUser usr) throws TransException, SQLException {
		AnResultset rs = (AnResultset) st
			.select(mtabl, "u")
			.col("userId").col("userName").col("roleId").col("orgId").col("nationId").col("birthday")
			.col("''", "pswd")
			.whereEq("userId", jreq.userId())
			.rs(st.instancontxt(Connects.uri2conn(jreq.uri()), usr))
			.rs(0);

		return ok(new AnsonResp().rs(rs));
	}

	protected AnsonMsg<AnsonResp> records(UserstReq jreq, IUser usr)
			throws SemanticException, TransException, SQLException {
		Query q = st.select("a_users", "u")
				.col("userId").col("userName").col("orgName").col("roleName")
				.l("a_orgs", "o", "o.orgId = u.orgId")
				.l("a_roles", "r", "r.roleId = u.roleId");

		if (!LangExt.isEmpty(jreq.userName()))
			q.whereLike("userName", jreq.userName());

		if (!LangExt.isEmpty(jreq.userId()))
			q.whereEq("userId", jreq.userId());

		if (!LangExt.isEmpty(jreq.roleId()))
			q.whereEq("u.roleId", jreq.roleId());

		if (!LangExt.isEmpty(jreq.orgId()))
			q.whereEq("u.orgId", jreq.orgId());

		AnResultset rs = (AnResultset) q
			.rs(st.instancontxt(Connects.uri2conn(jreq.uri()), usr))
			.rs(0);

		return ok(new AnsonResp().rs(rs));
	}

}
