package io.odysz.jquiz;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.jquiz.protocol.Quizport;
import io.odysz.jquiz.utils.JquizFlags;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.user.UserReq;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Delete;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Update;
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

	private static IUser jsonRobot;

	public Quiz() {
		super(null);
		p = Quizport.quiz;

		if (jsonRobot == null)
			jsonRobot = new JRobot();
	}

	@Override
	protected void onGet(AnsonMsg<UserReq> jmsg, HttpServletResponse resp)
			throws IOException, ServletException {
		if (JquizFlags.quiz)
			Utils.logi("---------- jserv-quiz/quiz.serv GET ----------");
		try {
			// serving json
			UserReq jreq = jmsg.body(0);
			write(resp, jsonQuiz((String) jreq.get(QuizProtocol.quizId)));
		} catch (TransException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			resp.flushBuffer();
		}
	}
	
	/**For empty get header, feed a json if possible. 
	 * @see io.odysz.semantic.jserv.ServPort#onGetAnsonException(io.odysz.anson.x.AnsonException, javax.servlet.http.HttpServletResponse, java.util.Map)
	 */
	protected void onGetAnsonException(AnsonException e,
			HttpServletResponse resp, Map<String, String[]> map) throws IOException, ServletException {
		String[] qid = map != null ? map.get("qid") : null;
		if (qid == null || qid.length <= 0) {
			resp.getWriter().write("{\"error\": ");
			resp.getWriter().write(e.getMessage());
			resp.getWriter().write("\"}");
		}
		else
			try {
				write(resp, jsonQuiz(qid[0]));
			} catch (TransException | SQLException e1) {
				e1.printStackTrace();
				throw new ServletException(e1.getMessage());
			}
	}

	public static AnsonMsg<JsonQuiz> jsonQuiz(String qzid)
			throws TransException, SQLException, IOException {
		// FIXME buffer is needed here (just for fun, how about implement that LRU buffer?)
		// FIXME conn = null is a bug
		ISemantext smtxt = st.instancontxt(null, jsonRobot);
		SemanticObject so = st
			.select("quizzes", "q")
			.col("qid").col("title").col("extra", "url")
			.where_("=", "qid", LangExt.isEmpty(qzid) ? "" : qzid)
			.rs(smtxt);
		SemanticObject ques = st
			.select("questions", "q")
			.col("qid")
			.col("answers").col("answer", "correct")
			.col("0", "number").col("qtype")
			.col("question", "prompt")
			.col("''", "image")  // TODO
			.where_("=", "quizId", qzid)
			.rs(smtxt);
		
		return new AnsonMsg<JsonQuiz>().body(new JsonQuiz()
				.quiz((AnResultset)so.rs(0))
				.questions((AnResultset)ques.rs(0)));
	}

	@Override
	protected void onPost(AnsonMsg<UserReq> jmsg, HttpServletResponse resp)
			throws IOException {
		if (JquizFlags.quiz)
			Utils.logi("========== jserv-quiz/quiz.serv POST ==========");

		resp.setCharacterEncoding("UTF-8");
		try {
			UserReq jreq = jmsg.body(0);
			String a = jreq.a();
			AnsonMsg<? extends AnsonResp> rsp = null;
			if ("poll".equals(a)) {
				rsp = onPoll(jreq);
			}
			else {
				IUser usr = JSingleton.getSessionVerifier().verify(jmsg.header());

				if ("quiz".equals(a))
					rsp = quiz(jmsg.body(0), usr);
				else if ("list".equals(a))
					rsp = quizzes(jmsg, usr);
				else if ("insert".equals(a))
					rsp = insert(jmsg.body(0), usr);
				else if ("update".equals(a))
					rsp = update(jmsg.body(0), usr);
				else
					throw new SemanticException("request.body.a can not handled: %s\n" +
							"Only a = poll | quiz | list | insert | update are supported.", jreq.a());
			}
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

	/**Save a poll. Each answer to a question has a pssid - used for filtering and analysing?
	 * @param props
	 * @return
	 * @throws TransException
	 * @throws SQLException
	 */
	protected AnsonMsg<? extends AnsonResp> onPoll(UserReq props) throws TransException, SQLException {
		@SuppressWarnings("unchecked")
		ArrayList<String[]> polls = (ArrayList<String[]>) props.get(QuizProtocol.poll);
		String qzid = (String) props.get(QuizProtocol.quizId);
		String usrinfo = (String) props.get(QuizProtocol.pollUser);


		ISemantext smtxt = st.instancontxt(Connects.uri2conn(props.uri()), jsonRobot);
		Insert inspoll = st.insert("polls", jsonRobot)
			.nv("userInfo", usrinfo)
			.nv("quizId", qzid)
			;

		if (polls != null) {
			for (String[] qid_ans : polls) {
				Insert ins = st.insert("polldetails", jsonRobot);
				ins.nv("questid", qid_ans[0]);
				ins.nv("results", qid_ans[1]);
				inspoll.post(ins);
			}
		}
		
		inspoll.ins(smtxt);

		return ok(new QuizResp()
				.quizId(qzid)
				.msg("poll saved"));
	}
	

	@SuppressWarnings("unchecked")
	protected AnsonMsg<? extends AnsonResp> insert(UserReq body, IUser usr) throws TransException, SQLException {
		List<String[][]> ques = (List<String[][]>) body.data(QuizProtocol.questions);
		String info = (String) body.data(QuizProtocol.quizinfo);
		String titl = (String) body.data(QuizProtocol.qtitle);
		String qown = (String) body.data(QuizProtocol.qowner);
		String day0 = (String) body.data(QuizProtocol.dcreate);


		ISemantext smtxt = st.instancontxt(Connects.uri2conn(body.uri()), usr);
		Insert insquz = st.insert("quizzes", usr)
			.nv("quizinfo", info)
			.nv("title", titl)
			.nv("qowner", qown)
			.nv("dcreate", day0)
			;

		int total = 0;
		if (ques != null) {
			for (String[][] q : ques) {
				Insert ins = st.insert("questions", usr);
				for (String[] nv : q)
					ins.nv(nv[0], nv[1]);
				insquz.post(ins);
				total++;
			}
		}
		
		insquz.ins(smtxt);
		
		return ok(new QuizResp()
				.quizId((String)smtxt.resulvedVal("quizzes", "qid", -1))
				.title(titl)
				.questions(total)
				.msg("inserted"));
	}

	@SuppressWarnings("unchecked")
	private AnsonMsg<? extends AnsonResp> update(UserReq body, IUser usr) throws TransException, SQLException {
		List<String[][]> ques = (List<String[][]>) body.data(QuizProtocol.questions);
		String info = (String) body.data(QuizProtocol.quizinfo);
		String titl = (String) body.data(QuizProtocol.qtitle);
		String qzid =  (String) body.data(QuizProtocol.quizId);

		Delete del = st.delete("questions", usr)
				.where_("=", "quizId", qzid);

		Update upd = st.update("quizzes", usr)
			.nv("quizinfo", info)
			.nv("title", titl)
			.where_("=", "qid", qzid)
			.post(del);

		int total = 0;
		if (ques != null) {
			for (String[][] q : ques) {
				Insert ins = st.insert("questions", usr);
				// FIXME can the semantics-DA support this auto update?
				// see DASemantextTest#testMultiChildInst()
				ins.nv("quizId", qzid);
				for (String[] nv : q)
					if (!"quizId".equals(nv[0]))
						ins.nv(nv[0], nv[1]);
				del.post(ins);
				total++;
			}
		}
	
		ISemantext smtxt = st.instancontxt(Connects.uri2conn(body.uri()), usr);
		upd.u(smtxt);
		
		return ok(new QuizResp()
				.quizId(qzid)
				.questions(total)
				.msg("updated"));
	}

	protected AnsonMsg<AnsonResp> quizzes(AnsonMsg<UserReq> jmsg, IUser usr) 
			throws TransException, SQLException {
		UserReq req = jmsg.body(0);
		
		SemanticObject rs = st.select("quizzes", "q")
				.l("s_domain", "d", "q.subject = d.did)")
				// TODO conditions
				// TODO conditions
				.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr));

		return ok(new QuizResp(rs).msg("list loaded"));
	}

	private AnsonMsg<? extends AnsonResp> quiz(UserReq body, IUser usr) throws TransException, SQLException {
		String qzid =  (String) body.data(QuizProtocol.quizId);
		ISemantext smtxt = st.instancontxt(Connects.uri2conn(body.uri()), usr);
		SemanticObject so = st
			.select("quizzes", "q")
			.where_("=", "qid", LangExt.isEmpty(qzid) ? "" : qzid)
			.rs(smtxt);
		SemanticObject ques = st
			.select("questions", "q")
			.where_("=", "quizId", qzid)
			.rs(smtxt);
		
		so.put(QuizProtocol.questions, ques.rs(0));

		return ok(new QuizResp(so).msg("quiz loaded"));
	}
}
