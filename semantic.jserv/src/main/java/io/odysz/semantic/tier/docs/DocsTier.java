package io.odysz.semantic.tier.docs;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;

@WebServlet(description = "Document uploading tier", urlPatterns = { "/docs.tier" })
public class DocsTier extends ServPort<DocsReq> {
	static DATranscxt st;

	static {
		try {
			st = new DATranscxt(null);
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}

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
			if (A.records.equals(jreq.a()))
				rsp = list(jreq, usr);
			else if (A.rec.equals(jreq.a()))
				rsp = doc(jreq, usr);
			else if (A.upload.equals(jreq.a()))
				rsp = upload(jreq, usr);
			else if (A.del.equals(jreq.a()))
				rsp = del(jreq, usr);
			else throw new SemanticException(String.format(
						"request.body.a can not handled: %s\\n" +
						"Only a = [%s, %s, %s, %s] are supported.",
						jreq.a(), A.records, A.rec, A.upload, A.del));

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
			.whereEq("docId", jreq.docId)
			.d(stx);
		
		return new AnsonResp().msg("ok").data(doc.props());
	}

	private AnsonResp upload(DocsReq jreq, IUser usr) throws TransException, SQLException {
		String conn = Connects.uri2conn(jreq.uri());
		ISemantext stx = st.instancontxt(conn, usr);

		SemanticObject doc = (SemanticObject) st.delete("n_docs", usr)
			.whereEq("docId", jreq.docId)
			.post(st.insert("n_docs")
				.nv("docName", jreq.docName).nv("mime", jreq.mime).nv("uri", jreq.content64))
			.d(stx);
		
		return new AnsonResp().msg("ok").data(doc.props());
	}

	private AnsonResp doc(DocsReq jreq, IUser usr) throws TransException, SQLException {
		String conn = Connects.uri2conn(jreq.uri());
		ISemantext stx = st.instancontxt(conn, usr);

		AnResultset doc = ((AnResultset) st.select("n_docs", "d")
			.l("n_doc_kid", "dk", "d.docId = dk.docId")
			.col("d.docId").col("docName").col("docType").col("uri")
			.rs(stx)
			.rs(0));
		
		return new AnsonResp().msg("ok").rs(doc);
	}

	private AnsonResp list(DocsReq req, IUser usr) throws TransException, SQLException {
		String conn = Connects.uri2conn(req.uri());
		ISemantext stx = st.instancontxt(conn, usr);

		AnResultset docs = ((AnResultset) st.select("n_docs", "d")
			.l("n_doc_kid", "dk", "d.docId = dk.docId")
			.col("d.docId").col("docName").col("docType") // .col("uri") - too big
			.col(Funcall.sqlCount("dk.userId"), "sharings")
			.col(Funcall.sqlCount(Funcall.sqlIfElse(stx, "dk.state = conf", "1", "null")), "confirmed")
			.orderby("d.optime", "desc")
			.rs(stx)
			.rs(0));
		
		return new AnsonResp().msg("ok").rs(docs);
	}

}
