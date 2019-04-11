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
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.semantic.jserv.helper.ServletAdapter;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.ISessionVerifier;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;

@WebServlet(description = "querying db via Semantic.DA", urlPatterns = { "/u.serv" })
public class SUpdate extends HttpServlet {
	private static DATranscxt st;
	static JHelper<UpdateReq> jreqHelper;
	protected static ISessionVerifier verifier;

	static {
		st = JSingleton.defltScxt;
		jreqHelper = new JHelper<UpdateReq>();
		verifier = JSingleton.getSessionVerifier();
	}

	private static final long serialVersionUID = 1L;

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

			SemanticObject res = update(msg.body().get(0), usr);
			
			ServletAdapter.write(resp, res);

		} catch (SQLException e) {
			e.printStackTrace();
		} catch (TransException e) {
			e.printStackTrace();
		} catch (SsException e) {
			e.printStackTrace();
		} catch (ReflectiveOperationException e) {
			e.printStackTrace();
		}
	}

	private SemanticObject update(UpdateReq msg, IUser usr) throws SQLException, TransException {
		// ArrayList<String> sqls = new ArrayList<String>();
		Update upd = st.update(msg.mtabl, usr);

		// if (ServFlags.update) Utils.logi(sqls);
		SemanticObject res = (SemanticObject) upd
				// .commit(sqls)
				.u(st.instancontxt(usr));
		return res;
	}
}
