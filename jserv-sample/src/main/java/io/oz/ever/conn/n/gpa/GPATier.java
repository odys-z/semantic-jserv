package io.oz.ever.conn.n.gpa;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.common.dbtype;
import io.odysz.jsample.protocol.Samport;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;
import io.oz.ever.conn.n.gpa.GPAReq.A;

@WebServlet(description = "GPA tier", urlPatterns = { "/gpa.tier" })
public class GPATier extends ServPort<GPAReq> {
	/** * */
	private static final long serialVersionUID = 1L;

	static DATranscxt st;

	static {
		try {
			st = new DATranscxt(null);
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	public GPATier() {
		super(Samport.gpatier);
	}

	@Override
	protected void onGet(AnsonMsg<GPAReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		
		if (StarFlags.gpatier)
			Utils.logi("---------- ever-connect /gpa.tier POST ----------");

	}

	@Override
	protected void onPost(AnsonMsg<GPAReq> jmsg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		if (StarFlags.gpatier)
			Utils.logi("========== ever-connect /quiz.serv POST ==========");

		resp.setCharacterEncoding("UTF-8");
		try {
			GPAReq jreq = jmsg.body(0);
			String a = jreq.a();
			AnsonMsg<GPAResp> rsp = null;

			IUser usr = JSingleton.getSessionVerifier().verify(jmsg.header());

			if (A.gpas.equals(a)) // load
				rsp = gpas(jmsg.body(0), usr);
			else if (A.insert.equals(a)) // load
				rsp = ins(jmsg.body(0), usr);
			else
				throw new SemanticException("request.body.a can not handled: %s\\n" +
						"Only a = [%s, %s] are supported.",
						jreq.a(), A.gpas, A.insert );
			write(resp, rsp);
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			if (StarFlags.gpatier)
				e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}

	}

	private AnsonMsg<GPAResp> ins(GPAReq req, IUser usr) throws SemanticException, TransException, SQLException {
		Insert ins = st.insert("gpas", usr);
		ins = req.insCols(ins)
				.insVals(ins);

		SemanticObject rs = (SemanticObject) ins
				.ins(st.instancontxt(Connects.uri2conn(req.uri()), usr));
		return null;
	}

	protected AnsonMsg<GPAResp> gpas(GPAReq req, IUser usr) throws SemanticException, TransException, SQLException {
		String conn = Connects.uri2conn(req.uri());
		dbtype dt = Connects.driverType(conn);
		ISemantext stx = st.instancontxt(conn, usr);

		// 1. load kids average
		// FIXME n_mykids
		AnResultset kids = ((AnResultset) st.select("a_users", "k")
			.l("gpas", "g", "k.userId = g.userId")
			.col(Funcall.average(null, Funcall.sqlIfnull(stx , "g.gpa", "0")), "avg")
			.col(Funcall.max("k.userName"), "userName")
			.col("k.userId", "kid")
			.groupby("k.userId")
			.orderby("k.userId")
			.rs(stx)
			.rs(0)).beforeFirst();

		// 2. load gpa history
		AnResultset gpas = ((AnResultset) st.select("gpas", "g")
			.col("gpa").col("userId").col(Funcall.toDate(dt, "gday"), "gday")
			.orderby("gday")
			.orderby("userId")
			.rs(stx)
			.rs(0)).beforeFirst();

		// 3. expand matrix
		// 3.1 setup colnames - some kids' gpa can be missing for some days
		HashMap<String, Integer> colx = new HashMap<String, Integer>();
		colx.put("gday", 1);
		int x = 2;
		while (kids.next()) {
			String c = kids.getString("kid");
			colx.put(c, x);
			x++;
		}
		kids.beforeFirst();
		
//		ArrayList<String[]> rows = new ArrayList<String[]>();
		ArrayList<ArrayList<Object>> rows = new ArrayList<ArrayList<Object>>();
		ArrayList<Object> row = null;
		String kday = null;
		while (gpas.next()) {
			if (!gpas.getString("gday").equals(kday)) {
				// save results
				rows.add(row);

				// start a new row
				row = new ArrayList<Object>(kids.getRowCount() + 1);
				kday = gpas.getString("gday");
				row.set(0, kday);

				// loop again
				kids.beforeFirst();
			}

			int ix = colx.get(gpas.getString("userId"));
			row.set(ix, gpas.getString("gpa"));

			if (!gpas.next()) break;
		}
		
		AnResultset matrx = new AnResultset(colx, rows);
		
		return ok(new GPAResp(kids, colx, matrx));
	}
}
