package io.odysz.jquiz;

import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantics.SemanticObject;

public class QuizResp extends AnsonResp {

	protected SemanticObject data;

	public QuizResp() {
		data = new SemanticObject();
	}

	public QuizResp(SemanticObject datium) {
		data = datium;
	}

	public QuizResp msg(String string) {
		m = string;
		return this;
	}
	
	public QuizResp quizId(String qid) {
		data.put(QuizProtocol.quizId, qid);
		return this;
	}
	
	public QuizResp title(String title) {
		data.put(QuizProtocol.qtitle, title);
		return this;
	}
	
	public QuizResp questions(int ques) {
		data.put(QuizProtocol.questions, ques);
		return this;
	}

}
