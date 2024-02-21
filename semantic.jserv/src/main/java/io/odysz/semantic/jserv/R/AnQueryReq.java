package io.odysz.semantic.jserv.R;

import java.util.ArrayList;

import io.odysz.semantic.CRUD;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.transact.sql.Query.Ix;

/**Query Request Body Item.<br>
 * Included are information of RDBMS query information,
 * table, joins, conditions, groups, orders, etc.
 * @author odys-z@github.com
 */
public class AnQueryReq extends AnsonBody {

	/**Main table */
	String mtabl;
	/**Main table alias*/
	String mAlias;
	
	/**<pre>joins: [join-obj],
     - join-obj: [{t: "j/R/l", tabl: "table-1", as: "t_alais", on: conds}]
           - conds: [cond-obj]
            	cond-obj: {(main-table | alais.)left-col-val op (table-1 | alias2 .)right-col-val}
           				- op: '=' | '&lt;=' | '&gt;=' ...</pre>
	 */
	ArrayList<String[]> joins;

	/**exprs: [expr-obj],
	 * expr-obj: {tabl: "b_articles/t_alais", alais: "recId", expr: "recId"}
	 *  */
	ArrayList<String[]> exprs;
	
	/**where: [cond-obj], see {@link #joins}for cond-obj.*/
	ArrayList<String[]> where;
	
	/**orders: [order-obj],
     - order-obj: {tabl: "b_articles", field: "pubDate", asc: "true"} */
	ArrayList<String[]> orders;
	
	/**group: [group-obj]
     - group-obj: {tabl: "b_articles/t_alais", expr: "recId" } */
	String[] groups;

	protected int page;
	protected int pgsize;

	String[] limt;

	ArrayList<String[]> havings;

	public AnQueryReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
		a = CRUD.R;
	}

	public AnQueryReq() {
		super(null, null);
		a = CRUD.R;
	}

	public AnQueryReq(AnsonMsg<? extends AnsonBody> parent, String uri, String fromTbl, String... alias) {
		super(parent, uri);
		a = CRUD.R;

		mtabl = fromTbl;
		mAlias = alias == null || alias.length == 0 ? null : alias[0];
		
		this.page = -1;
		this.pgsize = 0;
	}

	public AnQueryReq page(int page, int size) {
		this.page = page;
		this.pgsize = size;
		return this;
	}

	public int size() { return pgsize; }

	public int page() { return page; }

	public AnQueryReq j(String with, String as, String on) {
		return j("j", with, as, on);
	}

	public AnQueryReq l(String with, String as, String on) {
		return j("l", with, as, on);
	}

	public AnQueryReq r(String with, String as, String on) {
		return j("R", with, as, on);
	}

	public AnQueryReq j(ArrayList<String[]> joins) {
		if (joins != null)
			for (String[] join : joins) 
				j(join);
		return this;
	}

	public AnQueryReq j(String t, String with, String as, String on) {
		if (joins == null)
			joins = new ArrayList<String[]>();
		String[] j = new String[Ix.joinSize];
		j[Ix.joinTabl] = with;
		j[Ix.joinAlias] = as;
		j[Ix.joinType] = t;
		j[Ix.joinOnCond] = on;
		return j(j);
	}
	
	private AnQueryReq j(String[] join) {
		joins.add(join);
		return this;
	}

	public AnQueryReq expr(String expr, String alias, String... tabl) {
		if (exprs == null)
			exprs = new ArrayList<String[]>();
		String[] exp = new String[Ix.exprSize];
		exp[Ix.exprExpr] = expr;
		exp[Ix.exprAlais] = alias;
		exp[Ix.exprTabl] = tabl == null || tabl.length == 0 ? null : tabl[0];
		exprs.add(exp);
		return this;
	}
	
	public AnQueryReq where(String oper, String lop, String rop) {
		if (where == null)
			where = new ArrayList<String[]>();

		String[] predicate = new String[Ix.predicateSize];
		predicate[Ix.predicateOper] = oper;
		predicate[Ix.predicateL] = lop;
		predicate[Ix.predicateR] = rop;

		where.add(predicate);
		return this;
	}

	public AnQueryReq whereEq(String lop, String constv) {
		return where("=", lop, "'" + constv + "'");
	}

	public AnQueryReq orderby(String col, boolean... asc) {
		if (orders == null)
			orders = new ArrayList<String[]>();
		orders.add(new String[] {col,
				String.valueOf(asc == null || asc.length == 0 ? "asc"
						: asc[0] ? "asc" : "desc")});
		return this;
	}

	/**<p>Create a qeury request body item, for joining etc.</p>
	 * <p>This is a client side helper, don't confused with {@link io.odysz.transact.sql.Query Query}.</p>
	 * @param conn
	 * @param parent
	 * @param from 
	 * @param as 
	 * @return query request
	 */
	public static AnQueryReq formatReq(String conn, AnsonMsg<AnQueryReq> parent,
				String from, String... as) {
		AnQueryReq bdItem = new AnQueryReq(parent, conn, from,
				as == null || as.length == 0 ? null : as[0]);
		return bdItem;
	}

	public AnQueryReq having(String oper, String lop, String rop) {
		if (where == null)
			where = new ArrayList<String[]>();

		String[] predicate = new String[Ix.predicateSize];
		predicate[Ix.predicateOper] = oper;
		predicate[Ix.predicateL] = lop;
		predicate[Ix.predicateR] = rop;

		where.add(predicate);
		return this;
	}
}
