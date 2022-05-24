package io.oz.sandbox.sheet;

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
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;
import io.oz.sandbox.SandRobot;
import io.oz.sandbox.protocol.Sandport;
import io.oz.sandbox.sheet.SpreadsheetReq.A;

@WebServlet(description = "Semantic sessionless: spreadsheet", urlPatterns = { "/sheet.less" })
public class Spreadsheet extends ServPort<SpreadsheetReq> {

	private static final long serialVersionUID = 1L;

	static DATranscxt st;

	static IUser robot;

	static {
		try {
			st = new DATranscxt(null);
			robot = new SandRobot("Spread Robot");
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	public Spreadsheet() {
		super(Sandport.workbook);
	}

	@Override
	protected void onGet(AnsonMsg<SpreadsheetReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		
	}

	@Override
	protected void onPost(AnsonMsg<SpreadsheetReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		
		SpreadsheetReq jreq = msg.body(0);
		
		try {
			SpreadsheetResp rsp = null;
			if (A.insert.equals(jreq.a()))
				rsp = insert(jreq);
			else if (A.update.equals(jreq.a()))
				rsp = update(jreq);
			else if (A.records.equals(jreq.a()))
				rsp = records(jreq);
			else
				throw new SemanticException("Request (request.body.a = %s) can not be handled", jreq.a());

			write(resp, ok(rsp));
		} catch (TransException | SQLException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	static SpreadsheetResp records(SpreadsheetReq jreq) throws TransException, SQLException {
		String conn = Connects.uri2conn(jreq.uri());
		Query select = st.select(MyCurriculum.tabl, "c");
		
		if (jreq.page != null && jreq.page.condts != null)
			for (String[] cond : jreq.page.condts) {
				if (cond != null && "currName".equals(cond[0]))
					select.whereLike(cond[0], cond[1]);
				else
					select.whereEq(cond[0], cond[1]);
			}
		
		AnResultset rs = (AnResultset) st.select(MyCurriculum.tabl, "c")
		  .rs(st.instancontxt(conn, robot))
		  .rs(0);
		
		return new SpreadsheetResp(rs);
	}

	static SpreadsheetResp insert(SpreadsheetReq jreq) throws TransException, SQLException {

		Insert ins = st.insert(MyCurriculum.tabl, robot);
		
		jreq.insertRec(ins);
		
		String conn = Connects.uri2conn(jreq.uri());
		SemanticObject res = (SemanticObject) ins.ins(st.instancontxt(conn, robot));
		String pid = ((SemanticObject) ((SemanticObject) res.get("resulved"))
				.get(MyCurriculum.tabl))
				.getString("cid");
		
		jreq.rec.cid = pid;

		return new SpreadsheetResp(jreq.rec);
	}

	static SpreadsheetResp update(SpreadsheetReq jreq) throws TransException, SQLException {

		Update upd = st.update(MyCurriculum.tabl, robot)
				.whereEq("cid", jreq.rec.cid);
	
		if (jreq.rec.cate != null)
			upd.nv("cate", jreq.rec.cate);
		if (jreq.rec.subject != null)
			upd.nv("subject", jreq.rec.subject);
		if (jreq.rec.module != null)
			upd.nv("module", jreq.rec.module);
		if (jreq.rec.clevel != null)
			upd.nv("clevel", jreq.rec.clevel);
		if (jreq.rec.currName != null)
			upd.nv("currName", jreq.rec.currName);
		if (jreq.rec.sort != null)
			upd.nv("sort", jreq.rec.sort);
	
		String conn = Connects.uri2conn(jreq.uri());
		upd.u(st.instancontxt(conn, robot));

		return new SpreadsheetResp(jreq.rec);
	}

}
