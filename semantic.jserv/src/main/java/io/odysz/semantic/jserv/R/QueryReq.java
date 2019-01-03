package io.odysz.semantic.jserv.R;

import java.io.IOException;
import java.util.ArrayList;

import com.google.gson.stream.JsonWriter;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantics.SemanticObject;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Query.Ix;

/**<pre>
query-obj: { tabl: tabl-obj,
			 joins: [join-obj],
             exprs: [expr-obj],
             where: [cond-obj],
             orders: [order-obj],
             group: [group-obj]
           }
     - tabl-obj: {tabl: "table-1", as: "t_alais"}
     - join-obj: [{t: "j/r/l", tabl: "table-1", as: "t_alais", on: conds}]
           - conds: [cond-obj]
            	cond-obj: {(main-table | alais .)left-col-val op (table-1 | alias2 .)right-col-val}
           				- op: '=' | '&lt;=' | '&gt;=' ...
     - expr-obj: {tabl: "b_articles/t_alais", alais: "recId", expr: "recId"}
     - order-obj: {tabl: "b_articles", field: "pubDate", asc: "true"}
     - group-obj: {tabl: "b_articles/t_alais", expr: "recId" }

respons:
{total: num, rows[]}
or
{code: "failed", msg: msg}</pre>
 * @author ody
 *
 */
public class QueryReq extends JBody {

	/**Main table */
	String mtabl;
	/**Main table alias*/
	String mAlias;
	
	/**<pre>joins: [join-obj],
     - join-obj: [{t: "j/r/l", tabl: "table-1", as: "t_alais", on: conds}]
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
	ArrayList<String[]> groups;
	private int page;
	private int pgsize;

//	protected QueryReq setUserAct(String funcId, String funcName, String url, String cmd) {
//		parent.header().usrAct(new String[] {funcId, funcName, url, cmd});
//		return this;
//	}
	public QueryReq(JMessage<? extends JBody> parent) {
		super(parent);
		this.page = 0;
		this.pgsize = 20;
	}

	public void page(int page, int size) {
		this.page = page;
		this.pgsize = size;
	}

	public QueryReq j(String with, String as, String on) {
		return j("j", with, as, on);
	}

	public QueryReq l(String with, String as, String on) {
		return j("l", with, as, on);
	}

	public QueryReq r(String with, String as, String on) {
		return j("r", with, as, on);
	}

	public QueryReq j(ArrayList<String[]> joins) {
		if (joins != null)
			for (String[] join : joins) 
				j(join);
		return this;
	}

	public QueryReq j(String t, String with, String as, String on) {
		if (joins == null)
			joins = new ArrayList<String[]>();
		String[] j = new String[Ix.JoinSize];
		j[Ix.JoinTabl] = with;
		j[Ix.JoinAlias] = as;
		j[Ix.JoinType] = t;
		j[Ix.JoinOnCond] = on;
		return j(j);
	}
	
	private QueryReq j(String[] join) {
		joins.add(join);
		return this;
	}

	public void expr(String expr, String alais, String[] tabl) {
		if (exprs == null)
			exprs = new ArrayList<String[]>();
		String[] exp = new String[Ix.ExprSize];
		exp[Ix.ExprExpr] = expr;
		exp[Ix.ExprAlais] = expr;
		exp[Ix.ExprTabl] = tabl == null || tabl.length == 0 ? null : tabl[0];
		exprs.add(exp);
	}

	/**<p>Create a qeury request body item, for joining etc.,
	 * and can be serialized into json by {@link #toJson(JsonWriter)}.</p>
	 * <p>Client side helper, don't confused with {@link Query}.</p>
	 * @param jmsg
	 * @param ssInf
	 * @return
	 */
	public static QueryReq formatReq(JMessage<QueryReq> jmsg, SemanticObject ssInf) {
		QueryReq bdItem = new QueryReq(jmsg);
		return bdItem;
	}

	@Override
	public void toJson(JsonWriter writer) throws IOException {
		writer.beginObject();
		writer.name("a").value(a);
		writer.name("mtabl").value(mtabl);
		writer.name("mAlias").value(mAlias);

		if (joins != null) {
			writer.name("joins");
			writer.beginArray();
			for (String[] join : joins) {
				writer.beginObject();
				for (int i = 0; i < join.length; i++) {
					if (join[i] == null)
						writer.value("");
					else
						writer.value(join[i]);
				}
			}
			writer.endArray();
		}
		// TODO exprs ...
		writer.endObject();
	}
}
