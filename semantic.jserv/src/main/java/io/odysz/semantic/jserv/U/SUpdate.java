package io.odysz.semantic.jserv.U;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.common.Utils;
import io.odysz.semantic.DA.DATranscxt;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.semantic.jsession.ISessionVerifier;
import io.odysz.semantics.SemanticObject;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;

@WebServlet(description = "querying db via Semantic.DA", urlPatterns = { "/query.serv" })
public class SUpdate extends HttpServlet {
	private static DATranscxt st;
	static JHelper<UpdateReq> jreqHelper;
	static JHelper<UpdateResp> jrespHelper;
	private static ISessionVerifier verifier;

	static {
		st = JSingleton.st;
		jreqHelper = new JHelper<UpdateReq>();
		jrespHelper = new JHelper<UpdateResp>();
		verifier = JSingleton.getSessionVerifier();
	}

	private static final long serialVersionUID = 1L;

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		
		try {
			InputStream in = req.getInputStream();
			List<UpdateReq> msgs = jreqHelper.readJsonStream(in, UpdateReq.class);
			in.close();
			
			verifier.verify(msgs);
			
			UpdateReq msg = msgs.get(0);
//			// TODO let's use stream mode
			HashMap<String,SemanticObject> res = update(msg);
			
			resp.setCharacterEncoding("UTF-8");
			resp.getWriter().write(Html.map(res));
			resp.flushBuffer();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (TransException e) {
			e.printStackTrace();
		}
	}

	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		try {
			InputStream in = req.getInputStream();
			List<UpdateReq> msgs = jreqHelper.readJsonStream(in, UpdateReq.class);
			in.close();
			
			UpdateReq msg = msgs.get(0);
			HashMap<String, SemanticObject> res = update(msg);
			
			resp.setCharacterEncoding("UTF-8");
//			resp.getWriter().write(Html.list(res));
			OutputStream os = resp.getOutputStream();
			jrespHelper.writeJsonStream(os, new UpdateResp(res));
			resp.flushBuffer();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (TransException e) {
			e.printStackTrace();
		}
	}

	private HashMap<String,SemanticObject> update(UpdateReq msg) throws SQLException, TransException {
		ArrayList<String> sqls = new ArrayList<String>();
		Update upd = st.update(msg.mtabl);

		if (ServFlags.update)
			Utils.logi(sqls);
		HashMap<String, SemanticObject> res = upd.commit(sqls);
		return res;
	}
}
