package io.odysz.semantic.jserv.U;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.JProtocol.CRUD;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.ISessionVerifier;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Delete;
import io.odysz.transact.sql.Query.Ix;
import io.odysz.transact.sql.Statement;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;

@WebServlet(description = "querying db via Semantic.DA", urlPatterns = { "/u.serv11" })
public class AnUpdate extends ServPort<AnUpdateReq> {
	private static final long serialVersionUID = 1L;

	private static DATranscxt st;
	protected static ISessionVerifier verifier;

	static {
		st = JSingleton.defltScxt;
		verifier = JSingleton.getSessionVerifier();
	}

	@Override
	protected void onGet(AnsonMsg<AnUpdateReq> msg, HttpServletResponse resp)
			throws ServletException, IOException {
		
		try {
			IUser usr = verifier.verify(msg.header());
			
			AnsonMsg<AnsonResp> res = updt(msg.body(0), usr);
			
			resp.setCharacterEncoding("UTF-8");
			resp.getWriter().write(Html.map(res));
			resp.flushBuffer();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (TransException e) {
			e.printStackTrace();
		} catch (SsException e) {
			e.printStackTrace();
		}
	}

	protected void onPost(AnsonMsg<AnUpdateReq> msg, HttpServletResponse resp) throws IOException {
		try {
			IUser usr = verifier.verify(msg.header());

			// validate requires
			AnUpdateReq q = msg.body(0);
			q.validate();

			AnsonMsg<AnsonResp> res = null;
			if (CRUD.U.equals(q.a()))
				res = updt(q, usr);
			else if (CRUD.C.equals(q.a()))
				// res = inst((InsertReq) q, usr);
				throw new SemanticException("Inserting Request is handled by i.serv. Please update client.");
			else if (CRUD.D.equals(q.a()))
				res = delt(q, usr);
			
			write(resp, res, msg.opts());

		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage(), e.ex()));
		} catch (SQLException | TransException e) {
			if (ServFlags.update)
				e.printStackTrace(); // only for debug convenience
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (Exception e) {
			if (ServFlags.update)
				e.printStackTrace();
			write(resp, err(MsgCode.exGeneral, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	/**Handle update request, generate {@link Update}, commit, return results.
	 * @param msg
	 * @param usr
	 * @return results
	 * @throws SQLException
	 * @throws TransException
	 */
	private AnsonMsg<AnsonResp> updt(AnUpdateReq msg, IUser usr) throws SQLException, TransException {
		Update upd = st.update(msg.mtabl, usr);

		SemanticObject res = (SemanticObject) upd
				.nvs(msg.nvs)
				.where(tolerateNv(msg.where))
				.post(postUpds(msg.postUpds, usr))
				// .attachs(msg.attacheds)
				.limit(msg.limt)
				.u(st.instancontxt(msg.conn(), usr));

		if (res == null)
			return new AnsonMsg<AnsonResp>(p, MsgCode.ok);
		return new AnsonMsg<AnsonResp>(p, MsgCode.ok)
				.body(new AnsonResp().data(res.props()));
	}

	/**Change [n-v] to ["=", n, "'v'"], tolerate client's behavior.
	 * @param where
	 * @return predicates[[logic, n, v], ...]
	 */
	static ArrayList<Object[]> tolerateNv(ArrayList<Object[]> where) {
		if (where != null) {
			for (int ix = 0; ix < where.size(); ix++) {
				Object[] nv = where.get(ix);
				if (nv != null && nv.length == 2) {
					Object v = nv[Ix.nvv];
					if (v == null) {
						// client has done something wrong
						where.set(ix, null); // not remove(), because it's still iterating
						continue;
					}

					// v can be large, performance can be improved
					if (v instanceof String && ((String)v).startsWith("'"))
						where.set(ix, new Object[] {"=", nv[Ix.nvn], v});
					else
						where.set(ix, new String[] {"=", (String)nv[Ix.nvn], "'" + v + "'"});
				}
			}
			where.removeIf(m -> m == null);
		}

		return where;
	}

	/**convert update requests' body, usually from msg's post requests,
	 * list of ({@link UpdateReq}) to {@link io.odysz.transact.sql.Statement}.
	 * @param updreq
	 * @param st
	 * @param usr
	 * @return statements
	 * @throws TransException 
	 */
	public static ArrayList<Statement<?>> postUpds(ArrayList<AnUpdateReq> updreq, IUser usr) throws TransException {
		if (updreq != null) {
			ArrayList<Statement<?>> posts = new ArrayList<Statement<?>>(updreq.size());
			for (AnUpdateReq pst : updreq) {
				Statement<?> upd = null;
				if (CRUD.C.equals(pst.a()))
					upd = st.insert(pst.mtabl, usr)
							.cols(pst.cols())
							.values(pst.values());
				else if (CRUD.U.equals(pst.a()))
					upd = st.update(pst.mtabl, usr)
							.nvs(pst.nvs);
				else if (CRUD.D.equals(pst.a()))
					upd = st.delete(pst.mtabl, usr);
				else if (pst != null) {
					Utils.warn("Can't handle request:\n" + pst.toString());
					continue;
				}

				posts.add(upd.where(pst.where)
							.post(postUpds(pst.postUpds, usr)));
			}
			return posts;
		}
		return null;
	}
	
	/**Handle delete request, generate {@link Delete}, commit, return results.
	 * @param msg
	 * @param usr
	 * @return results
	 * @throws SQLException
	 * @throws TransException
	 */
	private AnsonMsg<AnsonResp> delt(AnUpdateReq msg, IUser usr)
			throws TransException, SQLException {
		Delete del = st.delete(msg.mtabl, usr);
		
		SemanticObject res = (SemanticObject) del
				.where(tolerateNv(msg.where))
				.post(postUpds(msg.postUpds, usr))
				.d(st.instancontxt(msg.conn(), usr));

		if (res == null)
			return new AnsonMsg<AnsonResp>(p, MsgCode.ok);
		return new AnsonMsg<AnsonResp>(p, MsgCode.ok)
				.body(new AnsonResp().data(res.props()));
	}
}
