package io.odysz.semantic.tier.docs;

import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isblank;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.AnsonException;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.parts.Logic.op;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;

/** @deprecated */
@WebServlet(description = "Document uploading tier", urlPatterns = { "/docs.tier-delete" })
public class DocsTier extends ServPort<DocsReq> {
	public DocsTier() {
		super(Port.docstier);
	}

	@Override
	protected void onGet(AnsonMsg<DocsReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
	}

	@Override
	protected void onPost(AnsonMsg<DocsReq> jmsg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		resp.setCharacterEncoding("UTF-8");
		try {
			IUser usr = JSingleton.getSessionVerifier().verify(jmsg.header());

			DocsReq jreq = jmsg.body(0);

			AnsonResp rsp = null;
			if (A.syncdocs.equals(jreq.a()))
				throw new SemanticException("Function not used.");
				// rsp = list(jreq, usr);
			else if (A.mydocs.equals(jreq.a()))
				// rsp = mydocs(jreq, usr);
				throw new SemanticException("Function not used.");
			else if (A.rec.equals(jreq.a()))
				rsp = doc(jreq, usr);
			else if (A.upload.equals(jreq.a()))
				rsp = upload(jreq, usr);
			else if (A.del.equals(jreq.a()))
				rsp = del(jreq, usr);
			else throw new SemanticException(f(
						"request.body.a can not handled: %s\\n",
						jreq.a()));

			write(resp, ok(rsp));
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	private AnsonResp del(DocsReq jreq, IUser usr) throws TransException, SQLException {
		String conn = Connects.uri2conn(jreq.uri());
		ISemantext stx = st.instancontxt(conn, usr);

		SemanticObject doc = (SemanticObject) st.delete("n_docs", usr)
			.whereIn("docId", jreq.deletings)
			.d(stx);
		
		return new AnsonResp().msg("ok").data(doc.props());
	}

	private AnsonResp upload(DocsReq jreq, IUser usr) throws TransException, SQLException {
		String conn = Connects.uri2conn(jreq.uri());
		ISemantext stx = st.instancontxt(conn, usr);

		SemanticObject doc = (SemanticObject) st.delete("n_docs", usr)
			.whereEq("docId", jreq.doc.recId)
			.post(st.insert("n_docs")
				.nv("docName", jreq.doc.pname).nv("mime", jreq.doc.mime).nv("uri", jreq.doc.uri64).nv("userId", usr.uid()))
			.d(stx);
		
		return new AnsonResp().msg("ok").data(doc.props());
	}

	private AnsonResp doc(DocsReq jreq, IUser usr) throws TransException, SQLException {
		String conn = Connects.uri2conn(jreq.uri());
		ISemantext stx = st.instancontxt(conn, usr);

		AnResultset doc = ((AnResultset) st.select("n_docs", "d")
			.col("d.docId").col("docName").col("mime").col(Funcall.extfile("uri"), "uri64")
			.whereEq("d.docId", jreq.doc.recId)
			.rs(stx)
			.rs(0));
		
		return new AnsonResp().msg("ok").rs(doc);
	}

	/**
	 * @deprecated function not used
	 * @param req
	 * @param usr
	 * @return
	 * @throws TransException
	 * @throws SQLException
	 */
	AnsonResp mydocs(DocsReq req, IUser usr) throws TransException, SQLException {
		String conn = Connects.uri2conn(req.uri());
		ISemantext stx = st.instancontxt(conn, usr);

		Query q = st.select("n_docs", "d")
			.j("n_doc_kid", "dk", "d.docId = dk.docId")
			.col("d.docId").col("docName").col("mime").col("d.userId", "sharer") // .col("uri") - too big
			/* MEMO:
			 * This makes Ever-Connect/docs-share unavailable
			 * .col(Funcall.count(Funcall.ifElse(String.format("dk.state = '%s'", DocsReq.State.confirmed), "1", "null")), "confirmed")
			 */
			.whereEq("dk.userId", usr.uid())
			.groupby("d.docId")
			.orderby("d.optime", "desc");
		
		if (!isblank(req.doc.pname))
			q.whereLike("dk.state", req.doc.pname);
		
		if (!isblank(req.doc.mime))
			q.where_(op.rlike, "d.mime", (isblank(req.doc.mime) ? "" : req.doc.mime));

		if (!isblank(req.doc.shareflag))
			q.whereEq("dk.state", req.doc.shareflag);

		AnResultset docs = ((AnResultset) q
			.rs(stx)
			.rs(0));
		
		return new AnsonResp().msg("ok").rs(docs);
	}

	/**
	 * Get n_docs records where userId and mime matched.
	 *  
	 * @deprecated function not used
	 * @param req
	 * @param usr
	 * @return response with doc result set
	 * @throws TransException
	 * @throws SQLException
	 */
	protected AnsonResp list(DocsReq req, IUser usr) throws TransException, SQLException {
		String conn = Connects.uri2conn(req.uri());
		ISemantext stx = st.instancontxt(conn, usr);

		AnResultset docs = ((AnResultset) st.select("n_docs", "d")
			.l("n_doc_kid", "dk", "d.docId = dk.docId")
			.col("d.docId").col("docName").col("mime") // .col("uri") - too big
			.col(Funcall.count("dk.userId"), "sharings")
			/* Memo:
			 * Ever-connect/docs-share is no more available.
			 * .col(Funcall.count(Funcall.ifElse(String.format("dk.state = '%s'", DocsReq.State.confirmed), "1", "null")), "confirmed")
			 */
			.whereEq("d.userId", usr.uid())
			.where(op.rlike, "d.mime", "'" + (isblank(req.doc.mime) ? "" : req.doc.mime) + "'")
			.groupby("d.docId")
			.orderby("d.optime", "desc")
			.rs(stx)
			.rs(0));
		
		return new AnsonResp().msg("ok").rs(docs);
	}

}
