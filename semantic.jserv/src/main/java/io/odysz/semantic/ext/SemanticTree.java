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
import io.odysz.semantic.DA.DatasetCfg.Ix;
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
@WebServlet(description = "Abstract Tree Data Service", urlPatterns = { "/s-tree.jserv" })
public class SemanticTree extends SQuery {
	private static final long serialVersionUID = 1L;

	
	protected static JHelper<QueryReq> jtreeReq;

	static {
		st = JSingleton.defltScxt;
		jtreeReq  = new JHelper<QueryReq>();
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
			String t = req.getParameter("t");
			if (t == null)
				throw new SemanticException("s-tree.serv usage: t=load/reforest/retree&rootId=...");
			String connId = req.getParameter("conn");

			JMessage<QueryReq> msg = ServletAdapter.<QueryReq>read(req, jtreeReq, QueryReq.class);
			// check session
			IUser usr = JSingleton.getSessionVerifier().verify(msg.header());

			// find tree semantics
			String semanticKey = req.getParameter("sk");
			if (semanticKey == null || semanticKey.trim().length() == 0)
				throw new SQLException("Sementic key must present for s-tree.serv.");
			String semantic = Configs.getCfg("tree-semantics", semanticKey);
			String[][] semanticss = null; 
			if (!"sql".equals(t)) {
				if (semantic == null || semantic.trim().length() == 0)
					throw new SQLException(String.format(
						"Sementics not cofigured correctly: \n\t%s\n\t%s", semanticKey, semantic));
				semanticss = parseSemantics(semantic);
			}

			SemanticObject r;
			// branches
			// http://127.0.0.1:8080/ifire/s-tree.serv?sk=easyuitree-area&t=reforest
			if ("reforest".equals(t))
				r = rebuildForest(connId, semanticss, usr);
			// http://127.0.0.1:8080/ifire/s-tree.serv?sk=easyuitree-area&t=retree&root=002
			else if ("retree".equals(t)) {
				String root = req.getParameter("root");
				r = rebuildTree(connId, root, semanticss, usr);
			}
			else {
				// sql or any
				int page = 0;
				int size = 20;
				try {page = Integer.valueOf(req.getParameter("page"));
				}catch (Exception e) {}
				try {size = Integer.valueOf(req.getParameter("size"));
				}catch (Exception e) {}
				
				if ("sql".equals(t)) {
					// sql
					String[] args = req.getParameterValues("args");
					r = loadConfigArgs(connId, semanticKey, args, page, size);
				}
				else {
					// any
					String rootId = req.getParameter("root");
					r = loadSemantics(msg.body(0), page, size, rootId, connId, semanticss);
				}
			}

			ServletAdapter.write(resp, r);
		} catch (SemanticException e) {
			ServletAdapter.write(resp, JProtocol.err(Port.query, MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(Port.query, MsgCode.exTransct, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			ServletAdapter.write(resp, JProtocol.err(Port.query, MsgCode.exGeneral, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	/**Get Tree
	 * @param msg
	 * @param semanticss
	 * @return
	 * @throws TransException 
	 * @throws SQLException 
	 */
	private SemanticObject query(JMessage<QueryReq> msg, String[][] semanticss) throws SQLException, TransException {
		return super.query(msg);
	}

	static SemanticObject loadConfigArgs(String connId, String sqlkey, String[] args,
			int page, int size) throws SQLException, SemanticException {
//		String[][] semanticss = parseSemantics(DatasetCfg.getStree(connId, sqlkey));
//		String sql = DatasetCfg.getSql(connId, sqlkey, args);
		// resp = loadSemantics(payload, page, size, rootId, connId, semanticss);
//		if (page >= 0 && size >= 0) {
//			sql = Connects.pagingSql(connId, sql, page, size);
//		}

//		String s1 = String.format("select count(*) as total from (%s) t", sql);
//		SResultset rs0 = Connects.select(s1);
//		rs0.beforeFirst().next();
//		int total = rs0.getInt("total");

		// SResultset rs = DatasetCfg.mapRs(connId, sqlkey, Connects.select(sql));
		List<SemanticObject> f = DatasetCfg.loadStree(connId, sqlkey);
		SemanticObject block = new SemanticObject();
//		block.add("total", total);
		block.put("forest", f);
		return block;
	}

	/**parse "easyui,,e_areas,areaId id,parentId,areaName text,fullpath,siblingSort,false" to 2d array.
	 * @param semantic
	 * @return [0:[easyui, null], 1:[checked, null], 2:[tabl, null], 3:[areaId, id], ...]
	 */
	private static String[][] parseSemantics(String semantic) {
		if (semantic == null) return null;
		String[][] sm = new String[Ix.count][];
		String[] sms = semantic.split(",");
		for (int ix = 0; ix < sms.length; ix++) {
			String smstr = sms[ix];
			smstr = smstr.replaceAll("\\s+[aA][sS]\\s+", " "); // replace " as "
			String[] smss = smstr.split(" ");
			if (smss == null || smss.length > 2 || smss[0] == null)
				System.err.println(String.format("WARN - SematnicTree: ignoring semantics not understandable: %s", smstr));
			else {
				sm[ix] = new String[] {smss[0].trim(),
					(smss.length > 1 && smss[1] != null) ? smss[1].trim() : null};
			}
		}

		return sm;
	}

//	public static SemanticObject loadSemantics(QueryReq jobj, int page, int pgSize, String rootId,
//			String connId, String sk) throws IOException, SQLException, SsException, SAXException {
//		String semantic = Configs.getCfg("tree-semantics", sk);
//		// String semantic = Configs.getCfg("tree-semantics", semanticKey);
//		String[][] semanticss = null; 
//		if (sk == null || sk.trim().length() == 0 || semantic == null || semantic.trim().length() == 0) 
//			throw new SQLException(String.format("Sementics not cofigured correctly: sk = %s, semantic = %s", sk, semantic));
//		else
//			semanticss = parseSemantics(semantic);
//		return loadSemantics(jobj, page, pgSize, rootId, connId, semanticss);
//	}

	private static SemanticObject loadSemantics(QueryReq jobj, int page, int pgSize, String rootId,
			String connId, String[][] semanticss) throws IOException, SQLException, SAXException, SsException {

		SemanticObject resp;
		// for robustness
		if (rootId != null && rootId.trim().length() == 0)
			rootId = null;
		
//		resp = querySTree(connId, jobj, rootId, page, pgSize, semanticss);
		return resp;
	}

	
	/**If exprs is not enough, add exprs from semanticss to form up the least collection including:
	 * recId, parentId, fullpath, text.
	 * @param jobj
	 * @param semanticss
	 * @return
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static SemanticObject complementExprs(SemanticObject jreq, String[][] semanticss) {
		JSONArray exprs = (JSONArray) jreq.get("exprs");
		if (exprs == null) {
			exprs = new JSONArray();
			jreq.put("exprs", exprs);
		}
		HashMap<String, String> leastCols = convertLestExprs(semanticss);

		Iterator i = exprs.iterator();
		while (i.hasNext()) {
			SemanticObject expr = (SemanticObject) i.next();
			String colname = (String) expr.get("alais");
			if (colname == null || "".equals(colname.trim()))
				colname = (String) expr.get("expr");
			if (leastCols.containsKey(colname))
				leastCols.remove(colname);
		}
		
		for (String als : leastCols.keySet()) {
			SemanticObject expr = new SemanticObject();
			expr.put("expr", leastCols.get(als));
			expr.put("alais", als);
			expr.put("tabl", semanticss[Ix.tabl][0]);
			exprs.add(expr);
		}

		return jreq;
	}

	@SuppressWarnings("serial")
	private static HashMap<String, String> convertLestExprs(String[][] smtc) {
		return new HashMap<String, String>() {
			{put(smtc[Ix.recId][1] == null ? smtc[Ix.recId][0] : smtc[Ix.recId][1], smtc[Ix.recId][0]);}
			{put(smtc[Ix.parent][1] == null ? smtc[Ix.parent][0] : smtc[Ix.parent][1], smtc[Ix.parent][0]);}
			{put(smtc[Ix.text][1] == null ? smtc[Ix.text][0] : smtc[Ix.text][1], smtc[Ix.text][0]);}
			{put(smtc[Ix.fullpath][1] == null ? smtc[Ix.fullpath][0] : smtc[Ix.fullpath][1], smtc[Ix.fullpath][0]);}
		};
	}
	 */

	/**Set query "order by" by fullpath.
	 * @param semanticss
	 * @return
	@SuppressWarnings("unchecked")
	private static JSONArray semanticOrder(String[][] semanticss) {
		JSONArray orders = new JSONArray();
		SemanticObject order = new SemanticObject();
		// semanticsss: <v>easyui,,id,parentId,text,fullpath</v>
		order.put("tabl", semanticss[Ix.tabl][0]);
		order.put("field", semanticss[Ix.fullpath][1] == null
						? semanticss[Ix.fullpath][0] : semanticss[Ix.fullpath][1]);
		order.put("asc", "asc");
		orders.add(order);

		return orders;
	}

	private static void checkSemantics(SResultset rs, String[][] sm, int ix) throws SQLException {
		try { rs.getString(sm[ix][1] == null ? sm[ix][0] : sm[ix][1]); }
		catch (Exception ex) {
			String cols = "";
			for  (String c : rs.getColnames().keySet()) {
				cols = String.format("%s:\t%s", cols, rs.getColnames().get(c)[1]);;
			}
			String sem = "";
			for (String[] s : sm)
				sem += String.format("%s %s, ", s[0], s[1] == null ? s[0] : s[1]);
			throw new SQLException(String.format("Checking query result against tree semantics failed - can't find %s. semantics:\n%s\ncolumns(count %s):\n%s",
					sm[ix], sem, rs.getColCount(), cols));
		}
	}
	 */


	/**Rebuild subtree starting at root.<br>
	 * Currently only mysql is supported. You may override this method to adapt to other RDBMS.
	 * @param connId
	 * @param rootId
	 * @param semanticss
	 * @param usrInf 
	 * @return
	 * @throws SQLException
	 */
	protected SemanticObject rebuildTree(String connId, String rootId, String[][] semanticss, IUser usrInf) throws SQLException {
		if (Connects.driverType(connId) == dbtype.mysql)
			return BuildMysql.rebuildDbTree(rootId, semanticss, usrInf);
		else throw new SQLException("TODO...");
	}

	protected SemanticObject rebuildForest(String connId, String[][] semanticss, IUser usrInf) throws SQLException {
		if (Connects.driverType(connId) == dbtype.mysql)
			return BuildMysql.rebuildDbForest(semanticss, usrInf);
		else throw new SQLException("TODO...");
	}

	/**A helper class to rebuild tree structure in db table - in case node's parent changing makes subtree fullpath incorrect.<br>
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
		private static SemanticObject rebuildDbTree(String rootId, String[][] sm, IUser dblog) throws SQLException {
			// clear root parentId
			String sql = String.format("update %1$s set %2$s = null where %2$s = %3$s or %2$s = ''",
					sm[Ix.tabl][0], sm[Ix.parent][0], sm[Ix.recId][0]);
			Connects.commit(dblog, sql);

			String pid = null;
			sql = String.format("select %1$s pid from %2$s where %3$s = '%4$s'", 
					sm[Ix.parent][0], sm[Ix.tabl][0], sm[Ix.recId][0], rootId);
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
			return JProtocol.ok(Port.stree, "Updated %s records from root %s", total, rootId);

		}
	
		/**update e_areas
		 * set fullpath = concat(lpad(ifnull(siblingSort, '0'), 2, '0'), ' ', areaId)
		 * where areaId = 'rootId'
		 * @param rootId
		 * @param sm
		 * @return
		 */
		private static String updateRoot(String rootId, String[][] sm) {
			// update e_areas set fullpath = concat(lpad(ifnull(siblingSort, '0'), 2, '0'), ' ', areaId)
			// where areaId = 'rootId'
			return String.format("update %1$s set %2$s = concat(char2rx64(ifnull(%3$s, 0)), ' ', %4$s) " +
					"where %4$s = '%5$s'",
					sm[Ix.tabl][0], sm[Ix.fullpath][0], sm[Ix.sort][0], sm[Ix.recId][0], rootId);
		}
	
		private static String updateSubroot(String rootId, String[][] sm) {
			// update a_domain p0 join a_domain r on p0.parentId = r.domainId
			// set p0.fullpath = concat(r.fullpath, '.', char2rx64(ifnull(p0.sort, 0)), ' ', p0.domainId)
			// where p0.domainId = '0202';
			return String.format("update %1$s p0 join %1$s r on p0.%2$s = r.%3$s " +
					"set p0.%4$s = concat(r.%4$s, '.', char2rx64(ifnull(p0.%5$s, 0)), ' ', p0.%3$s) where p0.%3$s = '%6$s'",
					sm[Ix.tabl][0], sm[Ix.parent][0], sm[Ix.recId][0], sm[Ix.fullpath][0], sm[Ix.sort][0], rootId);
		}
		
		//////////////////////////////// forest /////////////////////////////////////////////////////

		private static SemanticObject rebuildDbForest(String[][] sm, IUser dblog) throws SQLException {
			// clear root parentId
			String sql = String.format("update %1$s set %2$s = null where %2$s = %3$s or %2$s = ''",
					sm[Ix.tabl][0], sm[Ix.parent][0], sm[Ix.recId][0]);
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
			return JProtocol.ok(Port.stree, "Updated records: %s", total);
		}
		
		private static String updateForestRoot(String[][] sm) {
			// update e_areas set fullpath = CONCAT(char2rx64(ifnull(siblingSort, 0)), ' ', areaId) where parentId is null;
			return String.format("update %1$s set %2$s = concat(char2rx64(ifnull(%3$s, 0)), ' ', %4$s) " +
					"where %5$s is null",
					sm[Ix.tabl][0], sm[Ix.fullpath][0], sm[Ix.sort][0], sm[Ix.recId][0], sm[Ix.parent][0]);
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
		private static String updatePi(String rootId, String[][] sm, int pi) {
			// e_areas p0 on p1.parentId = p0.areaId
			String p0 = String.format("%1$s p%2$d on p%3$d.%4$s = p%2$d.%5$s",
					sm[Ix.tabl][0], 0, 1, sm[Ix.parent][0], sm[Ix.recId][0]);
			for (int i = 1; i < pi; i++) {
				// e_areas p1 on p2.parentId = p1.areaId join [e_areas p0 on p1.parentId = p0.areaId]
				p0 = String.format("%1$s p%2$d on p%3$d.%4$s = p%2$d.%5$s join %6$s",
						sm[Ix.tabl][0], i, i + 1, sm[Ix.parent][0], sm[Ix.recId][0], p0);
			}
			p0 = String.format("update %1$s p%2$d join %3$s %4$s %5$s",
					sm[Ix.tabl][0], pi, p0, setPi(sm, pi),
					rootId == null ? String.format("where p0.%1$s is null", sm[Ix.parent][0]) // where p0.parentId is null
								   : String.format("where p0.%1$s = '%2$s'", sm[Ix.recId][0], rootId)); // where p0.areaId = 'rootId'
			return p0;
		}

		private static String setPi(String[][] sm, int pi) {
			// set p2.fullpath = concat(p1.fullpath, ' ', char2rx64(ifnull(p2.siblingSort, 0)), '#', p2.areaId)
			return String.format("set p%1$d.%2$s = concat(p%3$d.%2$s, '.', char2rx64(ifnull(p%1$d.%4$s, 0)), ' ', p%1$d.%5$s)",
					pi, sm[Ix.fullpath][0], pi - 1, sm[Ix.sort][0], sm[Ix.recId][0]);
		}
	}
}
