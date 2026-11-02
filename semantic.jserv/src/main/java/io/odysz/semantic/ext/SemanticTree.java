package io.odysz.semantic.ext;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.transact.sql.parts.condition.ExprPart.constr;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.AnsonException;
import io.odysz.anson.JsonOpt;
import io.odysz.common.Utils;
import io.odysz.common.dbtype;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.DA.DatasetHelper;
import io.odysz.semantic.ext.AnDatasetReq.A;
import io.odysz.semantic.DA.DatasetCfg.TreeSemantics;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.tier.DatasetierReq;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Statement;
import io.odysz.transact.sql.parts.Logic.op;
import io.odysz.transact.sql.parts.condition.Condit;
import io.odysz.transact.x.TransException;

/**
 * Servlet implementing Semantic Tree<br>
 * Querying like query.jserv, return tree data with node configured with semantics in config.xml<br>
 * Using configured sql like TreeGrid is enough for trees like menu, but tree grid also needing joining and condition query, that' not enough.
 * t:<br>
 * reforest: re-build tree/forest structure of the taget table (specified in semantics, paramter sk);<br>
 * retree: re-build tree from root;<br>
 * sql: load tree by configured sql (t = ds, sk = sql-key);<br>
 * [any]: load semantics tree (sk)
 * 
 * @author odys-z@github.com
 */
@WebServlet(description = "Abstract Tree Data Service", urlPatterns = { "/s-tree.serv" })
public class SemanticTree extends ServPort<AnDatasetReq> {
	public SemanticTree() {
		super(Port.stree);
	}

	private static final long serialVersionUID = 1L;

	protected static DATranscxt st;

	static {
		st = JSingleton.defltScxt;
	}	

	@Override
	protected void onGet(AnsonMsg<AnDatasetReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		if (ServFlags.extStree)
			Utils.logi("---------- squery (s-tree.serv) get ----------");
		resp.setCharacterEncoding("UTF-8");
		try {
			jsonResp(msg, resp);
		} catch (SQLException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exGeneral, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}
	
	@Override
	protected void onPost(AnsonMsg<AnDatasetReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		if (ServFlags.extStree)
			Utils.logi("========== squery (s-tree.serv) post ==========");

		resp.setCharacterEncoding("UTF-8");
		try {
			jsonResp(msg, resp);

		} catch (SQLException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exGeneral, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	protected void jsonResp(AnsonMsg<AnDatasetReq> jmsg, HttpServletResponse resp)
			throws IOException, SQLException, SAXException, SsException, TransException {
		resp.setCharacterEncoding("UTF-8");

		String connId = jmsg.body(0).uri();
		connId = Connects.uri2conn(connId);

		// check session
		IUser usr = verifier().verify(jmsg.header());

		AnDatasetReq jreq = jmsg.body(0);
		String a = jreq.a();

		// find tree semantics
		if (jreq.sk == null || jreq.sk.trim().length() == 0)
			throw new SQLException("Sementic key must present for s-tree.serv.");

		AnsonMsg<? extends AnsonResp> r = null;
		if (A.reforest.equals(a))
			r = rebuildForest(connId, getTreeSemtcs(jreq), usr);
		// http://127.0.0.1:8080/ifire/s-tree.serv?sk=easyuitree-area&t=retree&root=002
		else if (A.retree.equals(a)) {
			String root = jreq.root();
			r = rebuildTree(connId, root, getTreeSemtcs(jreq), usr);
		}
		else if (A.tagtree.equals(a)) {
			String root = jreq.root();
			r = tagSubtree(connId, root, getTreeSemtcs(jreq), usr);
		}
		else if (A.tagtrees.equals(a)) {
			r = tagTrees(connId, getTreeSemtcs(jreq), usr);
		}
		else if (A.untagtree.equals(a)) {
			String root = jreq.root();
			r = untagSubtree(connId, root, getTreeSemtcs(jreq), usr);
		}
		else if (DatasetierReq.A.stree.equals(a)) {
			JsonOpt opts = jmsg.opts();
			List<?> lst = loadStree(connId, jreq, usr, opts);
			AnDatasetResp re = new AnDatasetResp(null).forest(lst);
			r = ok(re);
		}
		else throw new SemanticException("SemanticTree: request.A is not suppored: %s", a);

		 write(resp, r, jmsg.opts());
	}

	/**
	 * Figure out tree semantics in the following steps:<br>
	 * 1. if jreq is not null try get it (may be the client has defined a semantics);<br>
	 * 2. if req has an 'sk' parameter, load it from dataset.xml - this way can error prone;<br>
	 * 
	 * @param jreq
	 * @return tree's semantics, {@link TreeSemantics}
	 * @throws SAXException 
	 */
	private TreeSemantics getTreeSemtcs(AnDatasetReq jreq) throws SAXException {
		if (jreq == null)
			return null;
		TreeSemantics ts = jreq.getTreeSemantics();
		if (ts != null)
			return ts;

		return DatasetCfg.getTreeSemtcs(jreq.sk);
	}

	/**
	 * Build s-tree
	 * 
	 * @param connId
	 * @param jreq
	 * @param treeSmtcs
	 * @param usr
	 * @param opts
	 * @return forest
	 * @throws IOException
	 * @throws SQLException
	 * @throws SAXException
	 * @throws SsException
	 * @throws TransException
	 */
	private List<?> loadStree(String connId,
			AnDatasetReq jreq,  IUser usr, JsonOpt opts)
			throws IOException, SQLException, SAXException, SsException, TransException {
		TreeSemantics treeSmtcs = getTreeSemtcs(jreq);
		// for robustness
		if (treeSmtcs == null)
			throw new SemanticException("SemanticTree#loadSTree({s-tree: %s, sk: %s}): Can't build tree, tree semantics is null.",
					jreq.s_tree, jreq.sk);

		List<?> forest = DatasetHelper.loadStree(connId, jreq.sk(), jreq.page(), jreq.size(), jreq.sqlArgs);
		return forest;
	}
	
	protected AnsonMsg<AnDatasetResp> ok(int total, List<?> forest) {
		AnsonMsg<AnDatasetResp> msg = new AnsonMsg<AnDatasetResp>(p, MsgCode.ok);
		AnDatasetResp body = new AnDatasetResp(msg);
		body.forest(forest);
		msg.body(body);
		return msg;
	}

	/**
	 * Rebuild subtree starting at root.<br>
	 * Currently only mysql is supported. You may override this method to adapt to other RDBMS.
	 * 
	 * @param connId
	 * @param rootId
	 * @param semanticss
	 * @param usrInf 
	 * @return response
	 * @throws SQLException
	 * @throws TransException Failed to lookup connection
	 */
	protected AnsonMsg<AnsonResp> rebuildTree(String connId, String rootId, TreeSemantics semanticss, IUser usrInf)
			throws SQLException, TransException {
		int total = 0;
		if (Connects.driverType(connId) == dbtype.mysql) {
			// @deprecated
			total = BuildMysql.rebuildDbTree(rootId, semanticss, usrInf);
		}
		else
			total = Reforest.shapeSubtree(connId, rootId, semanticss, usrInf);

		return ok("re-forest", "Updated %s records from root %s", total, rootId);
	}

	protected AnsonMsg<AnsonResp> rebuildForest(String connId, TreeSemantics semanticss, IUser usrInf)
			throws SQLException, TransException {
		if (Connects.driverType(connId) == dbtype.mysql) {
			
			int total = BuildMysql.rebuildDbForest(semanticss, usrInf);
			return ok("Updated records: %s", total);
		}
		else throw new SQLException("TODO...");
	}

	protected AnsonMsg<AnsonResp> tagTrees(String connId, TreeSemantics sm, IUser usr)
			throws TransException, SQLException {
		ISemantext smtxt = st.instancontxt(connId, usr);

		AnResultset rs = (AnResultset) st.select(sm.tabl(), "t")
				.col(sm.dbRecId(), "rid")
				.where(new Condit(op.isnull, sm.dbParent(), "null").or(new Condit(op.in, sm.dbParent(), "'', 'cate'")))
				.rs(smtxt)
				.rs(0);
		
		while(rs.next()) {
			String root = rs.getString("rid");
			Reforest.tagSubtree(connId, root, "'" + root + "'", sm, usr);
		}
				
		return ok("tagtrees", "Tagged %s trees", rs.total());
	}

	protected AnsonMsg<AnsonResp> tagSubtree(String connId, String rootId, TreeSemantics semanticss, IUser usrInf)
			throws SQLException, TransException {
		int total = 0;
		if (Connects.driverType(connId) == dbtype.mysql) {
			// @deprecated
			throw new SemanticException("TODO ...");
		}
		else
			total = Reforest.tagSubtree(connId, rootId, "'" + rootId + "'", semanticss, usrInf);

		return ok("re-forest", "Tagged %s records from root %s", total, rootId);
	}

	protected AnsonMsg<AnsonResp> untagSubtree(String connId, String rootId, TreeSemantics semanticss, IUser usrInf)
			throws SQLException, TransException {
		int total = 0;
		if (Connects.driverType(connId) == dbtype.mysql) {
			// @deprecated
			throw new SemanticException("TODO ...");
		}
		else
			total = Reforest.tagSubtree(connId, rootId, "null", semanticss, usrInf);

		return ok("re-forest", "Tagged %s records from root %s", total, rootId);
	}

	/**FIXME use semantic.transact to extend this class to build sql for all supported DB.
	 * For mysql 8, see {@link Reforest}.
	 * - even supporting no radix64.<br>
	 * A helper class to rebuild tree structure in db table - in case node's parent changing makes subtree fullpath incorrect.<br>
	 * This needs two DB facilities to work:<br>
	 * 1. the radix64 array<pre>
CREATE TABLE ir_radix64 (
  intv int(11) NOT NULL,
  charv char(1) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (`intv`)
) ENGINE=InnoDB DEFAULT CHARSET=ascii
;

insert into ir_radix64(intv, charv) values (0, '0');
insert into ir_radix64(intv, charv) values (1, '1');
insert into ir_radix64(intv, charv) values (2, '2');
insert into ir_radix64(intv, charv) values (3, '3');
insert into ir_radix64(intv, charv) values (4, '4');
insert into ir_radix64(intv, charv) values (5, '5');
insert into ir_radix64(intv, charv) values (6, '6');
insert into ir_radix64(intv, charv) values (7, '7');

insert into ir_radix64(intv, charv) values (8, '8');
insert into ir_radix64(intv, charv) values (9, '9');
insert into ir_radix64(intv, charv) values (10, 'A');
insert into ir_radix64(intv, charv) values (11, 'B');
insert into ir_radix64(intv, charv) values (12, 'C');
insert into ir_radix64(intv, charv) values (13, 'D');
insert into ir_radix64(intv, charv) values (14, 'E');
insert into ir_radix64(intv, charv) values (15, 'F');

insert into ir_radix64(intv, charv) values (16, 'G');
insert into ir_radix64(intv, charv) values (17, 'H');
insert into ir_radix64(intv, charv) values (18, 'I');
insert into ir_radix64(intv, charv) values (19, 'J');
insert into ir_radix64(intv, charv) values (20, 'K');
insert into ir_radix64(intv, charv) values (21, 'L');
insert into ir_radix64(intv, charv) values (22, 'M');
insert into ir_radix64(intv, charv) values (23, 'N');

insert into ir_radix64(intv, charv) values (24, 'O');
insert into ir_radix64(intv, charv) values (25, 'P');
insert into ir_radix64(intv, charv) values (26, 'Q');
insert into ir_radix64(intv, charv) values (27, 'R');
insert into ir_radix64(intv, charv) values (28, 'S');
insert into ir_radix64(intv, charv) values (29, 'T');
insert into ir_radix64(intv, charv) values (30, 'U');
insert into ir_radix64(intv, charv) values (31, 'V');

insert into ir_radix64(intv, charv) values (32, 'W');
insert into ir_radix64(intv, charv) values (33, 'X');
insert into ir_radix64(intv, charv) values (34, 'Y');
insert into ir_radix64(intv, charv) values (35, 'Z');
insert into ir_radix64(intv, charv) values (36, 'a');
insert into ir_radix64(intv, charv) values (37, 'b');
insert into ir_radix64(intv, charv) values (38, 'C');
insert into ir_radix64(intv, charv) values (39, 'D');

insert into ir_radix64(intv, charv) values (40, 'e');
insert into ir_radix64(intv, charv) values (41, 'f');
insert into ir_radix64(intv, charv) values (42, 'g');
insert into ir_radix64(intv, charv) values (43, 'h');
insert into ir_radix64(intv, charv) values (44, 'i');
insert into ir_radix64(intv, charv) values (45, 'j');
insert into ir_radix64(intv, charv) values (46, 'k');
insert into ir_radix64(intv, charv) values (47, 'l');

insert into ir_radix64(intv, charv) values (48, 'm');
insert into ir_radix64(intv, charv) values (49, 'n');
insert into ir_radix64(intv, charv) values (50, 'o');
insert into ir_radix64(intv, charv) values (51, 'p');
insert into ir_radix64(intv, charv) values (52, 'q');
insert into ir_radix64(intv, charv) values (53, 'R');
insert into ir_radix64(intv, charv) values (54, 's');
insert into ir_radix64(intv, charv) values (55, 't');


insert into ir_radix64(intv, charv) values (56, 'U');
insert into ir_radix64(intv, charv) values (57, 'v');
insert into ir_radix64(intv, charv) values (58, 'w');
insert into ir_radix64(intv, charv) values (59, 'x');
insert into ir_radix64(intv, charv) values (60, 'y');
insert into ir_radix64(intv, charv) values (61, 'z');
insert into ir_radix64(intv, charv) values (62, '+');
insert into ir_radix64(intv, charv) values (63, '-');
</pre>
	 * 2. the stored function:<pre>
CREATE FUNCTION char2rx64(intv int(11)) RETURNS varchar(2)
-- get a radix64 char(2) for an integer value ( 0 ~ 64^2 - 1)
begin
  DECLARE chr0 char(1);
  DECLARE chr1 char(1);
  DECLARE ix INT DEFAULT 0;
  
  set ix = intv & 63; -- 03fh
  select charv into chr0 from ir_radix64 R where R.intv = ix;

  set intv = intv >> 6;
  set ix = intv & 63;
  select charv into chr1 from ir_radix64 R where R.intv = ix;
  
  return concat(chr1, chr0);
end </pre>
	 * See {@link #rebuildDbForest(String[])} for tested sqls.
	 * @author ody
	 */
	static class BuildMysql {
		/**
		 * @param rootId
		 * @param sm
		 * @param dblog 
		 * @return total updated records - not the moved items?
		 * @throws SQLException
		 * @throws TransException 
		 */
		private static int rebuildDbTree(String rootId, TreeSemantics sm, IUser dblog)
				throws SQLException, TransException {
			// clear root parentId
			String sql = String.format("update %1$s set %2$s = null where %2$s = %3$s or %2$s = ''",
					// sm[Ix.tabl][0], sm[Ix.parent][0], sm[Ix.recId][0]);
					sm.tabl(), sm.dbParent(), sm.dbRecId());
			Connects.commit(dblog, sql);

			String pid = null;
			sql = String.format("select %1$s pid from %2$s where %3$s = '%4$s'", 
					// sm[Ix.parent][0], sm[Ix.tabl][0], sm[Ix.recId][0], rootId);
					sm.dbParent(), sm.tabl(), sm.dbRecId(), rootId);
			AnResultset rs = new AnResultset(Connects.select(sql));
			if (rs.beforeFirst().next()) {
				pid = rs.getString("pid");
				if (pid != null && pid.trim().length() == 0)
					pid = null;
			}
				
			String updatei;
			if (pid != null)
				updatei = updateSubroot(rootId, sm);
			else
				updatei = updateRoot(rootId, sm);

			int total = 0;
			int level = 1;
			int[] i = Connects.commit(dblog, updatei);
			while (i != null && i.length > 0 && i[0] > 0) {
				total += i[0];
				updatei = updatePi(rootId, sm, level++);
				i = Connects.commit(dblog, updatei);
			}

			return total;
		}
	
		/**update e_areas
		 * set fullpath = concat(lpad(ifnull(siblingSort, '0'), 2, '0'), ' ', areaId)
		 * where areaId = 'rootId'
		 * @param rootId
		 * @param sm
		 * @return sql for root update
		 */
		private static String updateRoot(String rootId, TreeSemantics sm) {
			// update e_areas set fullpath = concat(lpad(ifnull(siblingSort, '0'), 2, '0'), ' ', areaId)
			// where areaId = 'rootId'
			return String.format("update %1$s set %2$s = concat(char2rx64(ifnull(%3$s, 0)), ' ', %4$s) " +
					"where %4$s = '%5$s'",
					// sm[Ix.tabl][0], sm[Ix.fullpath][0], sm[Ix.sort][0], sm[Ix.recId][0], rootId);
					sm.tabl(), sm.dbFullpath(), sm.dbSort(), sm.dbRecId(), rootId);
		}
	
		private static String updateSubroot(String rootId, TreeSemantics sm) {
			// update a_domain p0 join a_domain R on p0.parentId = R.domainId
			// set p0.fullpath = concat(R.fullpath, '.', char2rx64(ifnull(p0.sort, 0)), ' ', p0.domainId)
			// where p0.domainId = '0202';
			return String.format("update %1$s p0 join %1$s R on p0.%2$s = R.%3$s " +
					"set p0.%4$s = concat(R.%4$s, '.', char2rx64(ifnull(p0.%5$s, 0)), ' ', p0.%3$s) where p0.%3$s = '%6$s'",
					// sm[Ix.tabl][0], sm[Ix.parent][0], sm[Ix.recId][0], sm[Ix.fullpath][0], sm[Ix.sort][0], rootId);
					sm.tabl(), sm.dbParent(), sm.dbRecId(), sm.dbFullpath(), sm.dbSort(), rootId);
		}
		
		//////////////////////////////// forest /////////////////////////////////////////////////////

		/**
		 * @param sm
		 * @param dblog
		 * @return total records
		 * @throws SQLException
		 * @throws TransException 
		 */
		private static int rebuildDbForest(TreeSemantics sm, IUser dblog) throws SQLException, TransException {
			// clear root parentId
			String sql = String.format("update %1$s set %2$s = null where %2$s = %3$s or %2$s = ''",
					// sm[Ix.tabl][0], sm[Ix.parent][0], sm[Ix.recId][0]);
					sm.tabl(), sm.dbParent(), sm.dbRecId());
			Connects.commit(dblog, sql);

			String updatei = updateForestRoot(sm);

			int total = 0;
			int level = 1;
			int[] i = Connects.commit(dblog, updatei);
			while (i != null && i.length > 0 && i[0] > 0) {
				total += i[0];
				updatei = updatePi(null, sm, level++);
				i = Connects.commit(dblog, updatei);
			}
			// return ok("Updated records: %s", total);
			return total;
		}
		
		private static String updateForestRoot(TreeSemantics sm) {
			// update e_areas set fullpath = CONCAT(char2rx64(ifnull(siblingSort, 0)), ' ', areaId) where parentId is null;
			return String.format("update %1$s set %2$s = concat(char2rx64(ifnull(%3$s, 0)), ' ', %4$s) " +
					"where %5$s is null",
					// sm[Ix.tabl][0], sm[Ix.fullpath][0], sm[Ix.sort][0], sm[Ix.recId][0], sm[Ix.parent][0]);
					sm.tabl(), sm.dbFullpath(), sm.dbSort(), sm.dbRecId(), sm.dbParent());
		}

		/**<pre>
update e_areas set fullpath = CONCAT(char2rx64(ifnull(siblingSort, 0)), '#', areaId) where parentId is null;

update e_areas p1 join e_areas p0 on p1.parentId = p0.areaId 
set p1.fullpath = concat(p0.fullpath, ' ', char2rx64(ifnull(p1.siblingSort, 0)), '#', p1.areaId)
where p0.parentId is null;

update e_areas p2 join e_areas p1 on p2.parentId = p1.areaId join e_areas p0 on p1.parentId = p0.areaId
set p2.fullpath = concat(p1.fullpath, ' ', char2rx64(ifnull(p2.siblingSort, 0)), '#', p2.areaId)
where p0.parentId is null;

update e_areas p3 join e_areas p2 on p3.parentId = p2.areaId join e_areas p1 on p2.parentId = p1.areaId join e_areas p0 on p1.parentId = p0.areaId
set p3.fullpath = concat(p2.fullpath, ' ', char2rx64(ifnull(p3.siblingSort, 0)), '#', p3.areaId)
where p0.parentId is null; </pre>
		 * @param sm
		 * @param pi
		 * @return the i-th snippet
		 */
		private static String updatePi(String rootId, TreeSemantics sm, int pi) {
			// e_areas p0 on p1.parentId = p0.areaId
			String p0 = String.format("%1$s p%2$D on p%3$D.%4$s = p%2$D.%5$s",
					// sm[Ix.tabl][0], 0, 1, sm[Ix.parent][0], sm[Ix.recId][0]);
					sm.tabl(), sm.dbParent(), sm.dbRecId());
			for (int i = 1; i < pi; i++) {
				// e_areas p1 on p2.parentId = p1.areaId join [e_areas p0 on p1.parentId = p0.areaId]
				p0 = String.format("%1$s p%2$D on p%3$D.%4$s = p%2$D.%5$s join %6$s",
						// sm[Ix.tabl][0], i, i + 1, sm[Ix.parent][0], sm[Ix.recId][0], p0);
						sm.tabl(), i, i + 1, sm.dbParent(), sm.dbRecId(), p0);
			}
			p0 = String.format("update %1$s p%2$D join %3$s %4$s %5$s",
					// sm[Ix.tabl][0],
					sm.tabl(),
					pi, p0, setPi(sm, pi),
					rootId == null ? String.format("where p0.%1$s is null",
													// sm[Ix.parent][0]
													sm.dbParent()) // where p0.parentId is null
								   : String.format("where p0.%1$s = '%2$s'",
										   // sm[Ix.recId][0],
										   sm.dbRecId(),
										   rootId)); // where p0.areaId = 'rootId'
			return p0;
		}

		private static String setPi(TreeSemantics sm, int pi) {
			// set p2.fullpath = concat(p1.fullpath, ' ', char2rx64(ifnull(p2.siblingSort, 0)), '#', p2.areaId)
			return String.format("set p%1$D.%2$s = concat(p%3$D.%2$s, '.', char2rx64(ifnull(p%1$D.%4$s, 0)), ' ', p%1$D.%5$s)",
					// pi, sm[Ix.fullpath][0], pi - 1, sm[Ix.sort][0], sm[Ix.recId][0]);
					pi, sm.dbFullpath(), pi - 1, sm.dbSort(), sm.dbRecId());
		}
	}

	/*
	 * Tree modification, SQLite only (fullpaths are reshaped by Reforest.shapeSubtree()).
	 *
	 * Each method updates parent and sibling sorts, reshapes the fullpaths under the changed
	 * parents, then responds with the reload root and the forest under it:
	 * the root is the closest node whose subtree contains every node with changed fullpath or
	 * indents, i.e. the lowest common ancestor of the old and new parent, found by fullpath prefix
	 * before updating. Null when the top level is involved: the forest is then the whole table.
	 *
	 * Statements are committed with the connection's semantics, e.g. a fullpath handler on the table
	 * may set temporary fullpaths; Reforest.shapeSubtree() reshapes them afterwards.
	 */

	/** Digits of a sibling sort in fullpath, the same as {@link Reforest#shapeSubtree(String, String, TreeSemantics, IUser)}. */
	static final int sortDigits = 2;

	/** Transact builders for tree modification, per connection. */
	static HashMap<String, DATranscxt> trxes;

	/**
	 * The connection's transact builder, created when first used,
	 * which also loads the connection's table metas.
	 */
	static DATranscxt trans(String connId) throws TransException, SQLException {
		if (trxes == null)
			trxes = new HashMap<String, DATranscxt>();
		if (!trxes.containsKey(connId))
			trxes.put(connId, new DATranscxt(connId));
		return trxes.get(connId);
	}

	/**
	 * Move node req.nid (with its subtree) to req.toParent (null: top level), at req.toIndex,
	 * which is the final position among toParent's children, counted after the node is detached.
	 * 
	 * <p>Rejected, with the table unchanged: "cycle" (into itself or a descendant),
	 * "no-op" (same parent, same index).</p>
	 * 
	 * @return response with reload root, or rejected reason
	 * @throws SemanticException node or parent not found, index out of range
	 * @since 1.5.20
	 */
	public AnDatasetResp onMoveTree(String connId, TreeSemantics sm, AnDatasetReq req, IUser usr)
			throws TransException, SQLException {
		sqliteOnly(connId);
		DATranscxt t = trans(connId);

		String from = nodeRow(t, connId, sm, req.nid, usr).getString(sm.dbParent());
		if (!isblank(req.toParent))
			nodeRow(t, connId, sm, req.toParent, usr); // must exist

		if (req.nid.equals(req.toParent)
			|| !isblank(req.toParent) && isAncestor(t, connId, sm, req.nid, req.toParent, usr))
			return new AnDatasetResp().rejected("cycle");

		List<String> oldKids = children(t, connId, sm, from, usr);
		boolean sameParent = isblank(from) ? isblank(req.toParent) : eq(from, req.toParent);
		if (sameParent && oldKids.indexOf(req.nid) == req.toIndex)
			return new AnDatasetResp().rejected("no-op");

		String root = lca(t, connId, sm, from, req.toParent, usr);

		oldKids.remove(req.nid);
		List<String> newKids = sameParent ? oldKids : children(t, connId, sm, req.toParent, usr);
		insertAt(newKids, req.toIndex, req.nid);

		ArrayList<Statement<?>> sorts = renumber(t, sm, newKids, null);
		if (!sameParent)
			renumber(t, sm, oldKids, sorts);

		t.update(sm.tabl(), usr)
			.nv(sm.dbParent(), req.toParent)
			.whereEq(sm.dbRecId(), req.nid)
			.post(sorts)
			.u(t.instancontxt(connId, usr));

		if (isblank(from) || isblank(req.toParent))
			reshapeForest(t, connId, sm, usr);
		else {
			Reforest.shapeSubtree(connId, from, sm, usr);
			if (!sameParent)
				Reforest.shapeSubtree(connId, req.toParent, sm, usr);
		}

		return respond(t, connId, sm, root, usr);
	}

	/**
	 * Delete node req.nid and its subtree, then renumber its siblings.
	 * 
	 * @return response with reload root, the parent of the deleted node
	 * @throws SemanticException node not found
	 * @since 1.5.20
	 */
	public AnDatasetResp onDelTree(String connId, TreeSemantics sm, AnDatasetReq req, IUser usr)
			throws TransException, SQLException {
		sqliteOnly(connId);
		DATranscxt t = trans(connId);

		String parent = nodeRow(t, connId, sm, req.nid, usr).getString(sm.dbParent());
		List<String> kids = children(t, connId, sm, parent, usr);
		kids.remove(req.nid);

		// the subtree by parent links, not fullpaths, which may be stale while others are modifying the tree
		t.with(true)
			.withTable("sub_(nid)")
			.withVal(constr(req.nid))
			.union(t.select(sm.tabl(), "me")
					.col("me." + sm.dbRecId())
					.je("me", "sub_", "s", sm.dbParent(), "nid"))
			.delete(t.delete(sm.tabl(), usr))
		  .whereIn(sm.dbRecId(), t.select("sub_").col("nid"))
		  .post(renumber(t, sm, kids, null))
		  .d(t.instancontxt(connId, usr));

		if (isblank(parent))
			reshapeForest(t, connId, sm, usr);
		else
			Reforest.shapeSubtree(connId, parent, sm, usr);

		return respond(t, connId, sm, parent, usr);
	}

	/**
	 * Insert node req.nid under req.toParent (null: top level) at req.toIndex,
	 * with other columns in req.nvs, [name, value].
	 * 
	 * @return response with reload root, the parent
	 * @throws SemanticException parent not found, index out of range
	 * @since 1.5.20
	 */
	public AnDatasetResp onInsertTree(String connId, TreeSemantics sm, AnDatasetReq req, IUser usr)
			throws TransException, SQLException {
		sqliteOnly(connId);
		DATranscxt t = trans(connId);

		if (!isblank(req.toParent))
			nodeRow(t, connId, sm, req.toParent, usr); // must exist
		List<String> kids = children(t, connId, sm, req.toParent, usr);
		insertAt(kids, req.toIndex, req.nid);

		Insert ins = t.insert(sm.tabl(), usr)
			.nv(sm.dbRecId(), req.nid)
			.nv(sm.dbParent(), req.toParent)
			.nv(sm.dbSort(), 0); // fullpath: by table semantics if configured, then reshaped below
		if (req.nvs != null)
			for (String[] nv : req.nvs)
				ins.nv(nv[0], nv.length > 1 ? nv[1] : null);

		// posted updates are committed after the insert, the new node included
		ins.post(renumber(t, sm, kids, null))
			.ins(t.instancontxt(connId, usr));

		if (isblank(req.toParent))
			reshapeForest(t, connId, sm, usr);
		else
			Reforest.shapeSubtree(connId, req.toParent, sm, usr);

		return respond(t, connId, sm, req.toParent, usr);
	}

	static void sqliteOnly(String connId) throws SemanticException {
		if (Connects.driverType(connId) != dbtype.sqlite)
			throw new SemanticException("Modifying s-tree is only supported for SQLite yet.");
	}

	/**
	 * Is node a an ancestor of node b, by parent links?
	 * <pre>with recursive anc_(nid) as (select 'b' union
	 * select me.parent from tabl me join anc_ a on me.nid = a.nid)
	 * select nid from anc_ where nid = 'a'</pre>
	 * The union (distinct) stops on cyclic parent links.
	 */
	static boolean isAncestor(DATranscxt t, String connId, TreeSemantics sm, String a, String b, IUser usr)
			throws TransException, SQLException {
		AnResultset rs = (AnResultset) t
			.with(true)
				.withTable("anc_(nid)")
				.withVal(constr(b))
				.union(t.select(sm.tabl(), "me")
						.col("me." + sm.dbParent())
						.je("me", "anc_", "a", sm.dbRecId(), "nid"))
				.select(t.select("anc_"))
			.col("nid")
			.whereEq("nid", a)
			.rs(t.instancontxt(connId, usr))
			.rs(0);
		return rs.beforeFirst().next();
	}

	/** fullpath = fp or fullpath like 'fp.%' (for reading the reloaded subtree) */
	static Condit subtree(TreeSemantics sm, String fp) {
		return new Condit(op.eq, sm.dbFullpath(), "'" + fp + "'")
			.or(new Condit(op.rlike, sm.dbFullpath(), "'" + fp + ".'"));
	}

	/**
	 * Response with the reload root and the forest under it (the whole table if root is null).
	 * The root's own indents are empty: the client keeps the indents it has for the root.
	 */
	static AnDatasetResp respond(DATranscxt t, String connId, TreeSemantics sm, String root, IUser usr)
			throws SQLException, TransException {
		Query q = t.select(sm.tabl()).orderby(sm.dbFullpath());
		if (!isblank(root))
			q.where(subtree(sm, nodeRow(t, connId, sm, root, usr).getString(sm.dbFullpath())));

		AnResultset rs = (AnResultset) q.rs(t.instancontxt(connId, usr)).rs(0);
		return new AnDatasetResp().root(root).forest(DatasetHelper.buildForest(rs, sm));
	}

	/** Top level changed: top-level fullpath = sort, then reshape every tree. */
	static void reshapeForest(DATranscxt t, String connId, TreeSemantics sm, IUser usr)
			throws SQLException, TransException {
		List<String> tops = children(t, connId, sm, null, usr);
		if (tops.size() == 0) return;

		ArrayList<Statement<?>> fps = new ArrayList<Statement<?>>(tops.size());
		for (int x = 1; x < tops.size(); x++)
			fps.add(t.update(sm.tabl())
				.nv(sm.dbFullpath(), pad(x + 1))
				.whereEq(sm.dbRecId(), tops.get(x)));

		t.update(sm.tabl(), usr)
			.nv(sm.dbFullpath(), pad(1))
			.whereEq(sm.dbRecId(), tops.get(0))
			.post(fps)
			.u(t.instancontxt(connId, usr));

		for (String top : tops)
			Reforest.shapeSubtree(connId, top, sm, usr);
	}

	/**
	 * @return the node's row, with the cursor on it: parent, fullpath
	 * @throws SemanticException not found
	 */
	static AnResultset nodeRow(DATranscxt t, String connId, TreeSemantics sm, String nid, IUser usr)
			throws SQLException, TransException {
		if (isblank(nid))
			throw new SemanticException("Tree node id is null.");

		AnResultset rs = (AnResultset) t.select(sm.tabl())
				.col(sm.dbParent()).col(sm.dbFullpath())
				.whereEq(sm.dbRecId(), nid)
				.rs(t.instancontxt(connId, usr))
				.rs(0);

		if (!rs.beforeFirst().next())
			throw new SemanticException("Can't find tree node %s in %s.", nid, sm.tabl());
		return rs;
	}

	/** Children ids of parent (null for the top level), in sibling order. */
	static List<String> children(DATranscxt t, String connId, TreeSemantics sm, String parent, IUser usr)
			throws SQLException, TransException {
		Query q = t.select(sm.tabl())
				.col(sm.dbRecId())
				.orderby(sm.dbSort())
				.orderby(sm.dbFullpath());
		if (isblank(parent))
			q.where(new Condit(op.isnull, sm.dbParent(), "null").or(new Condit(op.eq, sm.dbParent(), "''")));
		else
			q.whereEq(sm.dbParent(), parent);

		return ((AnResultset) q.rs(t.instancontxt(connId, usr)).rs(0)).toArr(sm.dbRecId());
	}

	/**
	 * Lowest common ancestor of two parents, by fullpath prefix.
	 * @return null if any is the top level, or they are in different trees
	 */
	static String lca(DATranscxt t, String connId, TreeSemantics sm, String a, String b, IUser usr)
			throws SQLException, TransException {
		if (isblank(a) || isblank(b)) return null;
		if (a.equals(b)) return a;

		String[] pa = nodeRow(t, connId, sm, a, usr).getString(sm.dbFullpath()).split("\\.");
		String[] pb = nodeRow(t, connId, sm, b, usr).getString(sm.dbFullpath()).split("\\.");
		int x = 0;
		while (x < pa.length && x < pb.length && pa[x].equals(pb[x])) x++;
		if (x == 0) return null;

		AnResultset rs = (AnResultset) t.select(sm.tabl())
				.col(sm.dbRecId())
				.whereEq(sm.dbFullpath(), String.join(".", Arrays.copyOf(pa, x)))
				.rs(t.instancontxt(connId, usr))
				.rs(0);
		return rs.beforeFirst().next() ? rs.getString(sm.dbRecId()) : null;
	}

	static void insertAt(List<String> kids, int ix, String nid) throws SemanticException {
		if (ix < 0 || ix > kids.size())
			throw new SemanticException("Index %s out of range [0, %s] for %s.", ix, kids.size(), nid);
		kids.add(ix, nid);
	}

	/**
	 * Update statements setting sort = position + 1, for each of kids.
	 * @param to appending to, created if null
	 */
	static ArrayList<Statement<?>> renumber(DATranscxt t, TreeSemantics sm, List<String> kids,
			ArrayList<Statement<?>> to) {
		if (to == null)
			to = new ArrayList<Statement<?>>(kids.size());
		for (int x = 0; x < kids.size(); x++)
			to.add(t.update(sm.tabl())
				.nv(sm.dbSort(), x + 1)
				.whereEq(sm.dbRecId(), kids.get(x)));
		return to;
	}

	static String pad(int sort) {
		return String.format("%0" + sortDigits + "d", sort);
	}


	/**
	 * Update fullpath, recursively.
	 *
	 * TODO oracle 11gr2: https://dev.mysql.com/doc/refman/8.0/en/with.html 
	 * TODO mysql 8: https://dev.mysql.com/doc/refman/8.0/en/with.html 
	 * 
	 * @author Odys Zhou
	 */
	public static class Reforest {
		
		/** sqlite<pre>
	with backtrace (indId, parent, fullpath) as (
		select indId, parent, fullpath from ind_emotion2 where indId = 'C'
		union all
		select me.indId, me.parent, p.fullpath || '.' || printf('%02d', sort) from backtrace p
		join ind_emotion2 me on me.parent = p.indId 
	) 
	update ind_emotion2 set fullpath = (
	select fullpath from backtrace t where ind_emotion2.indId = t.indId) where indId in (select indId from backtrace); 
		</pre>
		 * @param connId
		 * @param rootId 
		 * @param sm
		 * @param dblog
		 * @return  updated count
		 * @throws TransException
		 * @throws SQLException
		 */
		public static int shapeSubtree(String connId, String rootId, TreeSemantics sm, IUser dblog) throws TransException, SQLException {

			if (Connects.driverType(connId) != dbtype.sqlite) {
				throw new SemanticException("[TODO] Reshape fullpath are not supported yet.");
			}
			else {
				/* sqlite
				with backtrace (indId, parent, fullpath) as (
					select indId, parent, fullpath from ind_emotion2 where indId = 'C'
					union all
					select me.indId, me.parent, p.fullpath || '.' || printf('%02d', sort) from backtrace p
					join ind_emotion2 me on me.parent = p.indId 
				) 
				update ind_emotion2 set fullpath = (
				select fullpath from backtrace t where ind_emotion2.indId = t.indId) where indId in (select indId from backtrace); 
				*/
				return Connects.commit(connId, dblog, String.format(
				"with backtrace (indId, parent, fullpath) as (" +
					"select %2$s indId, %3$s parent, %4$s fullpath from %1$s where %2$s = '%6$s' " +
					"union all " +
					// FIXME should be "me.<recId> indId" instead of me.indId?
					"select me.%2$s, me.%3$s, p.fullpath || '.' || printf('%%0%7$sd', %5$s) from backtrace p " +
					"join %1$s me on me.%3$s = p.indId) " +
				"update %1$s set %4$s = " +
				"(select %4$s from backtrace t where %1$s.%2$s = t.indId) where %2$s in (select indId from backtrace)",
				sm.tabl(), sm.dbRecId(), sm.dbParent(), sm.dbFullpath(), sm.dbSort(), rootId, 2))[0];
			}
		}
		
		/** Tag all subtree nodes with root's Id.
		 * @param connId
		 * @param rootId
		 * @param tagval 'root-id' or "null"
		 * @param sm
		 * @param dblog
		 * @return updated count
		 * @throws SQLException
		 * @throws TransException
		 */
		public static int tagSubtree(String connId, String rootId, String tagval, TreeSemantics sm, IUser dblog) throws SQLException, TransException {
			if (Connects.driverType(connId) != dbtype.sqlite) {
				throw new SemanticException("[TODO] Reshape fullpath are not supported yet.");
			}
			else {
			/* with backtrace (indId, parent) as (
				select indId, parent from ind_emotion where indId = '000001'
				union all
				select me.indId, me.parent from backtrace p
				join ind_emotion me on me.parent = p.indId 
			) update ind_emotion set templId = '000001' where indId in (select indId from backtrace);
			 */
				return Connects.commit(connId, dblog, String.format(
					"with backtrace (indId, parent) as (" +
					"select %2$s indId,%3$s parent from %1$s where indId = '%5$s' " +
					"union all " +
					"select me.%2$s indId, me.%3$s parent from backtrace p " +
					"join %1$s me on me.parent = p.indId" +
					") update %1$s set %4$s = %6$s where %2$s in (select indId from backtrace)",
					sm.tabl(), sm.dbRecId(), sm.dbParent(), sm.dbTagCol(), rootId, tagval))[0];
			}
		}
		
	}
}
