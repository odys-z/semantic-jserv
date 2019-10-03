package io.odysz.semantic.ext;

import java.util.ArrayList;
import java.util.List;

import io.odysz.anson.Anson;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonResp;

public class AnDatasetResp extends AnsonResp {

	private List<? extends Anson> forest;

	public AnDatasetResp(AnsonMsg<AnsonResp> parent, ArrayList<Anson> forest) {
		super(parent);
	}

	public AnDatasetResp(AnsonMsg<? extends AnsonResp> parent) {
		super(parent);
	}

	public AnDatasetResp() {
		super("");
	}

	public AnDatasetResp forest(List<? extends Anson> lst) {
		forest = lst;
		return this;
	}

	public List<? extends Anson> forest() { return forest; }
}
