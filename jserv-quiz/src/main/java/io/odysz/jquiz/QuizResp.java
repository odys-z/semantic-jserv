package io.odysz.jquiz;

import io.odysz.semantic.jprotocol.AnsonResp;

public class QuizResp extends AnsonResp {

	public QuizResp() { }

	public QuizResp msg(String string) {
		m = string;
		return this;
	}

}
