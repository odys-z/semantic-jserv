package io.odysz.semantic.jserv.U;

import java.util.ArrayList;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.JProtocol.CRUD;
import io.odysz.semantics.x.SemanticException;

/**<p>Insert Request helper</p>
 * <b>Note:</b>
 * <p>AnInsertReq is a subclass of {@link AnUpdateReq}.</p>
 * <p>Because all request element is deserialized as an AnUpdateReq, so this can only work for Update/Insert request.</p>
 * @author odys-z@github.com
 */
public class AnInsertReq extends AnUpdateReq {

	public AnInsertReq() { }

	/**Don't call new AnInsertReq(), call {@link #formatInsertReq(String, AnsonMsg, String)}
	 * This constructor is declared publicly for JHelper.
	 * @param parent
	 * @param furi function uri
	 */
	public AnInsertReq(AnsonMsg<? extends AnsonBody> parent, String furi) {
		super(parent, furi);
		a = CRUD.C;
	}
	
	public AnInsertReq cols(String[] cols) {
		super.cols = cols;
		a = CRUD.C;
		return this;
	}

	/**Format an insert request.
	 * @param furi
	 * @param parent
	 * @param tabl
	 * @return a new update request
	 */
	public static AnInsertReq formatInsertReq(String furi, AnsonMsg<AnInsertReq> parent, String tabl) {
		AnInsertReq bdItem = (AnInsertReq) new AnInsertReq(parent, furi)
				.a(CRUD.C);
		bdItem.mtabl = tabl;
		return bdItem;
	}
	
	public void valus(ArrayList<Object[]> row) throws SemanticException {
		if (nvs != null && nvs.size() > 0)
			throw new SemanticException("InsertReq don't support both nv() and values() been called for the same request object. User only one of them.");
		if (nvss == null)
			nvss = new ArrayList<ArrayList<Object[]>>();
		nvss.add(row);
	}

	public AnUpdateReq cols(String c, String ...ci) {
		if (cols == null)
			cols = new String[ci == null ? 1 : ci.length + 1];
		cols[0] = c;
		for (int ix = 0; ci != null && ix < ci.length; ix++)
			cols[ix + 1] = ci[ix];
		return this;
	}
}
