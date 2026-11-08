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
	TreeModify.Rejected rejected;
	public TreeModify.Rejected rejected() { return rejected; }
	public AnDatasetResp rejected(TreeModify.Rejected why) { rejected = why; return this; }

	/**
	 * Tree modification, insert: the new node's id.
	 * @since 1.5.20
	 */
	String newId;
	public String newId() { return newId; }
	public AnDatasetResp newId(String nid) { newId = nid; return this; }
}
