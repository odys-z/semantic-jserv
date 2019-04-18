package io.odysz.semantic.ext;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.common.dbtype;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.DA.DatasetCfg.TreeSemantics;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JMessage.MsgCode;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.R.QueryReq;
import io.odysz.semantic.jserv.R.SQuery;
import io.odysz.semantic.jserv.helper.ServletAdapter;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
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
public class SemanticTree extends SQuery {
	private static final long serialVersionUID = 1L;

	private static Port p = Port.stree;
	
	protected static JHelper<DatasetReq> jtreeReq;

	static {
		st = JSingleton.defltScxt;
		jtreeReq  = new JHelper<DatasetReq>();
		verifier = JSingleton.getSessionVerifier();
	}	

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest req, HttpServletResponse response) throws IOException {
		if (ServFlags.extStree)
			Utils.logi("---------- stree.jserv post <- %s ----------", req.getRemoteAddr());
		jsonResp(req, response);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest req, HttpServletResponse response) throws IOException {
		if (ServFlags.extStree)
			Utils.logi("========== stree.jserv post <= %s ==========", req.getRemoteAddr());
		jsonResp(req, response);
	}

	protected void jsonResp(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		resp.setCharacterEncoding("UTF-8");
		try {
//			String t = req.getParameter("t");
//			if (t == null)
//				throw new SemanticException("s-tree.serv usage: t=load/reforest/retree&rootId=...");
			String connId = req.getParameter("conn");


			// check session
			JMessage<DatasetReq> jmsg = ServletAdapter.<DatasetReq>read(req, jtreeReq, DatasetReq.class);
			IUser usr = JSingleton.getSessionVerifier().verify(jmsg.header());

			DatasetReq jreq = jmsg.body(0);
			String t = jreq.a();

			// find tree semantics
//			String semanticKey = jreq.sk;
			if (jreq.sk == null || jreq.sk.trim().length() == 0)
				throw new SQLException("Sementic key must present for s-tree.serv.");

			// String semantic = Configs.getCfg("tree-semantics", semanticKey);
			SemanticObject r;
			// t branches: reforest | retree | ds | <empty>
			// http://127.0.0.1:8080/ifire/s-tree.serv?sk=easyuitree-area&t=reforest
			if ("reforest".equals(t))
				r = rebuildForest(connId, getTreeSemtcs(req, jreq), usr);
			// http://127.0.0.1:8080/ifire/s-tree.serv?sk=easyuitree-area&t=retree&root=002
			else if ("retree".equals(t)) {
				String root = req.getParameter("root");
				r = rebuildTree(connId, root, getTreeSemtcs(req, jreq), usr);
			}
			else {
				if ("sqltree".equals(t)) {
					// ds (tree configured in dataset.xml)
					List<SemanticObject> lst = DatasetCfg.loadStree(connId,
							jreq.sk, jreq.page(), jreq.size(), jreq.sqlArgs);
					r = JProtocol.ok(p, lst);
				}
//				else if ("sqltable".equals(t)) {
//					SResultset lst = DatasetCfg.loadDataset(connId,
//							jreq.sk, jreq.page(), jreq.size(), jreq.sqlArgs);
//					r = JProtocol.ok(p, lst);
//				}
				else {
					// empty (build tree from general query results with semantic of 'sk')

					r = loadSemantics(connId, jreq, getTreeSemtcs(req, jreq));
				}
			}

			ServletAdapter.write(resp, r);
		} catch (SemanticException e) {
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exTransct, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exGeneral, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	/**Figure out tree semantics in the following steps:<br>
	 * 1. if jreq is not null try get it (the client is defined a semantics);<br>
	 * 2. if req has an 'sk' parameter, load in confix.xml, if failed, try dataset.xml;<br>
	 * 3. if jreq has and 'sk' parameter, load in confix.xml, if failed, try dataset.xml.<br>
	 * @param req
	 * @param jreq
	 * @return
	 */
	private TreeSemantics getTreeSemtcs(HttpServletRequest req, DatasetReq jreq) {
		TreeSemantics ts = jreq.getTreeSemantics();
		if (ts != null)
			return ts;

		String sk = req.getParameter("sk");
		if (sk == null)
			sk = jreq == null ? null : jreq.sk;
		String tss = Configs.getCfg("tree-semantics", sk);
		if (tss != null)
			return new TreeSemantics(tss);
		return DatasetCfg.getTreeSemtcs(sk);
	}

	/**Build s-tree with general query ({@link SQuery#query(QueryReq)}).
	 * @param connId
	 * @param jobj
	 * @param treeSmtcs
	 * @return
	 * @throws IOException
	 * @throws SQLException
	 * @throws SAXException
	 * @throws SsException
	 * @throws TransException
	 */
	private SemanticObject loadSemantics(String connId, DatasetReq jobj, TreeSemantics treeSmtcs)
			throws IOException, SQLException, SAXException, SsException, TransException {
		// for robustness
		String rootId = jobj.rootId;
		if (rootId != null && rootId.trim().length() == 0)
			rootId = null;
		
		SemanticObject rs = query((QueryReq)jobj);
		List<SemanticObject> resp = null;
		if (rs != null)
			resp = DatasetCfg.buildForest((SResultset) rs.get("rs"), treeSmtcs);
		return JProtocol.ok(p, resp);
	}

	
	/**Rebuild subtree starting at root.<br>
	 * Currently only mysql is supported. You may override this method to adapt to other RDBMS.
	 * @param connId
	 * @param rootId
	 * @param semanticss
	 * @param usrInf 
	 * @return
	 * @throws SQLException
	 */
	protected SemanticObject rebuildTree(String connId, String rootId, TreeSemantics semanticss, IUser usrInf)
			throws SQLException {
		if (Connects.driverType(connId) == dbtype.mysql)
			return BuildMysql.rebuildDbTree(rootId, semanticss, usrInf);
		else throw new SQLException("TODO...");
	}

	protected SemanticObject rebuildForest(String connId, TreeSemantics semanticss, IUser usrInf)
			throws SQLException {
		if (Connects.driverType(connId) == dbtype.mysql)
			return BuildMysql.rebuildDbForest(semanticss, usrInf);
		else throw new SQLException("TODO...");
	}

	/**FIXME use semantic.transact to extend this class to build sql for all supported DB
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
insert into ir_radix64(intv, charv) values (38, 'c');
insert into ir_radix64(intv, charv) values (39, 'd');

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
insert into ir_radix64(intv, charv) values (53, 'r');
insert into ir_radix64(intv, charv) values (54, 's');
insert into ir_radix64(intv, charv) values (55, 't');


insert into ir_radix64(intv, charv) values (56, 'u');
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
  select charv into chr0 from ir_radix64 r where r.intv = ix;

  set intv = intv >> 6;
  set ix = intv & 63;
  select charv into chr1 from ir_radix64 r where r.intv = ix;
  
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
		 * @return
		 * @throws SQLException
		 */
		private static SemanticObject rebuildDbTree(String rootId, TreeSemantics sm, IUser dblog)
				throws SQLException {
			// clear root parentId
			String sql = String.format("update %1$s set %2$s = null where %2$s = %3$s or %2$s = ''",
					// sm[Ix.tabl][0], sm[Ix.parent][0], sm[Ix.recId][0]);
					sm.tabl(), sm.dbParent(), sm.dbRecId());
			Connects.commit(dblog, sql);

			String pid = null;
			sql = String.format("select %1$s pid from %2$s where %3$s = '%4$s'", 
					// sm[Ix.parent][0], sm[Ix.tabl][0], sm[Ix.recId][0], rootId);
					sm.dbParent(), sm.tabl(), sm.dbRecId(), rootId);
			SResultset rs = Connects.select(sql);
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

//			SemanticObject respMsg = new SemanticObject();
//			respMsg.put("code", "ok");
//			respMsg.put("port", Port.stree);
//			respMsg.put("msg", String.format("Updated %d records from root %s", total, rootId));
//			return respMsg;
			return JProtocol.ok(p, "Updated %s records from root %s", total, rootId);
		}
	
		/**update e_areas
		 * set fullpath = concat(lpad(ifnull(siblingSort, '0'), 2, '0'), ' ', areaId)
		 * where areaId = 'rootId'
		 * @param rootId
		 * @param sm
		 * @return
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
			// update a_domain p0 join a_domain r on p0.parentId = r.domainId
			// set p0.fullpath = concat(r.fullpath, '.', char2rx64(ifnull(p0.sort, 0)), ' ', p0.domainId)
			// where p0.domainId = '0202';
			return String.format("update %1$s p0 join %1$s r on p0.%2$s = r.%3$s " +
					"set p0.%4$s = concat(r.%4$s, '.', char2rx64(ifnull(p0.%5$s, 0)), ' ', p0.%3$s) where p0.%3$s = '%6$s'",
					// sm[Ix.tabl][0], sm[Ix.parent][0], sm[Ix.recId][0], sm[Ix.fullpath][0], sm[Ix.sort][0], rootId);
					sm.tabl(), sm.dbParent(), sm.dbRecId(), sm.dbFullpath(), sm.dbSort(), rootId);
		}
		
		//////////////////////////////// forest /////////////////////////////////////////////////////

		private static SemanticObject rebuildDbForest(TreeSemantics sm, IUser dblog) throws SQLException {
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
			// return JsonHelper.OK("Updated records: " + total);
			return JProtocol.ok(p, "Updated records: %s", total);
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
		 * @return
		 */
		private static String updatePi(String rootId, TreeSemantics sm, int pi) {
			// e_areas p0 on p1.parentId = p0.areaId
			String p0 = String.format("%1$s p%2$d on p%3$d.%4$s = p%2$d.%5$s",
					// sm[Ix.tabl][0], 0, 1, sm[Ix.parent][0], sm[Ix.recId][0]);
					sm.tabl(), sm.dbParent(), sm.dbRecId());
			for (int i = 1; i < pi; i++) {
				// e_areas p1 on p2.parentId = p1.areaId join [e_areas p0 on p1.parentId = p0.areaId]
				p0 = String.format("%1$s p%2$d on p%3$d.%4$s = p%2$d.%5$s join %6$s",
						// sm[Ix.tabl][0], i, i + 1, sm[Ix.parent][0], sm[Ix.recId][0], p0);
						sm.tabl(), i, i + 1, sm.dbParent(), sm.dbRecId(), p0);
			}
			p0 = String.format("update %1$s p%2$d join %3$s %4$s %5$s",
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
			return String.format("set p%1$d.%2$s = concat(p%3$d.%2$s, '.', char2rx64(ifnull(p%1$d.%4$s, 0)), ' ', p%1$d.%5$s)",
					// pi, sm[Ix.fullpath][0], pi - 1, sm[Ix.sort][0], sm[Ix.recId][0]);
					pi, sm.dbFullpath(), pi - 1, sm.dbSort(), sm.dbRecId());
		}
	}
}
