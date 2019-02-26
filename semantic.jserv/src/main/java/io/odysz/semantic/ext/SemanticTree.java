package io.odysz.semantic.ext;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;

import io.odysz.common.Configs;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DA.DatasetCfg;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.R.QueryReq;
import io.odysz.semantic.jserv.R.SQuery;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.transact.sql.Query;

/**
 * Servlet implementing Semantic Tree<br>
 * Querying like query.jserv, return tree data with node configured with semantics in config.xml<br>
 * Using configured sql like TreeGrid is enough for trees like menu, but tree grid also needing joining and condition query, that' not enough.
 * t:<br>
 * reforest: re-build tree/forest structure of the taget table (specified in semantics, paramter sk);<br>
 * retree: re-build tree from root;<br>
 * sql: load tree by configured sql (t = ds, sk = sql-key);<br>
 * [any]: load semantics tree (sk)
 */
@WebServlet(description = "Abstract Tree Data Service", urlPatterns = { "/s-tree.jserv" })
public class SemanticTree extends SQuery {
	private static final long serialVersionUID = 1L;

	private static final int ixJsframe = 0;
	public static class IxVue {
		/** the boolean field */
		public static final int chked = 1;
		public static final int tabl = 2;
		public static final int recId = 3;
		public static final int parent = 4;
		public static final int text = 5;
		public static final int fullpath = 6;
		public static final int sort = 7;
		public static final int pageByServer = 8;
		public static final int count = 9;
	}
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (ServFlags.extStree) System.out.println("stree.serv get ------");
		jsonResp(request, response);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (ServFlags.extStree) System.out.println("stree.serv post ======");
		jsonResp(request, response);
	}

	protected void jsonResp(HttpServletRequest request, HttpServletResponse response) throws IOException {
		// response.setContentType("text/html;charset=UTF-8");
		JsonWriter writer = Json.createWriter(response.getOutputStream());
		try {
			JsonObject resp;
			JSONObject[] payload = ServManager.parseReq(request);
			DbLog dblog = null; 

			String t = request.getParameter("t");

			if ("reforest".equals(t)) 
				// try get dblog
				try { dblog = IrSession.check(payload[0]); } catch(Exception ex) {}
			else
				dblog = IrSession.check(payload[0]);

			if (t == null)
				throw new SQLException("s-tree.serv usage: t=load/reforest/retree&rootId=...");
			String connId = request.getParameter("conn");

			// find tree semantics
			String semanticKey = request.getParameter("sk");
			if (semanticKey == null || semanticKey.trim().length() == 0)
				throw new SQLException("Sementic key must present for s-tree.serv.");
			String semantic = Configs.getCfg("tree-semantics", semanticKey);
			String[][] semanticss = null; 
			if (!"sql".equals(t)) {
				if (semantic == null || semantic.trim().length() == 0)
					throw new SQLException(String.format("Sementics not cofigured correctly: \n\t%s\n\t%s", semanticKey, semantic));
				semanticss = parseSemantics(semantic);
			}
			if (connId == null || connId.trim().length() == 0)
				connId = DA.getDefltConnId();

			// branches
			// http://127.0.0.1:8080/ifire/s-tree.serv?sk=easyuitree-area&t=reforest
			if ("reforest".equals(t))
				resp = rebuildForest(connId, semanticss, dblog);
			// http://127.0.0.1:8080/ifire/s-tree.serv?sk=easyuitree-area&t=retree&root=002
			else if ("retree".equals(t)) {
				String root = request.getParameter("root");
				resp = rebuildTree(connId, root, semanticss, dblog);
			}
			else {
				// sql or any
				int page = 0;
				int size = 20;
				try {page = Integer.valueOf(request.getParameter("page"));
				}catch (Exception e) {}
				try {size = Integer.valueOf(request.getParameter("size"));
				}catch (Exception e) {}
				
				if ("sql".equals(t)) {
					// sql
					String[] args = request.getParameterValues("args");
					resp = loadConfigArgs(connId, semanticKey, args, page, size);
				}
				else {
					// any
					String rootId = request.getParameter("root");
					resp = loadSemantics(payload[1], page, size, rootId, connId, semanticss);
				}
			}

			writer.write(resp);
			writer.close();
			response.flushBuffer();
		} catch (IrSessionException ssex) {
			if (writer != null)
				writer.write(JsonHelper.err(IrSession.ERR_CHK, ssex.getMessage()));
		} catch (SQLException e) {
			e.printStackTrace();
			if (writer != null)
				writer.write(JsonHelper.Err(e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			if (writer != null)
				writer.write(JsonHelper.Err(e.getMessage()));
		} finally {
			if (writer != null)
				writer.close();
		}
	}

	static JsonObject loadConfigArgs(String connId, String sqlkey, String[] args,
			int page, int size) throws SQLException {
		String[][] semanticss = parseSemantics(DatasetCfg.getStree(connId, sqlkey));
		String sql = DatasetCfg.getSql(connId, sqlkey, args);
		// resp = loadSemantics(payload, page, size, rootId, connId, semanticss);
		if (page >= 0 && size >= 0) {
			sql = DA.pagingSql(connId, sql, page, size);
		}

		String s1 = String.format("select count(*) as total from (%s) t", sql);
		SResultset rs0 = DA.select(s1);
		rs0.beforeFirst().next();
		int total = rs0.getInt("total");

		SResultset rs = DatasetCfg.mapRs(connId, sqlkey, DA.select(sql));
		JsonArray jforest = buildRs (rs, semanticss);
		JsonObjectBuilder block = Json.createObjectBuilder();
		block.add("total", total);
		block.add("rows", jforest);
		return block.build();
	}

	/**parse "easyui,,e_areas,areaId id,parentId,areaName text,fullpath,siblingSort,false" to 2d array.
	 * @param semantic
	 * @return [0:[easyui, null], 1:[checked, null], 2:[tabl, null], 3:[areaId, id], ...]
	 */
	private static String[][] parseSemantics(String semantic) {
		if (semantic == null) return null;
		String[][] sm = new String[countIx][];
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

	public static JsonObject loadSemantics(JSONObject jobj, int page, int pgSize, String rootId,
			String connId, String sk) throws IOException, SQLException, IrSessionException, SAXException {
		String semantic = Configs.getCfg("tree-semantics", sk);
		// String semantic = Configs.getCfg("tree-semantics", semanticKey);
		String[][] semanticss = null; 
		if (sk == null || sk.trim().length() == 0 || semantic == null || semantic.trim().length() == 0) 
			throw new SQLException(String.format("Sementics not cofigured correctly: sk = %s, semantic = %s", sk, semantic));
		else
			semanticss = parseSemantics(semantic);
		return loadSemantics(jobj, page, pgSize, rootId, connId, semanticss);
	}

	private static JsonObject loadSemantics(JMessage<QueryReq> jobj, int page, int pgSize, String rootId,
			String connId, String[][] semanticss) throws IOException, SQLException, SAXException {

		JsonObject resp;
		// for robustness
		if (rootId != null && rootId.trim().length() == 0)
			rootId = null;
		
		resp = querySTree(connId, jobj, rootId, page, pgSize, semanticss);
		return resp;
	}

	/**Semantics Extension: filter area root: if rootId == null, get user org's root area
	 * @param jobj
	 * @param rootId
	 * @return
	private String smExtRootArea(JSONObject jobj, String rootId) {
		//没有root参数从session中读取所在区域 - default case: load areas only for that of user org
		if( rootId == null) {
			// rootId=IFireSingleton.getAreaId();
			JSONObject jheader = (JSONObject) jobj.get("header");
			IfireUser usr = (IfireUser) IrSession.getUser(jheader);
			return usr.getRootAreaId();
		}
		return rootId;
	}
	 */

	/**Query tree data, construct according to semanticss. If rootId != null, will query a subtree.
	 * @param connId
	 * @param t
	 * @param jobj
	 * @param rootId
	 * @param page
	 * @param size
	 * @param semanticss
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 * @throws IrSessionException
	 * @throws SAXException 
	 */
	@SuppressWarnings("unchecked")
	private static JsonObject querySTree(String connId, JMessage<QueryReq> jobj, String rootId,
		int page, int size, String[][] semanticss) throws SQLException, IOException, SsException, SAXException {
		// Ignore "order by" by client, override with semantics
		JSONArray orders = semanticOrder(semanticss);
		jobj.put("orders", orders);
		
		jobj = complementExprs(jobj, semanticss);

		String[] sql;
		if (rootId != null && rootId.trim().length() > 0) {
			// add cond: fullpath llike root-path
			// tabls = [{t, tabl, as, on}, ...]
			// conds = [{field, logic, tabl, v}, ...]
			JSONObject like = subnodeIds(semanticss, rootId);
			JSONArray conds = (JSONArray)jobj.get("conds");
			if (conds == null) {
				conds = new JSONArray();
				jobj.put("conds", conds);
			}
			conds.add(like);

			sql = Query.buildSelect(jobj, semanticss[ixTabl][0], connId);
		}
		else
			sql = Query.buildSelect(jobj, semanticss[ixTabl][0], connId);


		SResultset rs0 = DA.select(sql[0]);
		rs0.beforeFirst().next();
		int total = rs0.getInt("total");

		if (ispaging(semanticss, page, size))
			sql[1] = DA.pagingSql(connId, sql[1], page, size);
		SResultset rs = DA.select(sql[1]);
		rs.beforeFirst();

		// 2018-01-31 For Oracle portion: convert uppercase to bump-case according to mappings(dc.xml) and request object(alais)
		if (DA.isOracle(connId))
			JsonHelper.renameRsCols(jobj, rs, connId);

		JsonArray jforest = buildRs (rs, semanticss);
		JsonObjectBuilder block = Json.createObjectBuilder();
		block.add("total", total);
		block.add("rows", jforest);
		JsonObject resp = block.build();
		return resp;
	}
	
	private static JsonArray buildRs (SResultset rs, String[][] semanticss) throws SQLException {
		// build the tree/forest
		JsonArrayBuilder forest = Json.createArrayBuilder();
		rs.beforeFirst();
		while (rs.next()) {
			JsonObjectBuilder root  = formatSemanticNode(semanticss, rs);

			// checkSemantics(rs, semanticss, ixRecId);
			JsonArrayBuilder children = buildSubTree(semanticss, root,
					rs.getString(semanticss[ixRecId][1] == null ? semanticss[ixRecId][0] : semanticss[ixRecId][1]), rs);
			if (children.build().size() > 0)
				root.add("children", children);
			forest.add(root);
		}

		JsonArray jforest = forest.build();
		return jforest;
	}

	/** return true if: page >= 0, size >=0, semantics[ixPageByServer][0] == true<br>
	 * default semantics[ixPageByServer][0] == true
	 * @param sm
	 * @param page
	 * @param size
	 * @return
	 */
	private static boolean ispaging(String[][] sm, int page, int size) {
		if (page >= 0 && size >= 0
				&& sm != null && sm.length > ixPageByServer
				&& sm[ixPageByServer] != null && sm[ixPageByServer][0] != null) {
			String flg = sm[ixPageByServer][0].trim().toLowerCase();
			if ("false".equals(flg) || "f".equals(flg) || "0".equals(flg) || "n".equals(flg) || "no".equals(flg))
				return false;
		}
		return true;
	}

	/**If exprs is not enough, add exprs from semanticss to form up the least collection including:
	 * recId, parentId, fullpath, text.
	 * @param jobj
	 * @param semanticss
	 * @return
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static JSONObject complementExprs(JSONObject jreq, String[][] semanticss) {
		JSONArray exprs = (JSONArray) jreq.get("exprs");
		if (exprs == null) {
			exprs = new JSONArray();
			jreq.put("exprs", exprs);
		}
		HashMap<String, String> leastCols = convertLestExprs(semanticss);

		Iterator i = exprs.iterator();
		while (i.hasNext()) {
			JSONObject expr = (JSONObject) i.next();
			String colname = (String) expr.get("alais");
			if (colname == null || "".equals(colname.trim()))
				colname = (String) expr.get("expr");
			if (leastCols.containsKey(colname))
				leastCols.remove(colname);
		}
		
		for (String als : leastCols.keySet()) {
			JSONObject expr = new JSONObject();
			expr.put("expr", leastCols.get(als));
			expr.put("alais", als);
			expr.put("tabl", semanticss[ixTabl][0]);
			exprs.add(expr);
		}

		return jreq;
	}

	@SuppressWarnings("serial")
	private static HashMap<String, String> convertLestExprs(String[][] sm) {
		return new HashMap<String, String>() {
			{put(sm[ixRecId][1] == null ? sm[ixRecId][0] : sm[ixRecId][1], sm[ixRecId][0]);}
			{put(sm[ixParent][1] == null ? sm[ixParent][0] : sm[ixParent][1], sm[ixParent][0]);}
			{put(sm[ixText][1] == null ? sm[ixText][0] : sm[ixText][1], sm[ixText][0]);}
			{put(sm[ixFullpath][1] == null ? sm[ixFullpath][0] : sm[ixFullpath][1], sm[ixFullpath][0]);}
		};
	}


	/**Set query "order by" by fullpath.
	 * @param semanticss
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private static JSONArray semanticOrder(String[][] semanticss) {
		JSONArray orders = new JSONArray();
		JSONObject order = new JSONObject();
		// semanticsss: <v>easyui,,id,parentId,text,fullpath</v>
		order.put("tabl", semanticss[ixTabl][0]);
		order.put("field", semanticss[ixFullpath][1] == null
						? semanticss[ixFullpath][0] : semanticss[ixFullpath][1]);
		order.put("asc", "asc");
		orders.add(order);

		return orders;
	}

	/**Format jobj.conds for "fullpath like 'parent-path.%'"
	 * @param semanticss
	 * @param rootId
	 * @return {field, logic: "like", tabl, v: "fullpath-root"}
	 * @throws SQLException
	 */
	@SuppressWarnings("unchecked")
	private static JSONObject subnodeIds(String[][] semanticss, String rootId) throws SQLException {
		String sql = String.format("select %s from %s where %s = '%s'",
				semanticss[ixFullpath][0], semanticss[ixTabl][0], semanticss[ixRecId][0], rootId);
		SResultset rs = DA.select(sql);
		if (rs.beforeFirst().next()) {
			JSONObject j = new JSONObject();
			j.put("field", semanticss[ixFullpath][0]);
			j.put("logic", "=%");
			j.put("tabl", semanticss[ixTabl][0]);
			j.put("v", rs.getString(1));
			return j;
		}
		else{
			if (IrSingleton.debug)
				System.err.println("not rootId tree");
			return null;
		}
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

	private static JsonArrayBuilder buildSubTree(String[][] sm,
			JsonObjectBuilder parentNode, String parentId, SResultset rs) throws SQLException {
		JsonArrayBuilder childrenArray = Json.createArrayBuilder();
//		int cols = rs.getColCount();
		while (rs.next()) {
			checkSemantics(rs, sm, ixParent);
			String currentParentID = rs.getString(sm[ixParent][1] == null ? sm[ixParent][0] : sm[ixParent][1]);
			if (currentParentID == null || currentParentID.trim().length() == 0) {
				// new tree root
				rs.previous();
				if (childrenArray.build().size() > 0) // FIXME build each time?
					parentNode.add("children", childrenArray);
				return childrenArray;
//				throw new SQLException(
//						"Parent ID shouldn't be null in sub tree. One of such data comes from disordered resultset. Data for tree binding must ordered in deep first travels order - must order by fullpath when select from db.");
			}
			// HERE! ending adding children
			if (!currentParentID.trim().equals(parentId.trim())) {
				rs.previous();
				if (childrenArray.build().size() > 0)
					parentNode.add("children", childrenArray);
				return childrenArray;
			}

//			JsonObjectBuilder child = Json.createObjectBuilder();
//			for (int i = 1; i <= cols; i++) {
//				String v = rs.getString(i);
//				if (v != null)
//					child.add(rs.getColumnName(i), v);
//			}
			JsonObjectBuilder child = formatSemanticNode(sm, rs);

			JsonArrayBuilder subOrg = buildSubTree(sm, child,
					rs.getString(sm[ixRecId][1] == null ? sm[ixRecId][0] : sm[ixRecId][1]), rs);

			if (subOrg.build().size() > 0)
				child.add("children", subOrg);
			childrenArray.add(child);
		}
		return childrenArray;
	}

	/**Create a JsonObjectBuilder for easyui tree node with current rs row.
	 * @param sm
	 * @param rs
	 * @return
	 * @throws SQLException
	 */
	private static JsonObjectBuilder formatSemanticNode(String[][] sm, SResultset rs) throws SQLException {
		JsonObjectBuilder node = Json.createObjectBuilder();
		if(!"easyui".equals(sm[ixJsframe][0]))
			throw new SQLException("js frame configuration not supported: " + sm[ixJsframe][0]);

		for (int i = 1;  i <= rs.getColCount(); i++) {
			String v = rs.getString(i);
			String col = rs.getColumnName(i);
			if (v != null)
				if (col.equals(sm[ixChked][0]))
					node.add(col, rs.getBoolean(i));
				else
					node.add(col, v);
		}
		return node;
	}

	/**Rebuild subtree starting at root.
	 * @param connId
	 * @param rootId
	 * @param semanticss
	 * @param dblog 
	 * @return
	 * @throws SQLException
	 */
	private JsonObject rebuildTree(String connId, String rootId, String[][] semanticss, DbLog dblog) throws SQLException {
		if (DA.getConnType(connId).equals("mysql"))
			return BuildMysql.rebuildDbTree(rootId, semanticss, dblog);
		else throw new SQLException("TODO...");
	}

	private JsonObject rebuildForest(String connId, String[][] semanticss, DbLog dblog) throws SQLException {
		if (DA.getConnType(connId).equals("mysql"))
			return BuildMysql.rebuildDbForest(semanticss, dblog);
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
		private static JsonObject rebuildDbTree(String rootId, String[][] sm, DbLog dblog) throws SQLException {
			// clear root parentId
			String sql = String.format("update %1$s set %2$s = null where %2$s = %3$s or %2$s = ''",
					sm[ixTabl][0], sm[ixParent][0], sm[ixRecId][0]);
			DA.commit(dblog, sql);

			String pid = null;
			sql = String.format("select %1$s pid from %2$s where %3$s = '%4$s'", 
					sm[ixParent][0], sm[ixTabl][0], sm[ixRecId][0], rootId);
			SResultset rs = DA.select(sql);
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
			int[] i = DA.commit(dblog, updatei);
			while (i != null && i.length > 0 && i[0] > 0) {
				total += i[0];
				updatei = updatePi(rootId, sm, level++);
				i = DA.commit(dblog, updatei);
			}
			return JsonHelper.OK(String.format("Updated %d records from root %s", total, rootId));
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
					sm[ixTabl][0], sm[ixFullpath][0], sm[ixSibling][0], sm[ixRecId][0], rootId);
		}
	
		private static String updateSubroot(String rootId, String[][] sm) {
			// update a_domain p0 join a_domain r on p0.parentId = r.domainId
			// set p0.fullpath = concat(r.fullpath, '.', char2rx64(ifnull(p0.sort, 0)), ' ', p0.domainId)
			// where p0.domainId = '0202';
			return String.format("update %1$s p0 join %1$s r on p0.%2$s = r.%3$s " +
					"set p0.%4$s = concat(r.%4$s, '.', char2rx64(ifnull(p0.%5$s, 0)), ' ', p0.%3$s) where p0.%3$s = '%6$s'",
					sm[ixTabl][0], sm[ixParent][0], sm[ixRecId][0], sm[ixFullpath][0], sm[ixSibling][0], rootId);
		}
		
		//////////////////////////////// forest /////////////////////////////////////////////////////

		private static JsonObject rebuildDbForest(String[][] sm, DbLog dblog) throws SQLException {
			// clear root parentId
			String sql = String.format("update %1$s set %2$s = null where %2$s = %3$s or %2$s = ''",
					sm[ixTabl][0], sm[ixParent][0], sm[ixRecId][0]);
			DA.commit(dblog, sql);

			String updatei = updateForestRoot(sm);

			int total = 0;
			int level = 1;
			int[] i = DA.commit(dblog, updatei);
			while (i != null && i.length > 0 && i[0] > 0) {
				total += i[0];
				updatei = updatePi(null, sm, level++);
				i = DA.commit(dblog, updatei);
			}
			return JsonHelper.OK("Updated records: " + total);
		}
		
		private static String updateForestRoot(String[][] sm) {
			// update e_areas set fullpath = CONCAT(char2rx64(ifnull(siblingSort, 0)), ' ', areaId) where parentId is null;
			return String.format("update %1$s set %2$s = concat(char2rx64(ifnull(%3$s, 0)), ' ', %4$s) " +
					"where %5$s is null",
					sm[ixTabl][0], sm[ixFullpath][0], sm[ixSibling][0], sm[ixRecId][0], sm[ixParent][0]);
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
					sm[ixTabl][0], 0, 1, sm[ixParent][0], sm[ixRecId][0]);
			for (int i = 1; i < pi; i++) {
				// e_areas p1 on p2.parentId = p1.areaId join [e_areas p0 on p1.parentId = p0.areaId]
				p0 = String.format("%1$s p%2$d on p%3$d.%4$s = p%2$d.%5$s join %6$s",
						sm[ixTabl][0], i, i + 1, sm[ixParent][0], sm[ixRecId][0], p0);
			}
			p0 = String.format("update %1$s p%2$d join %3$s %4$s %5$s",
					sm[ixTabl][0], pi, p0, setPi(sm, pi),
					rootId == null ? String.format("where p0.%1$s is null", sm[ixParent][0]) // where p0.parentId is null
								   : String.format("where p0.%1$s = '%2$s'", sm[ixRecId][0], rootId)); // where p0.areaId = 'rootId'
			return p0;
		}

		private static String setPi(String[][] sm, int pi) {
			// set p2.fullpath = concat(p1.fullpath, ' ', char2rx64(ifnull(p2.siblingSort, 0)), '#', p2.areaId)
			return String.format("set p%1$d.%2$s = concat(p%3$d.%2$s, '.', char2rx64(ifnull(p%1$d.%4$s, 0)), ' ', p%1$d.%5$s)",
					pi, sm[ixFullpath][0], pi - 1, sm[ixSibling][0], sm[ixRecId][0]);
		}
	}
}
