package io.odysz.semantic.ext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static io.odysz.semantic.ext.SemanticTree.childIds;
import static io.odysz.semantic.ext.SemanticTree.fullpathOf;
import static java.util.Arrays.asList;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.odysz.anson.Anson;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg.TreeSemantics;
import io.odysz.semantic.ext.TreeModify.At;
import io.odysz.semantic.ext.TreeModify.Op;
import io.odysz.semantic.ext.TreeModify.Rejected;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantics.IUser;
import io.odysz.transact.sql.Insert;

/**
 * Tests of {@link SemanticTree#onModifyTree(String, ITreeMeta, TreeModify, IUser)},
 * on table doctree of the main test db (src/test/res/jserv-main.db), reset before each test:
 *
 * <pre>
 * D1 doc        01
 *   A section   01.01
 *     a1 para   01.01.01
 *     a2 section 01.01.02
 *       a21 para, a22 para
 *     a3 para   01.01.03
 *   B section: b1
 *   C section (empty)
 *   E section: e1, e2 section: e21
 * D2 doc
 *   F section: f1
 * </pre>
 *
 * Rules (the {@link ITreeMeta}): docs and sections hold children, paragraphs don't;
 * the docs are fixed; new ids are n1, n2, ...
 *
 * @since 1.5.20
 */
class SemanticTreeTest {
	static final String conn = "main-sqlite";
	static final String webinf = "src/test/res/WEB-INF";

	/** The test db, created empty if missing (Connects requires the file). */
	static final String db = "src/test/res/jserv-main.db";

	static final IUser robot = new JRobot();

	static final String[] ddl = {
		"drop table if exists doctree",
		"create table doctree (nid varchar(12) primary key, parent varchar(12), sort integer, "
			+ "fullpath varchar(256), nodetype varchar(12), text varchar(256))",
	};

	/**
	 * nid, parent, sort, nodetype; fullpath by the table's semantics (semantics.xml, doctree.fp),
	 * one insert each, as it reads the parent's fullpath from the table.
	 */
	static final String[][] fixture = {
		{"D1", null, "1", "doc"},
		{"A",  "D1", "1", "section"},
		{"a1", "A",  "1", "para"},
		{"a2", "A",  "2", "section"},
		{"a21","a2", "1", "para"},
		{"a22","a2", "2", "para"},
		{"a3", "A",  "3", "para"},
		{"B",  "D1", "2", "section"},
		{"b1", "B",  "1", "para"},
		{"C",  "D1", "3", "section"},
		{"E",  "D1", "4", "section"},
		{"e1", "E",  "1", "para"},
		{"e2", "E",  "2", "section"},
		{"e21","e2", "1", "para"},
		{"D2", null, "2", "doc"},
		{"F",  "D2", "1", "section"},
		{"f1", "F",  "1", "para"},
	};

	static class TestTree implements ITreeMeta {
		int n = 0;
		final TreeSemantics stree;

		TestTree() throws Exception {
			stree = ITreeMeta.stree("doctree", "nid", "parent", "fullpath", "sort", "text", "nodetype");
		}

		@Override public TreeSemantics treeSemantics() { return stree; }
		@Override public String nodetype() { return "nodetype"; }
		@Override public boolean canHold(String type) { return "doc".equals(type) || "section".equals(type); }
		@Override public boolean fixedRoots() { return true; }
		@Override public String newId(String connId, String parentId, IUser usr) { return "n" + ++n; }
	}

	static TestTree tree;
	static TreeSemantics sm;

	@BeforeAll
	static void init() throws Exception {
		new File(db).createNewFile();
		Connects.init(webinf);
		// table metas of a connection are loaded once, by its first DATranscxt, so create the table first
		Connects.commit(conn, null, new ArrayList<String>(Arrays.asList(ddl)));
		Connects.clearMeta(conn);
		tree = new TestTree();
		sm = tree.treeSemantics();
	}

	@BeforeEach
	void reset() throws Exception {
		Connects.commit(conn, null, new ArrayList<String>(Arrays.asList(ddl)));

		DATranscxt st = SemanticTree.trans(conn);
		for (String[] r : fixture) {
			Insert ins = st.insert("doctree", robot)
				.nv("nid", r[0]).nv("sort", Integer.valueOf(r[2]))
				.nv("nodetype", r[3]).nv("text", r[0]);
			if (r[1] != null)
				ins.nv("parent", r[1]);
			ins.ins(st.instancontxt(conn, robot));
		}
	}

	/** Close the connections (on Windows an open db file can't be deleted), then delete the test db. */
	@AfterAll
	static void clean() throws Exception {
		Connects.close();
		Files.deleteIfExists(Paths.get(db));
	}

	static AnDatasetResp modify(TreeModify mod) throws Exception {
		return new SemanticTree().onModifyTree(conn, tree, mod, robot);
	}

	/** The fixture's fullpaths, by the table's semantics on insert */
	@Test
	void testFixture() throws Exception {
		assertEquals("01.01.02.02", fullpathOf(conn, sm, "a22", robot));
		assertEquals("01.04.02.01", fullpathOf(conn, sm, "e21", robot));
		assertEquals("02.01.01", fullpathOf(conn, sm, "f1", robot));
	}

	@Test
	void testUpDown() throws Exception {
		AnDatasetResp r = modify(TreeModify.up("a3"));
		assertEquals(null, r.rejected());
		assertEquals("A", r.root());
		assertEquals(asList("a1", "a3", "a2"), childIds(conn, sm, "A", robot));
		assertEquals("01.01.02", fullpathOf(conn, sm, "a3", robot));
		assertEquals("01.01.03.01", fullpathOf(conn, sm, "a21", robot));

		modify(TreeModify.down("a1"));
		assertEquals(asList("a3", "a1", "a2"), childIds(conn, sm, "A", robot));

		assertEquals(Rejected.noop, modify(TreeModify.up("a3")).rejected());
		assertEquals(Rejected.noop, modify(TreeModify.down("a2")).rejected());
	}

	@Test
	void testIndentOutdent() throws Exception {
		// the last child of the previous sibling, a section
		AnDatasetResp r = modify(TreeModify.indent("a3"));
		assertEquals("A", r.root());
		assertEquals(asList("a1", "a2"), childIds(conn, sm, "A", robot));
		assertEquals(asList("a21", "a22", "a3"), childIds(conn, sm, "a2", robot));
		assertEquals("01.01.02.03", fullpathOf(conn, sm, "a3", robot));

		// the next sibling of the parent
		modify(TreeModify.outdent("a21"));
		assertEquals(asList("a1", "a2", "a21"), childIds(conn, sm, "A", robot));
		assertEquals(asList("a22", "a3"), childIds(conn, sm, "a2", robot));

		assertEquals(Rejected.noop, modify(TreeModify.indent("a1")).rejected());
		// a1 is a paragraph
		assertEquals(Rejected.holder, modify(TreeModify.indent("a2")).rejected());
	}

	@Test
	void testRemove() throws Exception {
		AnDatasetResp r = modify(TreeModify.remove("a2"));
		assertEquals("A", r.root());
		assertEquals(asList("a1", "a3"), childIds(conn, sm, "A", robot));
		assertEquals(asList(), childIds(conn, sm, "a2", robot));
		assertEquals("01.01.02", fullpathOf(conn, sm, "a3", robot));
	}

	@Test
	void testInsert() throws Exception {
		AnDatasetResp r = modify(TreeModify.insert("a1", At.after, "nodetype", "para", "text", "new"));
		assertEquals(null, r.rejected());
		assertEquals("n1", r.newId());
		assertEquals(asList("a1", "n1", "a2", "a3"), childIds(conn, sm, "A", robot));
		assertEquals("01.01.02", fullpathOf(conn, sm, "n1", robot));

		assertEquals("n2", modify(TreeModify.insert("C", At.last, "nodetype", "para")).newId());
		assertEquals(asList("n2"), childIds(conn, sm, "C", robot));

		modify(TreeModify.insert("a2", At.first, "nodetype", "para"));
		assertEquals(asList("n3", "a21", "a22"), childIds(conn, sm, "a2", robot));

		assertEquals(Rejected.holder, modify(TreeModify.insert("a1", At.first, "nodetype", "para")).rejected());
	}

	/** No top-level node moves, is removed, or is made. */
	@Test
	void testFixedRoots() throws Exception {
		assertEquals(Rejected.fixed, modify(TreeModify.up("D2")).rejected());
		assertEquals(Rejected.fixed, modify(TreeModify.outdent("A")).rejected());
		assertEquals(Rejected.fixed, modify(TreeModify.remove("D1")).rejected());
		assertEquals(Rejected.fixed, modify(TreeModify.insert("D1", At.after, "nodetype", "doc")).rejected());
		assertEquals(asList("D1", "D2"), childIds(conn, sm, null, robot));
		assertEquals(asList("A", "B", "C", "E"), childIds(conn, sm, "D1", robot));
	}

	/** Enums go by name; an unknown name is refused while parsing. */
	@Test
	void testAnson() throws Exception {
		AnDatasetReq req = new AnDatasetReq().mod(TreeModify.insert("a1", At.after, "nodetype", "para"));
		TreeModify back = ((AnDatasetReq) Anson.fromJson(req.toBlock())).mod;
		assertEquals(Op.insert, back.op);
		assertEquals(At.after, back.at);
		assertEquals("para", back.nvs.get(0)[1]);

		assertEquals(Rejected.holder, ((AnDatasetResp) Anson.fromJson(
				new AnDatasetResp().rejected(Rejected.holder).toBlock())).rejected());

		assertThrows(Exception.class, () -> Anson.fromJson(
				"{\"type\": \"io.odysz.semantic.ext.TreeModify\", \"op\": \"move\", \"nid\": \"a2\"}"));
	}
}
