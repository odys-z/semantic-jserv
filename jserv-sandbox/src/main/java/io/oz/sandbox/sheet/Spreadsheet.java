package io.oz.sandbox.sheet;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;
import io.oz.sandbox.SandRobot;
import io.oz.sandbox.protocol.Sandport;
import io.oz.sandbox.sheet.SpreadsheetReq.A;

@WebServlet(description = "Semantic sessionless: spreadsheet", urlPatterns = { "/sheet.less" })
public class Spreadsheet extends ServPort<SpreadsheetReq> {

	private static final long serialVersionUID = 1L;

	public static final String tabl = "b_curriculums";

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


	public Spreadsheet(IPort port) {
		super(Sandport.sheet);
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
			if (A.update == jreq.a())
				rsp = insert(jreq);
			else if (A.update == jreq.a())
				rsp = update(jreq);
			else
				throw new SemanticException("Request (request.body.a = %s) can not be handled", jreq.a());

			write(resp, ok(rsp));
		} catch (TransException | SQLException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	static SpreadsheetResp insert(SpreadsheetReq jreq) throws TransException, SQLException {

		Insert ins = st.insert(tabl, robot)
				.nv("cate", jreq.rec.cate)
				.nv("clevle", jreq.rec.level)
				.nv("subject", jreq.rec.subject)
				.nv("module", jreq.rec.module);
		
		String conn = Connects.uri2conn(jreq.uri());
		SemanticObject res = (SemanticObject) ins.ins(st.instancontxt(conn, robot));
		String pid = ((SemanticObject) ((SemanticObject) res.get("resulved"))
				.get(tabl))
				.getString("cid");
		
		jreq.rec.cid = pid;

		return new SpreadsheetResp(jreq.rec);
	}

	static SpreadsheetResp update(SpreadsheetReq jreq) throws TransException, SQLException {

		Update upd = st.update(tabl, robot)
				.whereEq("cid", jreq.rec.cid);
	
		if (jreq.rec.cate != null)
			upd.nv("cate", jreq.rec.cate);
		if (jreq.rec.subject != null)
			upd.nv("subject", jreq.rec.subject);
		if (jreq.rec.module != null)
			upd.nv("module", jreq.rec.module);
		if (jreq.rec.level != null)
			upd.nv("clevel", jreq.rec.level);
	
		String conn = Connects.uri2conn(jreq.uri());
		upd.u(st.instancontxt(conn, robot));

		return new SpreadsheetResp(jreq.rec);
	}

}
