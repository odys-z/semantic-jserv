package io.oz.spreadsheet;

import java.util.ArrayList;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jprotocol.AnsonResp;

public class SpreadsheetResp extends AnsonResp {

	ISheetRec rec;

	public SpreadsheetResp(ISheetRec rec) {
		this.rec = rec;
	}

	public SpreadsheetResp(AnResultset sheet) {
		this.rs = new ArrayList<AnResultset>() { {add(sheet);} };
	}
}
