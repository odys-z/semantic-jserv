package io.odysz.semantic.ext;

import io.odysz.semantic.DA.DatasetCfg.TreeSemantics;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.R.AnQueryReq;

public class AnDatasetReq extends AnQueryReq {

	String sk;
	public String[] sqlArgs;
	public String rootId;
	/**String array of tree semantics from client */
	protected String smtcss;
	/**{@link TreeSemantics} of tree from {@link #smtcss} or set with {@link #treeSemtcs} ({@link TreeSemantics}) */
	protected TreeSemantics stcs;

	String root;
	public String root() { return root; }

	public AnDatasetReq() { super(null, null);}

	public AnDatasetReq root(String rootId) {
		this.root = rootId;
		return this;
	}

	public AnDatasetReq(AnsonMsg<? extends AnsonBody> parent, String conn) {
		super(parent, conn);
		a = "ds";
	}

	public String sk() { return sk; }

	public static AnDatasetReq formatReq(String conn, AnsonMsg<AnDatasetReq> parent, String sk) {
		AnDatasetReq bdItem = new AnDatasetReq(parent, conn);
		bdItem.sk = sk;
		return bdItem;
	}

	/**
	 * @return parsed semantics
	 */
	public TreeSemantics getTreeSemantics() {
		if (stcs != null)
			return stcs;
		else {
			if (smtcss == null)
				return null;
			else {
				stcs = new TreeSemantics(smtcss);
				return stcs;
			}
		}
	}

	public AnDatasetReq treeSemtcs(TreeSemantics semtcs) {
		this.stcs = semtcs;
		return this;
	}
}
