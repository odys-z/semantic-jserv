package io.odysz.semantic.ext;

import java.util.ArrayList;

import io.odysz.anson.Anson;

/**
 * A placement in the test data, for SemanticTree's internal moveNode(), insertNode(), removeNode():
 * the parent and index that {@link SemanticTree#onModifyTree(String, ITreeMeta, TreeModify, io.odysz.semantics.IUser)}
 * works out from a {@link TreeModify}.
 * @since 1.5.20
 */
public class StreePlace extends Anson {
	/** move | insert | remove */
	String a;
	String nid;
	/** move, insert: the new parent, null for the top level */
	String parent;
	/** move, insert: the final position among parent's children, counted after the node is detached */
	int index;
	/** insert: other columns, [name, value] */
	ArrayList<String[]> nvs;

	public StreePlace() { }

	@Override
	public String toString() {
		return "remove".equals(a) ? String.format("remove %s", nid) : String.format("%s %s -> %s[%s]", a, nid, parent, index);
	}
}
