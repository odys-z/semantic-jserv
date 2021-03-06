package io.odysz.jquiz;

import io.odysz.anson.Anson;
import io.odysz.common.LangExt;

public class JsonQuestion extends Anson {

	public static class Correct extends Anson {
		int index = 0;
		Correct(String idx) {
			index = LangExt.isEmpty(idx) ? 0 : Integer.valueOf(idx);
		}
	}

	protected String qid;
	protected String answers;
	protected Correct correct;
	protected String prompt;
	protected String number;
	protected String image;
	protected String qtype;

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

	public JsonQuestion qtype(String v) {
		qtype = v;
		return this;
	}

}
