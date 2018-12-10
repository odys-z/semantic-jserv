package io.odysz.semantic.jserv.R;

import java.util.ArrayList;

import io.odysz.module.rs.SResultset;
import io.odysz.semantic.jprotocol.JMessage;

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
public class QueryReq extends JMessage {

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
	

	public QueryReq() {
		super(Port.query);
	}


	public QueryReq respond(SResultset rs) {

		return this;
	}

}
