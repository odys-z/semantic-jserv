package io.odysz.semantic.jserv.U;

import java.io.IOException;
import java.util.ArrayList;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JHeader;
import io.odysz.semantic.jprotocol.JMessage;

/**Update Request Body<br>
 * 
 * @author odys-z@github.com
 */
public class UpdateReq extends JBody {
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

	/**nvs: [nv-obj],
	 * nv-obj: {n: "roleName", v: "admin"}
	 *  */
	ArrayList<String[]> nvs;
	
	/**where: [cond-obj], see {@link #joins}for cond-obj.*/
	ArrayList<String[]> where;
	
	
	public JHeader header;

	/**orders: [order-obj],
     - order-obj: {tabl: "b_articles", field: "pubDate", asc: "true"} */
//	ArrayList<String[]> orders;
	
	/**group: [group-obj]
     - group-obj: {tabl: "b_articles/t_alais", expr: "recId" } */
//	ArrayList<String[]> groups;
	
	public UpdateReq(JMessage<? extends JBody> parent, String conn) {
		super(parent, conn);
	}

	@Override
	public void toJson(JsonWriter writer) throws IOException {
		writer.beginObject().endArray();
	}

	@Override
	public void fromJson(JsonReader reader) throws IOException {
		
	}
	
}
