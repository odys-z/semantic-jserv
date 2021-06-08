package io.odysz.jquiz;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.common.Utils;
import io.odysz.jsample.protocol.Quizport;
import io.odysz.jquiz.utils.JquizFlags;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.semantic.jserv.user.UserReq;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

/**Service Port for quiz data.
 * 
 * @author odys-z@github.com
 *
 */
@WebServlet(description = "jserv.sample example/quiz.serv", urlPatterns = { "/quiz.serv" })
public class Quiz extends ServPort<UserReq> {
	public Quiz() {
		super(null);
		p = Quizport.quiz;
	}

	private static final long serialVersionUID = 1L;

	@Override
	protected void onGet(AnsonMsg<UserReq> jmsg, HttpServletResponse resp)
			throws IOException {
		if (JquizFlags.quiz)
			Utils.logi("---------- jserv-quiz/quiz.serv GET ----------");
		try {
			// UserReq jreq = jmsg.body(0);
			resp.getWriter().write(Html.ok("Please visit POST."));
		} finally {
			resp.flushBuffer();
		}
	}

	@Override
	protected void onPost(AnsonMsg<UserReq> jmsg, HttpServletResponse resp)
			throws IOException {
		if (JquizFlags.quiz)
			Utils.logi("========== jserv-quiz/quiz.serv POST ==========");

		resp.setCharacterEncoding("UTF-8");
		try {
			IUser usr = JSingleton.getSessionVerifier().verify(jmsg.header());

			UserReq jreq = jmsg.body(0);

			AnsonMsg<? extends AnsonResp> rsp = null;
			if ("quiz".equals(jreq.a()))
				throw new NullPointerException("todo ...");
			else if ("list".equals(jreq.a()))
				rsp = quizzes(jmsg, usr);
			else
				throw new SemanticException("request.body.a can not handled: %s\n" +
						"Only a = quiz | list are supported.", jreq.a());

			write(resp, rsp);
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			if (JquizFlags.user)
				e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	private String[] serialsGroup = new String[] {"age", "dim1", "dim2"};

	@SuppressWarnings("serial")
	protected static ArrayList<String[]> serialsOrder = new ArrayList<String[]>() {
		{add(new String[] {"parent"});};
		{add(new String[] {"did"});}
	};

	static DATranscxt getContext(String connId) throws SemanticException {
		// TODO & FIXME you can create a context buffer here
		try {
			return new DATranscxt(connId);
		} catch (SQLException | SAXException | IOException e) {
			throw new SemanticException("Can't create DATranscxt. Caused by: " + e.getMessage());
		}
	}

	protected AnsonMsg<AnsonResp> quizzes(AnsonMsg<UserReq> jmsg, IUser usr) 
			throws TransException, SQLException {
		UserReq req = jmsg.body(0);
		DATranscxt st = getContext(req.conn());
		
		SemanticObject rs = st.select("quizzes", "q")
				.l("s_domain", "d", "q.subject = d.did)")
				.rs(st.instancontxt(req.conn(), usr));

		return ok((AnResultset)rs.rs(0));
	}

}
