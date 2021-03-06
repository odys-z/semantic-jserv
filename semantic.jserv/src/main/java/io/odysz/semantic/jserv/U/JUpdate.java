//package io.odysz.semantic.jserv.U;
//
//import java.io.IOException;
//import java.io.InputStream;
//import java.sql.SQLException;
//import java.util.ArrayList;
//
//import javax.servlet.ServletException;
//import javax.servlet.annotation.WebServlet;
//import javax.servlet.http.HttpServlet;
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
//
//import io.odysz.common.Utils;
//import io.odysz.semantic.DATranscxt;
//import io.odysz.semantic.jprotocol.JHelper;
//import io.odysz.semantic.jprotocol.JMessage;
//import io.odysz.semantic.jprotocol.JMessage.MsgCode;
//import io.odysz.semantic.jprotocol.JMessage.Port;
//import io.odysz.semantic.jprotocol.JProtocol;
//import io.odysz.semantic.jprotocol.JProtocol.CRUD;
//import io.odysz.semantic.jserv.JSingleton;
//import io.odysz.semantic.jserv.helper.Html;
//import io.odysz.semantic.jserv.helper.ServletAdapter;
//import io.odysz.semantic.jserv.x.SsException;
//import io.odysz.semantic.jsession.ISessionVerifier;
//import io.odysz.semantics.IUser;
//import io.odysz.semantics.SemanticObject;
//import io.odysz.semantics.x.SemanticException;
//import io.odysz.transact.sql.Delete;
//import io.odysz.transact.sql.Query.Ix;
//import io.odysz.transact.sql.Statement;
//import io.odysz.transact.sql.Update;
//import io.odysz.transact.x.TransException;
//
//@WebServlet(description = "querying db via Semantic.DA", urlPatterns = { "/u.serv" })
//public class JUpdate extends HttpServlet {
//	private static final long serialVersionUID = 1L;
//
//	private static final Port p = Port.update;
//
//	private static DATranscxt st;
//	static JHelper<UpdateReq> jreqHelper;
//	protected static ISessionVerifier verifier;
//
//	static {
//		st = JSingleton.defltScxt;
//		jreqHelper = new JHelper<UpdateReq>();
//		verifier = JSingleton.getSessionVerifier();
//	}
//
//	@Override
//	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
//			throws ServletException, IOException {
//		
//		try {
//			// url = .../update.serv?req={header: {...}, body: []}
//			JMessage<UpdateReq> msg = ServletAdapter.<UpdateReq>read(req, jreqHelper, UpdateReq.class);
//			
//			IUser usr = verifier.verify(msg.header());
//			
//			SemanticObject res = updt(msg.body().get(0), usr);
//			
//			resp.setCharacterEncoding("UTF-8");
//			resp.getWriter().write(Html.map(res));
//			resp.flushBuffer();
//		} catch (SQLException e) {
//			e.printStackTrace();
//		} catch (TransException e) {
//			e.printStackTrace();
//		} catch (ReflectiveOperationException e) {
//			e.printStackTrace();
//		} catch (SsException e) {
//			e.printStackTrace();
//		}
//	}
//
//	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
//		try {
//			InputStream in = req.getInputStream();
//			JMessage<UpdateReq> msg = ServletAdapter.<UpdateReq>read(req, jreqHelper, UpdateReq.class);
//			in.close();
//			
//			IUser usr = verifier.verify(msg.header());
//
//			// validate requires
//			UpdateReq q = msg.body(0);
//			q.validate();
//
//			SemanticObject res = null;
//			if (CRUD.U.equals(q.a()))
//				res = updt(q, usr);
//			else if (CRUD.C.equals(q.a()))
//				// res = inst((InsertReq) q, usr);
//				throw new SemanticException("Inserting Request is handled by i.serv. Please update client.");
//			else if (CRUD.D.equals(q.a()))
//				res = delt(q, usr);
//			
//			ServletAdapter.write(resp, res, msg.opts());
//
//		} catch (SsException e) {
//			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSession, e.getMessage()));
//		} catch (SemanticException e) {
//			// ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSemantic, e.getMessage()));
////			if (e.ex() == null)
////				ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSemantic, e.ex()));
////			else
////				ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSemantic, e.getMessage()));
//				ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSemantic, e.getMessage(), e.ex()));
//		} catch (SQLException | TransException e) {
//			e.printStackTrace(); // only for debug convenience
//			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exTransct, e.getMessage()));
//		} catch (Exception e) {
//			e.printStackTrace();
//			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exGeneral, e.getMessage()));
//		} finally {
//			resp.flushBuffer();
//		}
//	}
//
//	/**Handle update request, generate {@link Update}, commit, return results.
//	 * @param msg
//	 * @param usr
//	 * @return results
//	 * @throws SQLException
//	 * @throws TransException
//	 */
//	private SemanticObject updt(UpdateReq msg, IUser usr) throws SQLException, TransException {
//		Update upd = st.update(msg.mtabl, usr);
//
//		SemanticObject res = (SemanticObject) upd
//				.nvs(msg.nvs)
//				.where(tolerateNv(msg.where))
//				.post(postUpds(msg.postUpds, usr))
//				// .attachs(msg.attacheds)
//				.limit(msg.limt)
//				.u(st.instancontxt(msg.conn(), usr));
//		if (res == null)
//			// stop SelvletAdapter.writer(null) error
//			return new SemanticObject()
//					.port(p.name())
//					.code(MsgCode.ok.name())
//					.msg("");
//		return res.port(p.name())
//					.code(MsgCode.ok.name());
//	}
//
//	/**Change [n-v] to ["=", n, "'v'"], tolerate client's behavior.
//	 * @param where
//	 * @return predicates[[logic, n, v], ...]
//	 */
//	static ArrayList<Object[]> tolerateNv(ArrayList<Object[]> where) {
//		if (where != null) {
//			for (int ix = 0; ix < where.size(); ix++) {
//				Object[] nv = where.get(ix);
//				if (nv != null && nv.length == 2) {
//					Object v = nv[Ix.nvv];
//					if (v == null) {
//						// client has done something wrong
//						where.set(ix, null); // not remove(), because it's still iterating
//						continue;
//					}
//
//					// v can be large, performance can be improved
//					if (v instanceof String && ((String)v).startsWith("'"))
//						where.set(ix, new Object[] {"=", nv[Ix.nvn], v});
//					else
//						where.set(ix, new String[] {"=", (String)nv[Ix.nvn], "'" + v + "'"});
//				}
//			}
//			where.removeIf(m -> m == null);
//		}
//
//		return where;
//	}
//
//	/**convert update requests' body, usually from msg's post requests,
//	 * list of ({@link UpdateReq}) to {@link io.odysz.transact.sql.Statement}.
//	 * @param updreq
//	 * @param st
//	 * @param usr
//	 * @return statements
//	 * @throws TransException 
//	 */
//	public static ArrayList<Statement<?>> postUpds(ArrayList<UpdateReq> updreq, IUser usr) throws TransException {
//		if (updreq != null) {
//			ArrayList<Statement<?>> posts = new ArrayList<Statement<?>>(updreq.size());
//			for (UpdateReq pst : updreq) {
//				Statement<?> upd = null;
//				if (CRUD.C.equals(pst.a()))
//					upd = st.insert(pst.mtabl, usr)
//							.cols(pst.cols())
//							.values(pst.values());
//				else if (CRUD.U.equals(pst.a()))
//					upd = st.update(pst.mtabl, usr)
//							.nvs(pst.nvs);
//				else if (CRUD.D.equals(pst.a()))
//					upd = st.delete(pst.mtabl, usr);
//				else if (pst != null) {
//					Utils.warn("Can't handle request:\n" + pst.toString());
//					continue;
//				}
//
//				posts.add(upd.where(pst.where)
//							.post(postUpds(pst.postUpds, usr)));
//			}
//			return posts;
//		}
//		return null;
//	}
//	
//	/**Handle delete request, generate {@link Delete}, commit, return results.
//	 * @param msg
//	 * @param usr
//	 * @return results
//	 * @throws SQLException
//	 * @throws TransException
//	 */
//	private SemanticObject delt(UpdateReq msg, IUser usr)
//			throws TransException, SQLException {
//		Delete del = st.delete(msg.mtabl, usr);
//		
//		SemanticObject res = (SemanticObject) del
//				.where(tolerateNv(msg.where))
//				.post(postUpds(msg.postUpds, usr))
//				.d(st.instancontxt(msg.conn(), usr));
//		if (res == null)
//			// stop SelvletAdapter.writer(null) error
//			return new SemanticObject()
//					.port(p.name())
//					.code(MsgCode.ok.name());
//		return res.port(p.name()).code(MsgCode.ok.name());
//	}
//}
