package io.oz.ever.conn.n.gpa;

import java.util.HashMap;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jprotocol.AnsonResp;

public class GPAResp extends AnsonResp {

	AnResultset kids;
	AnResultset gpas;
	HashMap<String, Integer> cols;

	public GPAResp(AnResultset kids, HashMap<String, Integer> cols, AnResultset gpas) {
		this.kids = kids;
		this.gpas = gpas;
		this.cols = cols;
	}

}
