package io.odysz.semantic.jserv.U;

import static io.odysz.semantic.jprotocol.JProtocol.CRUD.D;
import static io.odysz.semantic.jprotocol.JProtocol.CRUD.R;
import static io.odysz.semantic.jprotocol.JProtocol.CRUD.U;
import java.util.ArrayList;

import io.odysz.anson.AnsonField;
import io.odysz.common.LangExt;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.JProtocol.CRUD;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Query.Ix;

/**<p>Insert Request Message</p>
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
	
//	@AnsonField(valType="java.util.ArrayList/[Ljava.lang.Object;")
	/**inserting values, used for "I". 3d array [[[n, v], ...]] */
	protected ArrayList<ArrayList<Object[]>> nvss;
	/**inserting columns, used for "I".
	 * Here a col shouldn't be an expression - so not Object[], unlike that of query. */
	protected String[] cols;

	/** get columns for sql's insert into COLs. 
	 * @return columns
	 */
	public String[] cols() { return cols; }


	/**where: [cond-obj], see {@link #joins}for cond-obj.*/
	ArrayList<Object[]> where;
	
	String limt;

	ArrayList<AnUpdateReq> postUpds;
	
	public AnsonHeader header;

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

	public AnUpdateReq nv(String n, Object v) {
		if (nvs == null)
			nvs = new ArrayList<Object[]>();
		Object[] nv = new Object[2];
		nv[Ix.nvn] = n;
		nv[Ix.nvv] = v;
		nvs.add(nv);
		return this;	
	}

	public void valus(ArrayList<Object[]> row) throws SemanticException {
		if (nvs != null && nvs.size() > 0)
			throw new SemanticException("InsertReq don't support both nv() and values() been called for the same request object. User only one of them.");
		if (nvss == null)
			nvss = new ArrayList<ArrayList<Object[]>>();
		nvss.add(row);
	}

	/** get values in VALUE-CLAUSE for sql insert into (...) values VALUE-CLAUSE 
	 * @return [[[n, v], ...]]
	 */
	public ArrayList<ArrayList<Object[]>> values() {
		if (nvs != null && nvs.size() > 0) {
			if (nvss == null)
				nvss = new ArrayList<ArrayList<Object[]>>();

			nvss.add(nvs);
			nvs = null;
		}
		return nvss;
	}

	/**FIXME wrong?
	 * @param file
	 * @param b64
	 * @return
	 */
	public AnUpdateReq attach(String file, String b64) {
		if (attacheds == null)
			attacheds = new ArrayList<Object[]>();
		attacheds.add(new String[] {file, b64});
		return this;
	}

	public AnUpdateReq where(String oper, String lop, String rop) {
		if (where == null)
			where = new ArrayList<Object[]>();

		String[] predicate = new String[Ix.predicateSize];
		predicate[Ix.predicateOper] = oper;
		predicate[Ix.predicateL] = lop;
		predicate[Ix.predicateR] = rop;

		where.add(predicate);
		return this;
	}

	/** calling where("=", lop, "'" + rconst + "'")
	 * @param lop
	 * @param rconst
	 * @return
	 */
	public AnUpdateReq whereEq(String lop, String rconst) {
		return where("=", lop, "'" + rconst + "'");
	}

	public AnUpdateReq post(AnUpdateReq pst) {
		if (postUpds == null)
			postUpds = new ArrayList<AnUpdateReq>();
		postUpds.add(pst);
		return this;
	}

	public void validate() throws SemanticException {
		if (!D.equals(a) && (nvs == null || nvs.size() <= 0) && (nvss == null || nvss.size() <= 0))
			throw new SemanticException("Updating/inserting denied for empty column values");
		if ((U.equals(a) || D.equals(a)) && (where == null || where.isEmpty()))
				throw new SemanticException("Updatin/deleting  denied for empty conditions");
		if (!R.equals(a) && mtabl == null || LangExt.isblank(mtabl))
				throw new SemanticException("Updating/inserting/deleting denied for empty main table");
		
	}
}
