package io.oz.sandbox.sheet;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jprotocol.AnsonResp;

public class SpreadsheetResp extends AnsonResp {

	MyCurriculum rec;

	AnResultset sheet;

	public SpreadsheetResp(MyCurriculum rec) {
		this.rec = rec;
	}

	public SpreadsheetResp(AnResultset sheet) {
		this.sheet = sheet;
	}
}
