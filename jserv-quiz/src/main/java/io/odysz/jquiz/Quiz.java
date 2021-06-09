package io.odysz.jquiz;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.common.Utils;
import io.odysz.jquiz.protocol.Quizport;
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
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.x.TransException;

/**Service Port for quiz data.
 * 
 * @author odys-z@github.com
 *
 */
@WebServlet(description = "jserv.sample example/quiz.serv", urlPatterns = { "/quiz.serv" })
public class Quiz extends ServPort<UserReq> {
	private static final long serialVersionUID = 1L;

	static DATranscxt st;

	static {
		try {
			st = new DATranscxt("quiz");
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}


	public Quiz() {
		super(null);
		p = Quizport.quiz;
	}

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
			else if ("insert".equals(jreq.a()))
				rsp = insert(jmsg.body(0), usr);
			else if ("update".equals(jreq.a()))
				rsp = update(jmsg.body(0), usr);
			else
				throw new SemanticException("request.body.a can not handled: %s\n" +
						"Only a = quiz | list | insert | update are supported.", jreq.a());

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

	private AnsonMsg<? extends AnsonResp> update(UserReq body, IUser usr) {
		return null;
	}

	@SuppressWarnings("unchecked")
	protected AnsonMsg<? extends AnsonResp> insert(UserReq body, IUser usr) throws TransException, SQLException {
		List<String[][]> ques = (List<String[][]>) body.data(QuizProtocol.questions);
		String info = (String) body.data(QuizProtocol.quizinfo);
		String titl = (String) body.data(QuizProtocol.qtitle);
		String qown = (String) body.data(QuizProtocol.qowner);
		String day0 = (String) body.data(QuizProtocol.dcreate);

		int total = 0;
		Insert ins = st.insert("questions", usr);
		for (String[][] it : ques) {
			for (String[] nv : it)
				ins.nv(nv[0], nv[1]);
			total++;
		}

		ISemantext smtxt = st.instancontxt(body.conn(), usr);
		// ArrayList<String> sqls = new ArrayList<String>();
		st.insert("quizzes", usr)
			.nv("quizinfo", info)
			.nv("title", titl)
			.nv("qowner", qown)
			.nv("dcreate", day0)
			.post(ins)
			.ins(smtxt);
		
		return ok(new AnsonResp(String.valueOf(total)));
	}


//	static DATranscxt getContext(String connId) throws SemanticException {
//		// TODO & FIXME you can create a context buffer here
//		try {
//			return new DATranscxt(connId);
//		} catch (SQLException | SAXException | IOException e) {
//			throw new SemanticException("Can't create DATranscxt. Caused by: " + e.getMessage());
//		}
//	}

	protected AnsonMsg<AnsonResp> quizzes(AnsonMsg<UserReq> jmsg, IUser usr) 
			throws TransException, SQLException {
		UserReq req = jmsg.body(0);
		// DATranscxt st = getContext(req.conn());
		
		SemanticObject rs = st.select("quizzes", "q")
				.l("s_domain", "d", "q.subject = d.did)")
				.rs(st.instancontxt(req.conn(), usr));

		return ok((AnResultset)rs.rs(0));
	}

}
