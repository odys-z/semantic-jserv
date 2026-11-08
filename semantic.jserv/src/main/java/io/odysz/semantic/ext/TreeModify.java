package io.odysz.semantic.ext;

import java.util.ArrayList;

import io.odysz.anson.Anson;

/**
 * A modification of a tree, as the user means it: the server works out the parent
 * and position ({@link SemanticTree#onModifyTree(String, ITreeMeta, TreeModify, io.odysz.semantics.IUser)}),
 * so clients never compute sibling indexes.
 *
 * <pre>
 * up, down        swap with the previous / next sibling
 * indent          become the last child of the previous sibling
 * outdent         become the next sibling of the parent
 * remove          delete the node with its subtree
 * insert          a new node, with columns {@link #nvs}: {@link #at} after {@link #nid},
 *                 or as its first / last child
 * </pre>
 *
 * @since 1.5.20
 */
public class TreeModify extends Anson {

	/** The modification. Serialized by name, e.g. "indent". */
	public enum Op {
		/** swap with the previous sibling */
		up,
		/** swap with the next sibling */
		down,
		/** become the last child of the previous sibling */
		indent,
		/** become the next sibling of the parent */
		outdent,
		/** delete the node with its subtree */
		remove,
		/** a new node, see {@link At} */
		insert
	}

	/** Where an {@link Op#insert} goes, relative to {@link TreeModify#nid}. */
	public enum At {
		/** the next sibling */
		after,
		/** the first child */
		first,
		/** the last child */
		last
	}

	/** Why a modification is refused, the table unchanged: AnDatasetResp.rejected(). */
	public enum Rejected {
		/** nothing to do, e.g. the first child up */
		noop,
		/** into itself or a descendant */
		cycle,
		/** under a node that can't have children, see {@link ITreeMeta#canHold(String)} */
		holder,
		/** moving / removing a top-level node, or making one, see {@link ITreeMeta#fixedRoots()} */
		fixed
	}

	/** see {@link Op} */
	public Op op;
	/** the node acted on; for an insert, the node the new one goes after / under */
	public String nid;
	/** insert: see {@link At} */
	public At at;
	/** insert: the new node's other columns, [name, value] */
	public ArrayList<String[]> nvs;

	public TreeModify() { }

	public TreeModify(Op op, String nid) {
		this.op = op;
		this.nid = nid;
	}

	public static TreeModify up(String nid)      { return new TreeModify(Op.up, nid); }
	public static TreeModify down(String nid)    { return new TreeModify(Op.down, nid); }
	public static TreeModify indent(String nid)  { return new TreeModify(Op.indent, nid); }
	public static TreeModify outdent(String nid) { return new TreeModify(Op.outdent, nid); }
	public static TreeModify remove(String nid)  { return new TreeModify(Op.remove, nid); }

	/**
	 * @param nid the node the new one goes after / under
	 * @param at see {@link At}
	 * @param nvs the new node's other columns: name, value, name, value, ...
	 */
	public static TreeModify insert(String nid, At at, String... nvs) {
		TreeModify m = new TreeModify(Op.insert, nid);
		m.at = at;
		m.nvs = new ArrayList<String[]>();
		for (int x = 0; x + 1 < nvs.length; x += 2)
			m.nvs.add(new String[] { nvs[x], nvs[x + 1] });
		return m;
	}

	@Override
	public String toString() {
		return op == Op.insert ? String.format("insert %s %s", at, nid) : String.format("%s %s", op, nid);
	}
}
