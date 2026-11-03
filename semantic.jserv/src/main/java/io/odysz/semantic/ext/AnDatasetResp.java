package io.odysz.semantic.ext;

import java.util.ArrayList;
import java.util.List;

import io.odysz.anson.Anson;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonResp;

public class AnDatasetResp extends AnsonResp {

	private List<?> forest;

	public AnDatasetResp(AnsonMsg<AnsonResp> parent, ArrayList<Anson> forest) {
		super(parent);
	}

	public AnDatasetResp(AnsonMsg<? extends AnsonResp> parent) {
		super(parent);
	}

	public AnDatasetResp() {
		super("");
	}

	public AnDatasetResp forest(List<?> lst) {
		forest = lst;
		return this;
	}

	public List<?> forest() { return forest; }

	/**
	 * Tree modification: the root of the reloaded subtree in {@link #forest}, null for the whole forest.
	 * @since 1.5.20
	 */
	String root;
	public String root() { return root; }
	public AnDatasetResp root(String r) { root = r; return this; }

	/**
	 * Tree modification: null if accepted, otherwise the reason, e.g. "cycle", "no-op".
	 * @since 1.5.20
	 */
	String rejected;
	public String rejected() { return rejected; }
	public AnDatasetResp rejected(String why) { rejected = why; return this; }
}
