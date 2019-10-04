package io.odysz.semantic.jserv.U;

import java.util.ArrayList;

import io.odysz.semantic.jprotocol.JHeader;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.JProtocol.CRUD;
import io.odysz.semantics.x.SemanticException;

/**<p>Insert Request helper</p>
 * <b>Note:</b>
 * <p>InsertReq is a subclass of UpdateReq, and have no {@link #toJson(com.google.gson.stream.JsonWriter, io.odysz.semantic.jprotocol.JOpts) toJson()}
 * and {@link #fromJsonName(String, com.google.gson.stream.JsonReader) fromJson()} implementation.
 * Otherwise any post updating list in request won't work.</p>
 * Because all request element is deserialized a UpdateReq, so this can only work for Update/Insert request.</p>
 * <p>Design Memo<br>
 * This is a strong evidence showing that we need anson.</p>
 * see {@link UpdateReq#fromJsonName(String, com.google.gson.stream.JsonReader) super.fromJsonName()}<br>
 * and {@link io.odysz.semantic.jprotocol.JHelper#readLstUpdateReq(com.google.gson.stream.JsonReader) JHelper.readListUpdateReq()}
 * @author odys-z@github.com
 */
public class AnUpdateReq extends AnsonBody {
	/**Main table */
	String mtabl;

	/**nvs: [nv-obj],
	 * nv-obj: {n: "roleName", v: "admin"}
	 */
	ArrayList<Object[]> nvs;
	
	/**inserting values, used for "I". 3d array [[[n, v], ...]] */
	protected ArrayList<ArrayList<?>> nvss;
	/**inserting columns, used for "I".
	 * Here a col shouldn't be an expression - so not Object[], unlike that of query. */
	protected String[] cols;

	/**where: [cond-obj], see {@link #joins}for cond-obj.*/
	ArrayList<Object[]> where;
	
	String limt;

	ArrayList<AnUpdateReq> postUpds;
	
	public JHeader header;

	ArrayList<Object[]> attacheds;

	public AnUpdateReq() {
		super(null, null);
	}
	
	/**Don't call new InsertReq(), call {@link #formatReq(String, JMessage, String)}.
	 * This constructor is declared publicly for JHelper.
	 * @param parent
	 * @param conn
	 */
	public AnUpdateReq(AnsonMsg<? extends AnsonBody> parent, String conn) {
		super(parent, conn);
	}
	
	/**Format an insert request.
	 * @param conn
	 * @param parent
	 * @param tabl
	 * @param cmd {@link CRUD}.c R U D
	 * @return a new update request
	 */
	public static AnUpdateReq formatUpdateReq(String conn, AnsonMsg<AnUpdateReq> parent, String tabl) {
		AnUpdateReq bdItem = (AnUpdateReq) new AnUpdateReq(parent, conn)
				.a(CRUD.C);
		bdItem.mtabl = tabl;
		return bdItem;
	}

	public void valus(ArrayList<Object[]> row) throws SemanticException {
		if (nvs != null && nvs.size() > 0)
			throw new SemanticException("InsertReq don't support both nv() and values() been called for the same request object. User only one of them.");
		if (nvss == null)
			nvss = new ArrayList<ArrayList<?>>();
		nvss.add(row);
	}

}
