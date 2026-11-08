package io.odysz.semantic.ext;

import java.sql.SQLException;

import org.xml.sax.SAXException;

import io.odysz.semantic.DA.DatasetCfg.TreeSemantics;
import io.odysz.semantics.IUser;
import io.odysz.transact.x.TransException;

/**
 * A table holding trees, for {@link SemanticTree#onModifyTree(String, ITreeMeta, TreeModify, IUser)}:
 * its tree columns, and the rules of its data.
 *
 * <p>An interface, not a class, so that any table meta can be a tree, a plain SemanticTableMeta
 * or a syntity's meta (SyntityMeta), whose records are synchronized: then the parent column
 * must hold ids that are the same on every synode.</p>
 *
 * @since 1.5.20
 */
public interface ITreeMeta {

	/** The table's tree semantics: table, id, parent, fullpath, sort (text, node type) columns. */
	TreeSemantics treeSemantics();

	/** Column of the node's type, for {@link #canHold(String)}; null: no types */
	default String nodetype() { return null; }

	/**
	 * May a node of the type have children?
	 * Checked when a node goes under another (indent, insert as a child).
	 * 
	 * @param nodetype value of column {@link #nodetype()}
	 */
	default boolean canHold(String nodetype) { return true; }

	/** Top-level nodes are fixed: they don't move or get removed, and no node becomes one. */
	default boolean fixedRoots() { return false; }

	/**
	 * Id of a node to be inserted.
	 * 
	 * @param connId
	 * @param parentId the new node's parent, null for the top level
	 * @param usr
	 */
	String newId(String connId, String parentId, IUser usr) throws TransException, SQLException;

	/**
	 * Tree semantics of columns.
	 * 
	 * @param text column, or null
	 * @param nodetype column, or null
	 * @throws SAXException
	 */
	static TreeSemantics stree(String tabl, String id, String parent, String fullpath, String sort,
			String text, String nodetype) throws SAXException {
		return new TreeSemantics(String.join(",", "", tabl, id, parent, fullpath, sort,
				text == null ? "" : text, nodetype == null ? "" : nodetype, "false"));
	}
}
