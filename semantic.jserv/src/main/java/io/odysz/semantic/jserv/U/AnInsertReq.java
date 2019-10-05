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

	/**Don't call new InsertReq(), call {@link #formatReq(String, JMessage, String)}.
	 * This constructor is declared publicly for JHelper.
	 * @param parent
	 * @param conn
	 */
	public AnInsertReq(AnsonMsg<? extends AnsonBody> parent, String conn) {
		super(parent, conn);
	}
	
	public AnInsertReq cols(String[] cols) {
		super.cols = cols;
		return this;
	}

	/**Format an insert request.
	 * @param conn
	 * @param parent
	 * @param tabl
	 * @param cmd {@link CRUD}.C R U D
	 * @return a new update request
	 */
	public static AnInsertReq formatInsertReq(String conn, AnsonMsg<AnInsertReq> parent, String tabl) {
		AnInsertReq bdItem = (AnInsertReq) new AnInsertReq(parent, conn)
				.a(CRUD.C);
		bdItem.mtabl = tabl;
		return bdItem;
	}
	
//	/**Tolerate some situation and call super{@link #toJson(JsonWriter, JOpts)}.
//	 * @see io.odysz.semantic.jserv.U.UpdateReq#toJson(com.google.gson.stream.JsonWriter, io.odysz.semantic.jprotocol.JOpts)
//	 */
//	public void toJson(JsonWriter writer, JOpts opts) throws IOException, SemanticException {
//		if (cols == null && nvs != null) {
//			// try figure out the cols and tolerate this
//			cols = new String[nvs.size()];
//			for (int ix = 0; ix < nvs.size(); ix++) {
//				Object[] nv = nvs.get(ix);
//				cols[ix] = (String)nv[0];
//			}
//		}
//			
//		super.toJson(writer, opts);
//	}

	public void valus(ArrayList<Object[]> row) throws SemanticException {
		if (nvs != null && nvs.size() > 0)
			throw new SemanticException("InsertReq don't support both nv() and values() been called for the same request object. User only one of them.");
		if (nvss == null)
			nvss = new ArrayList<ArrayList<?>>();
		nvss.add(row);
	}
}
