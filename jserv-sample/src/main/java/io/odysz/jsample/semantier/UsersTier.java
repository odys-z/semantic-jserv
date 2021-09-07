package io.odysz.jsample.semantier;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.LangExt;
import io.odysz.jsample.protocol.Samport;
import io.odysz.jsample.semantier.UserstReq.A;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Query;
import io.odysz.transact.x.TransException;

@WebServlet(description = "Semantic tier: users", urlPatterns = { "/users.tier" })
public class UsersTier extends ServPort<UserstReq> {

	private static final long serialVersionUID = 1L;

	static DATranscxt st;

	static {
		try {
			st = new DATranscxt(null);
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	public UsersTier() {
		super(Samport.userstier);
	}

	@Override
	protected void onGet(AnsonMsg<UserstReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		
	}

	@Override
	protected void onPost(AnsonMsg<UserstReq> jmsg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		
		resp.setCharacterEncoding("UTF-8");
		try {
			IUser usr = JSingleton.getSessionVerifier().verify(jmsg.header());

			UserstReq jreq = jmsg.body(0);

			AnsonMsg<AnsonResp> rsp = null;
			if (UserstReq.A.records.equals(jreq.a()))
				rsp = records(jreq, usr);
			else if (UserstReq.A.rec.equals(jreq.a()))
				rsp = rec(jreq, usr);
			else throw new SemanticException(String.format(
						"request.body.a can not handled: %s\\n" +
						"Only a = [%s, %s] are supported.",
						jreq.a(), A.records, A.rec));

			write(resp, rsp);
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	protected AnsonMsg<AnsonResp> rec(UserstReq jreq, IUser usr) throws TransException, SQLException {
		AnResultset rs = (AnResultset) st
			.select("a_users", "u")
			.whereEq("userId", jreq.userId)
			.rs(st.instancontxt(Connects.uri2conn(jreq.uri()), usr))
			.rs(0);

		return ok(new AnsonResp().rs(rs));
	}

	protected AnsonMsg<AnsonResp> records(UserstReq jreq, IUser usr)
			throws SemanticException, TransException, SQLException {
		Query q = st.select("a_users", "u")
				.col("userId").col("userName").col("orgName").col("roleName")
				.j("a_orgs", "o", "o.orgId = u.orgId")
				.l("a_roles", "r", "r.roleId = u.roleId");

		if (!LangExt.isEmpty(jreq.userName))
			q.where("%", "roleName", jreq.userName);

		if (!LangExt.isEmpty(jreq.userId))
			q.whereEq("userId", jreq.userId);

		AnResultset rs = (AnResultset) q
			.rs(st.instancontxt(Connects.uri2conn(jreq.uri()), usr))
			.rs(0);

		return ok(new AnsonResp().rs(rs));
	}

}
