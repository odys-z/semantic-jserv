package io.odysz.semantic.jserv.U;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.common.Utils;
import io.odysz.semantic.CRUD;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.ISessionVerifier;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.x.TransException;

/**CRUD insertion service.
 * @author odys-z@github.com
 */
@WebServlet(description = "querying db via Semantic.DA", urlPatterns = { "/c.serv" })
public class AnInsert extends ServPort<AnInsertReq> {

	public AnInsert() {
		// ody Jul 22, 2021, bug? super(Port.query);
		super(Port.insert);
	}

	protected static ISessionVerifier verifier;
	// protected static DATranscxt st;

	static {
		// st = JSingleton.defltScxt;
		verifier = JSingleton.getSessionVerifier();
	}

	@Override
	protected void onGet(AnsonMsg<AnInsertReq> msg, HttpServletResponse resp)
			throws ServletException, IOException {
		if (ServFlags.update)
			Utils.logi("---------- insert (c.serv) get ----------");
		try {
			IUser usr = verifier.verify(msg.header());

			AnsonMsg<AnsonResp> res;
			AnInsertReq q = msg.body(0);
			if (CRUD.C.equals(q .a()))
				res = inst(q, usr);
			else
				throw new SemanticException("%s only handling a=i. Please update client!", p.name());
			
			resp.getWriter().write(Html.map(res));
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
		if (ServFlags.update)
			Utils.logi("========== insert (c.serv) post ==========");

		try {
			IUser usr = verifier.verify(msg.header());
			AnInsertReq q = msg.body(0);
			q.validate();

			AnsonMsg<AnsonResp> res = null;
			if (CRUD.C.equals(q.a()))
				res = inst((AnInsertReq) q, usr);
			else
				throw new SemanticException("i.serv only handling a=i. Please update client!");

			write(resp, res, msg.opts());
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

	/**Handle insert request, generate {@link Insert} statement,
	 * then commit and return results.
	 * @param msg
	 * @param usr
	 * @return results
	 * @throws SQLException
	 * @throws TransException
	 */
	private AnsonMsg<AnsonResp> inst(AnInsertReq msg, IUser usr)
			throws TransException, SQLException {
		Insert upd = st.insert(msg.mtabl, usr);

		String[] cols = msg.cols();
		if (cols == null || cols.length == 0)
			throw new SemanticException("Can't insert %s values without columns sepecification.", msg.mtabl);

		String connId = Connects.uri2conn(msg.uri());

		SemanticObject res = (SemanticObject) upd
				.cols(cols)
				.values(msg.values())
				.where(AnUpdate.tolerateNv(msg.where))
				.post(AnUpdate.postUpds(st, msg.postUpds, usr))
				.ins(st.instancontxt(connId, usr));
		if (res == null)
			return new AnsonMsg<AnsonResp>(p, MsgCode.ok);
		return new AnsonMsg<AnsonResp>(p, MsgCode.ok)
				.body(new AnsonResp().data(res.props()));
	}
}
