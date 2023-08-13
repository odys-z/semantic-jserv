package io.odysz.semantic.ext;

import org.xml.sax.SAXException;

import io.odysz.semantic.DA.DatasetCfg.TreeSemantics;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.R.AnQueryReq;

public class AnDatasetReq extends AnQueryReq {
	
	public static class A {
		public static final String reforest = "reforest";
		public static final String retree   = "retree";
		public static final String tagtree  = "tagtree";
		public static final String tagtrees = "tagtrees";
		public static final String untagtree= "untagtree";
		public static final String sqltree  = "sqltree";
	}

	String sk;
	public String[] sqlArgs;
	/**String array of tree semantics from client, same as dataset.xml/t/c/s-tree */
	protected String s_tree;
	/**
	 * The {@link TreeSemantics} of tree from {@link #s_tree} or set
	 * with {@link #treeSemtcs} ({@link TreeSemantics})
	 */
	protected TreeSemantics stcs;

	public String rootId;
	public String root() { return rootId; }

	public AnDatasetReq() { super(null, null);}

	public AnDatasetReq root(String rootId) {
		this.rootId = rootId;
		return this;
	}

	public AnDatasetReq(AnsonMsg<? extends AnsonBody> parent, String funcUri) {
		super(parent, funcUri);
		a = "ds";
	}

	public String sk() { return sk; }

	public static AnDatasetReq formatReq(String furi, AnsonMsg<AnDatasetReq> parent, String sk) {
		AnDatasetReq bdItem = new AnDatasetReq(parent, furi);
		bdItem.sk = sk;
		return bdItem;
	}

	/**Get the tree semantics. Client can provide {@link #s_tree} string as tree semantics.
	 * @return parsed semantics
	 * @throws SAXException 
	 */
	public TreeSemantics getTreeSemantics() throws SAXException {
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
