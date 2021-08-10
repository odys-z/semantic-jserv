package io.odysz.semantic.ext;

import io.odysz.semantic.DA.DatasetCfg.TreeSemantics;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.R.AnQueryReq;

public class AnDatasetReq extends AnQueryReq {

	String sk;
	public String[] sqlArgs;
	public String rootId;
	/**String array of tree semantics from client, same as dataset.xml/t/c/s-tree */
	protected String s_tree;
	/**{@link TreeSemantics} of tree from {@link #s_tree} or set with {@link #treeSemtcs} ({@link TreeSemantics}) */
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

	/**Get the tree semantics. Client can provide {@link #s_tree} string as tree semantics.
	 * @return parsed semantics
	 */
	public TreeSemantics getTreeSemantics() {
		if (stcs != null)
			return stcs;
		else {
			if (s_tree == null)
				return null;
			else {
				stcs = new TreeSemantics(s_tree);
				return stcs;
			}
		}
	}

	public AnDatasetReq treeSemtcs(TreeSemantics semtcs) {
		this.stcs = semtcs;
		return this;
	}
}
