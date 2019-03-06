package io.odysz.semantic.jserv.R;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
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

	protected int page;
	protected int pgsize;

	public QueryReq(JMessage<? extends JBody> parent) {
		super(parent);
		a = "r";
	}

	public QueryReq(JMessage<? extends JBody> parent, String conn, String fromTbl, String... alias) {
		super(parent);
		a = "r";

		mtabl = fromTbl;
		mAlias = alias == null || alias.length == 0 ? null : alias[0];
		
		this.page = -1;
		this.pgsize = 0;
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
		String[] j = new String[Ix.joinSize];
		j[Ix.joinTabl] = with;
		j[Ix.joinAlias] = as;
		j[Ix.joinType] = t;
		j[Ix.joinOnCond] = on;
		return j(j);
	}
	
	private QueryReq j(String[] join) {
		joins.add(join);
		return this;
	}

	public QueryReq expr(String expr, String alias, String... tabl) {
		if (exprs == null)
			exprs = new ArrayList<String[]>();
		String[] exp = new String[Ix.exprSize];
		exp[Ix.exprExpr] = expr;
		exp[Ix.exprAlais] = alias;
		exp[Ix.exprTabl] = tabl == null || tabl.length == 0 ? null : tabl[0];
		exprs.add(exp);
		return this;
	}
	
	public QueryReq where(String oper, String lop, String rop) {
		if (where == null)
			where = new ArrayList<String[]>();

		String[] predicate = new String[Ix.predicateSize];
		predicate[Ix.predicateOper] = oper;
		predicate[Ix.predicateL] = lop;
		predicate[Ix.predicateR] = rop;

		where.add(predicate);
		return this;
	}

	/**<p>Create a qeury request body item, for joining etc.,
	 * and can be serialized into json by {@link #toJson(JsonWriter)}.</p>
	 * <p>Client side helper, don't confused with {@link Query}.</p>
	 * @param conn
	 * @param parent
	 * @param ssInf
	 * @param from 
	 * @param as 
	 * @return query request
	 */
	public static QueryReq formatReq(String conn, JMessage<QueryReq> parent, SemanticObject ssInf, String from, String as) {
		QueryReq bdItem = new QueryReq(parent, conn, from, as);
		return bdItem;
	}

	@Override
	public void toJson(JsonWriter writer) throws IOException {
		writer.beginObject();
		writer.name("a").value(a);
		writer.name("mtabl").value(mtabl);
		writer.name("mAlias").value(mAlias);
		writer.name("page").value(page);
		writer.name("pgSize").value(pgsize);

		try {
			if (exprs != null) {
				writer.name("exprs");
				JHelper.writeLst(writer, exprs);
			}
			else 
				writer.name("exprs").value("*");

			if (joins != null) {
				writer.name("joins");
				JHelper.writeLst(writer, joins);
			}
			if (where != null) {
				writer.name("where");
				JHelper.writeLst(writer, where);
			}
			// TODO orders, groups ...
		} catch (SQLException e) {
			e.printStackTrace();	
		}
		writer.endObject();
	}

	@Override
	public void fromJson(JsonReader reader) throws IOException, SemanticException {
		JsonToken token = reader.peek();
		if (token == JsonToken.BEGIN_OBJECT) {
			reader.beginObject();
			while (token != JsonToken.END_OBJECT) {
				String name = reader.nextName();
				if ("a".equals(name))
					a = nextString(reader);
				else if ("page".equals(name))
					page = reader.nextInt();
				else if ("pgSize".equals(name))
					pgsize = reader.nextInt();
				else if ("mtabl".equals(name))
					mtabl = nextString(reader);
				else if ("mAlias".equals(name))
					mAlias = nextString(reader);
				else if ("exprs".equals(name)) {
					if (reader.peek() == JsonToken.BEGIN_ARRAY)
						exprs = JHelper.readLstStrs(reader);
					else reader.nextString(); // skip "*"
				}
				else if ("joins".equals(name))
					joins = JHelper.readLstStrs(reader);
				else if ("where".equals(name))
					where = JHelper.readLstStrs(reader);
				// TODO ...
				token = reader.peek();
			}
			reader.endObject();
		}
	}

	public static String nextString(JsonReader reader) throws IOException {
		if (reader.peek() == JsonToken.NULL) {
			reader.nextNull();
			return null;
		}
		else return reader.nextString(); 
	}

}
