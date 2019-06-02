package io.odysz.semantic.jserv.U;

import java.io.IOException;

import com.google.gson.stream.JsonWriter;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JOpts;
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
public class InsertReq extends UpdateReq {

	public InsertReq(JMessage<? extends JBody> parent, String conn) {
		super(parent, conn);
	}
	
//	public InsertReq cols(String[] cols) {
//		super.cols = cols;
//		return this;
//	}
	/**Format an insert request.
	 * @param conn
	 * @param parent
	 * @param tabl
	 * @param cmd {@link CRUD}.c R U D
	 * @return a new update request
	 */
	public static InsertReq formatReq(String conn, JMessage<InsertReq> parent, String tabl) {
		InsertReq bdItem = (InsertReq) new InsertReq(parent, conn)
				.a(CRUD.C);
		bdItem.mtabl = tabl;
		return bdItem;
	}

	
	/**Tolerate some situation and call super{@link #toJson(JsonWriter, JOpts)}.
	 * @see io.odysz.semantic.jserv.U.UpdateReq#toJson(com.google.gson.stream.JsonWriter, io.odysz.semantic.jprotocol.JOpts)
	 */
	public void toJson(JsonWriter writer, JOpts opts) throws IOException, SemanticException {
		if (cols == null && nvs != null) {
			// try figure out the cols and tolerate this
			cols = new String[nvs.size()];
			for (int ix = 0; ix < nvs.size(); ix++) {
				Object[] nv = nvs.get(ix);
				cols[ix] = (String)nv[0];
			}
		}
			
		super.toJson(writer, opts);
	}
}
