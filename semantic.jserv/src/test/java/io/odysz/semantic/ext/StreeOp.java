//package io.odysz.semantic.ext;
//
//import io.odysz.anson.Anson;
//import io.odysz.semantic.ext.SemanticTree.TreeMod;
//
///**
// * A tree modification in the test data, converted to {@link TreeMod}.
// * @since 1.5.20
// */
//public class StreeOp extends Anson {
//	/** {@link TreeMod#move}, {@link TreeMod#insert}, {@link TreeMod#remove} */
//	String a;
//	String nid;
//	String toParent;
//	int toIndex;
//
//	/** insert only */
//	String nodetype;
//	/** insert only */
//	String text;
//
//	public StreeOp() { }
//
//	TreeMod toTreeMod() {
//		if (TreeMod.move.equals(a))
//			return TreeMod.move(nid, toParent, toIndex);
//		if (TreeMod.insert.equals(a))
//			return TreeMod.insert(nid, toParent, toIndex).col("nodetype", nodetype).col("text", text);
//		if (TreeMod.remove.equals(a))
//			return TreeMod.remove(nid);
//		throw new IllegalArgumentException("Unknown op: " + a);
//	}
//}
