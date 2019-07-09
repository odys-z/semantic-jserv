package io.odysz.semantic.jserv.U;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JMessage.MsgCode;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jprotocol.JProtocol.CRUD;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.semantic.jserv.helper.ServletAdapter;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.ISessionVerifier;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.x.TransException;

@WebServlet(description = "querying db via Semantic.DA", urlPatterns = { "/c.serv" })
public class JInsert extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private static final Port p = Port.insert;

	private static DATranscxt st;
	static JHelper<InsertReq> jreqHelper;
	protected static ISessionVerifier verifier;

	static {
		st = JSingleton.defltScxt;
		jreqHelper = new JHelper<InsertReq>();
		verifier = JSingleton.getSessionVerifier();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		try {
			JMessage<InsertReq> msg = ServletAdapter.<InsertReq>read(req, jreqHelper, InsertReq.class);
			
			IUser usr = verifier.verify(msg.header());
			
			SemanticObject res;
			JBody q = msg.body(0);
			if (CRUD.C.equals(q .a()))
				res = inst((InsertReq) q, usr);
			else
				throw new SemanticException("%s only handling a=i. Please update client!", p.name());
			
			resp.setCharacterEncoding("UTF-8");
			resp.getWriter().write(Html.map(res));
			resp.flushBuffer();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (TransException e) {
			e.printStackTrace();
		} catch (ReflectiveOperationException e) {
			e.printStackTrace();
		} catch (SsException e) {
			e.printStackTrace();
		}
	}

	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		try {
			InputStream in = req.getInputStream();
			JMessage<InsertReq> msg = ServletAdapter.<InsertReq>read(req, jreqHelper, InsertReq.class);
			in.close();
			
			IUser usr = verifier.verify(msg.header());

			InsertReq q = msg.body(0);
			q.validate();

			SemanticObject res = null;
			if (CRUD.C.equals(q.a()))
				res = inst((InsertReq) q, usr);
			else
				throw new SemanticException("i.serv only handling a=i. Please update client!");
			
			ServletAdapter.write(resp, res, msg.opts());

		} catch (SsException e) {
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSession, e.getMessage()));
		} catch (SemanticException e) {
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			e.printStackTrace(); // only for debugging convenience
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exTransct, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exGeneral, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	/**Handle insert request, generate {@link Insert}, commit, return results.
	 * @param msg
	 * @param usr
	 * @return results
	 * @throws SQLException
	 * @throws TransException
	 */
	private SemanticObject inst(InsertReq msg, IUser usr)
			throws TransException, SQLException {
		Insert upd = st.insert(msg.mtabl, usr);

		String[] cols = msg.cols();
		if (cols == null || cols.length == 0)
			throw new SemanticException("Can't insert %s values without columns sepecification.", msg.mtabl);

		SemanticObject res = (SemanticObject) upd
				.cols(cols)
				.values(msg.values())
				.where(JUpdate.tolerateNv(msg.where))
				.post(JUpdate.postUpds(msg.postUpds, usr))
				.ins(st.instancontxt(msg.conn(), usr));
		if (res == null)
			// stop SelvletAdapter.writer(null) error
			return new SemanticObject()
					.port(p.name())
					.code(MsgCode.ok.name());
		return res.port(p.name()).code(MsgCode.ok.name());
	}
}
