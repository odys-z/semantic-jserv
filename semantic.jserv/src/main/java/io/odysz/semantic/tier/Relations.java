package io.odysz.semantic.tier;

import java.util.ArrayList;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonField;
import io.odysz.semantic.DATranscxt;
import io.odysz.transact.sql.Delete;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Statement;
import io.odysz.transact.x.TransException;

/**Relation table's object (parent-tabl, rows)
 * 
 * @author odys-z@github.com
 *
 */
public class Relations extends Anson {
	String rtabl;
	String[] cols;

	@AnsonField(valType="java.util.ArrayList;[Ljava.lang.Object;")
	ArrayList<ArrayList<Object[]>> rows;

	public Statement<?> update(DATranscxt st) throws TransException {
		Delete del = st.delete(rtabl);
		
		if (rows != null && rows.size() > 0) {
			Insert ins = st.insert(rtabl).cols(cols);
			for (int i = 0; i < cols.length; i++)
				ins.values(rows);
			del.post(ins);
		}
		return del;
	}
}
