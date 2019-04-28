package io.odysz.semantic.jserv.R;

import java.io.IOException;
import java.util.ArrayList;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JProtocol.CRUD;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Query.Ix;

/**Query Request Body Item.<br>
 * @author odys-z@github.com
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

	public QueryReq(JMessage<? extends JBody> parent, String conn) {
		super(parent, conn);
		a = "r";
	}

	public QueryReq(JMessage<? extends JBody> parent, String conn, String fromTbl, String... alias) {
		super(parent, conn);
		a = CRUD.R;

		mtabl = fromTbl;
		mAlias = alias == null || alias.length == 0 ? null : alias[0];
		
		this.page = -1;
		this.pgsize = 0;
	}

	public QueryReq page(int page, int size) {
		this.page = page;
		this.pgsize = size;
		return this;
	}

	public int size() { return pgsize; }

	public int page() { return page; }

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

	public QueryReq orderby(String col, boolean... asc) {
		if (orders == null)
			orders = new ArrayList<String[]>();
		orders.add(new String[] {col,
				String.valueOf(asc == null || asc.length == 0 ? "asc"
						: asc[0] ? "asc" : "desc")});
		return this;
	}

	/**<p>Create a qeury request body item, for joining etc.,
	 * and can be serialized into json by {@link #toJson(JsonWriter)}.</p>
	 * <p>Client side helper, don't confused with {@link Query}.</p>
	 * @param conn
	 * @param parent
	 * @param from 
	 * @param as 
	 * @return query request
	 */
	public static QueryReq formatReq(String conn, JMessage<QueryReq> parent,
				String from, String as) {
		QueryReq bdItem = new QueryReq(parent, conn, from, as);
		return bdItem;
	}

	@Override
	public void toJson(JsonWriter writer) throws IOException, SemanticException {
		writer.beginObject();
		// design notes: keep consists with UpdateReq
		writer.name("conn").value(conn)
			.name("a").value(a)
			.name("mtabl").value(mtabl)
			.name("mAlias").value(mAlias)
			.name("page").value(page)
			.name("pgSize").value(pgsize);

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
		if (orders != null) {
			writer.name("orders");
			JHelper.writeLst(writer, orders);
		}

		writer.endObject();
	}

	@Override
	public void fromJson(JsonReader reader) throws IOException, SemanticException {
		JsonToken token = reader.peek();
		if (token == JsonToken.BEGIN_OBJECT) {
			reader.beginObject();
			token = reader.peek();
			while (token != JsonToken.END_OBJECT) {
				String name = reader.nextName();
				fromJsonName(name, reader);
				token = reader.peek();
			}
			reader.endObject();
		}
		else throw new SemanticException("Parse QueryReq failed. %s : %s", reader.getPath(), token.name());
	}

	@SuppressWarnings("unchecked")
	protected void fromJsonName(String name, JsonReader reader)
			throws SemanticException, IOException {
		if ("a".equals(name))
			a = JHelper.nextString(reader);
		else if ("conn".equals(name))
			conn = JHelper.nextString(reader);
		else if ("page".equals(name))
			page = reader.nextInt();
		else if ("pgSize".equals(name))
			pgsize = reader.nextInt();
		else if ("mtabl".equals(name))
			mtabl = JHelper.nextString(reader);
		else if ("mAlias".equals(name))
			mAlias = JHelper.nextString(reader);
		else if ("exprs".equals(name)) {
			if (reader.peek() == JsonToken.BEGIN_ARRAY)
				exprs = (ArrayList<String[]>) JHelper.readLstStrs(reader);
			else reader.nextString(); // skip "*"
		}
		else if ("joins".equals(name))
			joins = (ArrayList<String[]>) JHelper.readLstStrs(reader);
		else if ("where".equals(name))
			where = (ArrayList<String[]>) JHelper.readLstStrs(reader);
		else if ("orders".equals(name))
			orders = (ArrayList<String[]>) JHelper.readLstStrs(reader);
		else if ("groups".equals(name))
			groups = (ArrayList<String[]>) JHelper.readLstStrs(reader);
		// TODO ...
	}
}
