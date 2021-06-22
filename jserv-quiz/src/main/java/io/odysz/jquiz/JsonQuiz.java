package io.odysz.jquiz;

import java.sql.SQLException;
import java.util.ArrayList;

import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jprotocol.AnsonResp;

/**Data type of plain-quiz' json object.
 * <pre>{
 "questions": [ { 
   "answers": [""],
   "correct": { "index": 0 },
   "number": 6,
   "prompt": "Which one you like most?",
   "image": "imgs/qr.jpg"
   }, ... ]
   "title": "How what when ... ?",
   "url": "url from DB.quizzes.extra"
 }</pre>
 * 
 * @author odys-z@github.com
 */
public class JsonQuiz extends AnsonResp {

	protected String title;
	protected String url;
	protected String quizId;
	// protected ArrayList<ArrayList<Object[]>> questions;
	protected ArrayList<JsonQuestion> questions;

	public String quizId() { return quizId; }

	public JsonQuiz quiz(AnResultset quiz) throws SQLException {
		if (quiz.total() > 1) {
			Utils.warn("JsonQuiz#quiz(): can only convert 1 quiz:");
			quiz.printSomeData(true, 2);
		}

		if (quiz.total() >= 1) {
			quiz.beforeFirst().next();
			quizId = quiz.getString("qid");
			title = quiz.getString("title");
			url = quiz.getString("url");
		}
		return this;
	}
	
	/**Convert questions data - can we use a better json format?
	 * @param questions
	 * @return
	 * @throws SQLException
	 */
	public JsonQuiz questions(AnResultset questions) throws SQLException {
		if (questions != null) {
			this.questions = new ArrayList<JsonQuestion>(questions.total());
			questions.beforeFirst();
			while (questions.next()) {
				/*
				ArrayList<Object[]> row = new ArrayList<Object[]>(4);
				String answers = questions.getString("answers");
				// String[] answerss = answers.split("\n");
				row.add(new Object[] {"qid", questions.getString("qid")});
				row.add(new Object[] {"answers", answers});
				row.add(new String[] {"correct", questions.getString("correct")});
				row.add(new String[] {"number", questions.getString("number")});
				row.add(new String[] {"prompt", questions.getString("prompt")});
				row.add(new String[] {"image", questions.getString("image")});
				this.questions.add(row);
				*/
				JsonQuestion q = new JsonQuestion(questions.getString("qid"))
						.answers(questions.getString("answers"))
						.correct(questions.getString("correct"))
						.number(questions.getString("number"))
						.prompt(questions.getString("prompt"))
						.image(questions.getString("image"))
						;
				this.questions.add(q);
			}
		}
		return this;
	}

	public JsonQuestion questions(int row) {
		return questions != null && questions.size() > row - 1 ?
				questions.get(row) : null;
	}

}
