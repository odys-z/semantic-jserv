package io.odysz.semantic.jserv.U;

import static io.odysz.semantic.CRUD.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import io.odysz.anson.Anson;
import io.odysz.anson.JsonOpt;
import io.odysz.anson.x.AnsonException;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Query.Ix;

/**<p>Insert Request Message</p>
 * <b>Note:</b>
 * <p>InsertReq is a subclass of UpdateReq, and have no {@link #toBlock(JsonOpt)}
 * and {@link #fromJson(java.io.InputStream)} implementation.
 * Otherwise any post updating list in request won't work.</p>
 * Because all request element is deserialized a UpdateReq, so this can only work for Update/Insert request.</p>
 * <p>Design Memo<br>
 * This is a strong evidence showing that we need anson.</p>
 * @author odys-z@github.com
 */
public class AnUpdateReq extends AnsonBody {
	@Override
	public Anson toBlock(OutputStream stream, JsonOpt... opts) throws AnsonException, IOException {
		if (C.equals(a) && (cols == null || cols.length == 0))
			Utils.warn("WARN - UpdateReq.toJson():\nFound inserting request but cols are null, this is wrong for no insert statement can be generated.\n" +
					"Suggestion: call the InsertReq.col(col-name) before serialize this to json for table: %s\n" +
					"Another common error leads to this is using UpdateReq for inserting with java client.",
					mtabl);
		return super.toBlock(stream, opts);
	}

	/**Format an update request.
	 * @param funcUri
	 * @param parent
	 * @param tabl
	 * @return a new update request
	 */
	public static AnUpdateReq formatUpdateReq(String funcUri, AnsonMsg<AnUpdateReq> parent, String tabl) {
		AnUpdateReq bdItem = ((AnUpdateReq) new AnUpdateReq(parent, funcUri)
				.a(U))
				.mtabl(tabl);
		return bdItem;
	}
	
	/**Format a delete request.
	 * @param furi
	 * @param parent
	 * @param tabl
	 * @return a new deleting request
	 */
	public static AnUpdateReq formatDelReq(String furi, AnsonMsg<AnUpdateReq> parent, String tabl) {
		AnUpdateReq bdItem = ((AnUpdateReq) new AnUpdateReq(parent, furi)
								.a(D))
								.mtabl(tabl);
		return bdItem;
	}

	/**Main table */
	String mtabl;
	public AnUpdateReq mtabl(String mtbl) {
		mtabl = mtbl;
		return this;
	}

	/**nvs: [nv-obj],
	 * nv-obj: {n: "roleName", v: "admin"}
	 */
	ArrayList<Object[]> nvs;
	
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
	
	/**Don't call new InsertReq(), call {@link #formatUpdateReq(String, AnsonMsg, String)}.
	 * This constructor is declared publicly for JHelper.
	 * @param parent
	 * @param uri
	 */
	public AnUpdateReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
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
	 * @return update request
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
	 * @return update request
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
			throw new SemanticException("Updating/inserting denied for empty column values (Empyt nv value)");
		if ((U.equals(a) || D.equals(a)) && (where == null || where.isEmpty()))
				throw new SemanticException("Updatin/deleting denied for empty conditions");
		if (!R.equals(a) && mtabl == null || LangExt.isblank(mtabl))
				throw new SemanticException("Updating/inserting/deleting denied for empty main table name.");
		
	}
}
