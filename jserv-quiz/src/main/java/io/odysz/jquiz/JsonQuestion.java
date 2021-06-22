package io.odysz.jquiz;

import io.odysz.anson.Anson;

public class JsonQuestion extends Anson {

	public static class Correct extends Anson {
		int index = 0;
		Correct(String idx) {
			index = Integer.valueOf(idx);
		}
	}

	protected String qid;
	protected String answers;
	protected Correct correct;
	protected String prompt;
	protected String number;
	protected String image;

	public JsonQuestion(String id) {
		qid = id;
	}

	public JsonQuestion answers(String v) {
		answers = v;
		return this;
	}

	public JsonQuestion correct(String v) {
		correct = new Correct(v);
		return this;
	}

	public JsonQuestion prompt(String v) {
		prompt = v;
		return this;
	}

	public JsonQuestion number(String v) {
		number = v;
		return this;
	}

	public JsonQuestion image(String v) {
		image = v;
		return this;
	}

}
