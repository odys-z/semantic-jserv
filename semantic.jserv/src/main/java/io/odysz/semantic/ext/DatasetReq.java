package io.odysz.semantic.ext;

import io.odysz.semantic.DA.DatasetCfg.TreeSemantics;
import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jserv.R.QueryReq;

public class DatasetReq extends QueryReq {

	String sk;
	public String[] sqlArgs;
	public String rootId;
	/**String array of tree semantics from client */
	private String smtcss;
	/**{@link TreeSEmantics} of tree from {@link #smtcss} or set with {@link #treeSemtcs(TreeSemantics)} */
	private TreeSemantics stcs;

	public DatasetReq(JMessage<? extends JBody> parent) {
		super(parent);
	}

	public String sk() { return sk; }
	public int size() { return pgsize; }
	public int page() { return page; }

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

	public void treeSemtcs(TreeSemantics semtcs) {
		this.stcs = semtcs;
	}

}
