package io.oz.sandbox.sheet;

import io.odysz.semantic.jprotocol.AnsonResp;

public class SpreadsheetResp extends AnsonResp {

	MyCurriculum rec;

	public SpreadsheetResp(MyCurriculum rec) {
		this.rec = rec;
	}

}
