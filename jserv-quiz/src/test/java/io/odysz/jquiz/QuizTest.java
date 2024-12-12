package io.odysz.jquiz;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.transact.x.TransException;

/**SQL for test:
 * <pre> insert into quizzes (qid, oper, title, optime, tags, quizinfo, qowner, dcreate, subject, extra)
 values ('--1', 'admin', 'test q1', '1911-10-10', 'hashtag', 'see io.odysz.jquiz.QuizTest', 'admin', '1911-10-10', 'test', null);

 insert into quizzes (qid, oper, title, optime, tags, quizinfo, qowner, dcreate, subject, extra)
 values ('--2', 'admin', 'test q2', '1911-10-10', 'hashtag', 'see io.odysz.jquiz.QuizTest', 'admin', '1911-10-10', 'test', null);
 insert into questions (qid, quizId, question, answers, qtype, answer, hints)
 values ('--2.1', '--2', 'What?', 'yes\nno', '1', '0', 'along correct text');
   </pre>
 * 
 * @author odys-z@github.com */
class QuizTest {

	@BeforeAll
	static void initSqlite() throws Exception {
		File file = new File("WEB-INF");
		String path = file.getAbsolutePath();
		Utils.logi(path);
		Connects.init(path);

		@SuppressWarnings("unused")
		Quiz quiz = new Quiz();						// initialize jrobot
		Quiz.st = new DATranscxt("quiz-test");	// see comments for sql scripts
	}

	@Test
	void testJson() throws TransException, SQLException, IOException, AnsonException {
		ByteArrayOutputStream sb = new ByteArrayOutputStream();
		AnsonMsg<JsonQuiz> msg = Quiz.jsonQuiz("???");
		msg.toBlock(sb);
		assertEquals(
			"{\"type\": \"io.odysz.semantic.jprotocol.AnsonMsg\", " +
			 "\"code\": null, \"opts\": null, \"port\": null, \"header\": null, " +
			 "\"body\": [{\"type\": \"io.odysz.jquiz.JsonQuiz\", \"rs\": null, " +
			             "\"parent\": \"io.odysz.semantic.jprotocol.AnsonMsg\", " + 
			             "\"a\": null, \"conn\": null, \"quizId\": null, " + 
			             "\"questions\": [], \"title\": null, \"m\": null, \"map\": null, \"url\": null}], " + 
			 "\"version\": \"1.0\"", // \"seq\": 475} 
			sb.toString().substring(0, 342));
		
		
		msg = Quiz.jsonQuiz("--1");
		assertEquals("test q1", msg.body(0).title);

		msg = Quiz.jsonQuiz("--2");
		assertEquals("test q2", msg.body(0).title);
		assertEquals("--2.1", msg.body(0).questions(0).qid);
		assertEquals("What?", msg.body(0).questions(0).prompt);
		assertEquals(0, msg.body(0).questions(0).correct.index);
	}

}
