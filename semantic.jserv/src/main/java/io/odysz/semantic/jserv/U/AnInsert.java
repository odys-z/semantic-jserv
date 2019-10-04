package io.odysz.semantic.jserv.U;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.common.Utils;
import io.odysz.common.dbtype;
import io.odysz.module.rs.AnResultset;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.ServHandler;
import io.odysz.semantic.jserv.helper.Html;
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
@WebServlet(description = "querying db via Semantic.DA", urlPatterns = { "/r.serv11" })
public class AnQuery extends ServHandler<AnInsertReq> {

	@Override
	public void init() throws ServletException {
		super.init();
		p = Port.query;
	}

	protected static ISessionVerifier verifier;
	protected static DATranscxt st;

	static {
		st = JSingleton.defltScxt;
		verifier = JSingleton.getSessionVerifierV11();
	}

	@Override
	protected void onGet(AnsonMsg<AnInsertReq> msg, HttpServletResponse resp)
			throws ServletException, IOException {
		if (ServFlags.query)
			Utils.logi("---------- squery (r.serv11) get ----------");
		resp.setCharacterEncoding("UTF-8");
		try {
			IUser usr = verifier.verify(msg.header());
			AnResultset rs = query(msg.body(0), usr);
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
	protected void onPost(AnsonMsg<AnInsertReq> msg, HttpServletResponse resp) throws IOException {
		if (ServFlags.query)
			Utils.logi("========== squery (r.serv11) post ==========");

		resp.setCharacterEncoding("UTF-8");
		try {
			IUser usr = verifier.verify(msg.header());
			AnResultset rs = query(msg.body(0), usr);

			write(resp, ok(rs), msg.opts());
		} catch (SsException e) {
			// ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSession, e.getMessage()));
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} catch (SemanticException e) {
			// ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSemantic, e.getMessage()));
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			e.printStackTrace();
			// ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exTransct, e.getMessage()));
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			// ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exGeneral, e.getMessage()));
			write(resp, err(MsgCode.exGeneral, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

}
