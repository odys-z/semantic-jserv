package io.odysz.semantic.jserv.U;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JMessage.MsgCode;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.semantic.jserv.helper.ServletAdapter;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.ISessionVerifier;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Update;
import io.odysz.transact.sql.Query.Ix;
import io.odysz.transact.x.TransException;

@WebServlet(description = "querying db via Semantic.DA", urlPatterns = { "/u.serv" })
public class SUpdate extends HttpServlet {
	private static final long serialVersionUID = 1L;

	private static final Port p = Port.update;

	private static DATranscxt st;
	static JHelper<UpdateReq> jreqHelper;
	protected static ISessionVerifier verifier;

	static {
		st = JSingleton.defltScxt;
		jreqHelper = new JHelper<UpdateReq>();
		verifier = JSingleton.getSessionVerifier();
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		try {
			// url = .../update.serv?req={header: {...}, body: []}
			JMessage<UpdateReq> msg = ServletAdapter.<UpdateReq>read(req, jreqHelper, UpdateReq.class);
			
			IUser usr = verifier.verify(msg.header());
			
			SemanticObject res = update(msg.body().get(0), usr);
			
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
			JMessage<UpdateReq> msg = ServletAdapter.<UpdateReq>read(req, jreqHelper, UpdateReq.class);
			in.close();
			
			IUser usr = verifier.verify(msg.header());

			// validate requires
			UpdateReq q = msg.body(0);
			q.validate();

			SemanticObject res = update(q, usr);
			
			ServletAdapter.write(resp, res);

		} catch (SsException e) {
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSession, e.getMessage()));
		} catch (SemanticException e) {
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			e.printStackTrace(); // only for debug convenience
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exTransct, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exGeneral, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	private SemanticObject update(UpdateReq msg, IUser usr) throws SQLException, TransException {
		Update upd = st.update(msg.mtabl, usr);

		SemanticObject res = (SemanticObject) upd
				.nvs(msg.nvs)
				.where(tolerateNv(msg.where))
				// .post(toStatmts(msg.postUpds))
				.post(postUpds(msg, st, usr))
				.u(st.instancontxt(usr));
		if (res == null)
			// stop SelvletAdapter.writer(null) error
			return new SemanticObject()
					.code(MsgCode.ok.name())
					.msg("");
		return res.code(MsgCode.ok.name());
	}

	/**Change [n-v] to ["=", n, "'v'"]
	 * @param where
	 * @return
	 */
	private ArrayList<String[]> tolerateNv(ArrayList<String[]> where) {
		if (where != null)
			for (int ix = 0; ix < where.size(); ix++) {
				String[] nv = where.get(ix);
				if (nv != null && nv.length == 2) {
					String v = nv[Ix.nvv];

					// v can be large, performance can be improved
					if (v.startsWith("'"))
						where.set(ix, new String[] {"=", nv[Ix.nvn], v});
					else
						where.set(ix, new String[] {"=", nv[Ix.nvn], "'" + v + "'"});
				}
			}
		return where;
	}

	/**convert request {@link UpdateReq} to {@link io.odysz.transact.sql.Statement}
	 * @param msg
	 * @param st
	 * @param usr
	 * @return
	 */
	ArrayList<Update> postUpds(UpdateReq msg, DATranscxt st, IUser usr) {
		if (msg.postUpds != null) {
			ArrayList<Update> posts = new ArrayList<Update>(msg.postUpds.size());
			for (UpdateReq pst : msg.postUpds) {
				Update upd = st.update(pst.mtabl, usr);
				posts.add(upd);
			}
			return posts;
		}
		return null;
	}
}
