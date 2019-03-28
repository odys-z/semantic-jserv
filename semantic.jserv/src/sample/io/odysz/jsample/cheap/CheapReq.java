package io.odysz.jsample.cheap;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jserv.R.QueryReq;
import io.odysz.semantics.x.SemanticException;
import io.odysz.sworkflow.EnginDesign.Req;

public class CheapReq extends JBody {
	public static CheapReq format(JMessage<QueryReq> parent, Req req, String wfId) {
		CheapReq r = new CheapReq(parent, null);
		r.a = req.name();
		r.wftype = wfId;
		return r;
	}

	protected String wftype;
	public String wftype() { return wftype; }
	public CheapReq wftype(String wfid) {
		wftype = wfid;
		return this;
	}

	protected String nodeDesc;
	protected String ndescpt;
	protected String childTbl;
	protected ArrayList<String[]> taskNvs;
	public ArrayList<String[]> taskNvs() { return taskNvs; }
	protected ArrayList<ArrayList<String[]>> childInserts;

	public CheapReq(JMessage<? extends JBody> parent, String conn) {
		super(parent, conn);
	}

	@Override
	public void toJson(JsonWriter writer) throws IOException {
		writer.beginObject();
		writer.name("conn").value(conn);
		writer.name("a").value(a);
		writer.name("wftype").value(wftype);
		writer.name("ndescpt").value(ndescpt);
		writer.name("childTbl").value(childTbl);

		try {
			if (taskNvs != null) {
				writer.name("taskNvs");
				JHelper.writeLst(writer, taskNvs);
			}
			if (childInserts != null) {
				writer.name("childInserts");
				JHelper.writeLst(writer, childInserts);
			}
		} catch (SQLException e) {
			e.printStackTrace();	
		}
		writer.endObject();
	}

	@Override
	public void fromJson(JsonReader reader) throws IOException, SemanticException {
		JsonToken token = reader.peek();
		if (token == JsonToken.BEGIN_OBJECT) {
			reader.beginObject();
			while (token != JsonToken.END_OBJECT) {
				String name = reader.nextName();
				if ("a".equals(name))
					a = JHelper.nextString(reader);
				else if ("conn".equals(name))
					conn = JHelper.nextString(reader);
				else if ("wftype".equals(name))
					wftype = JHelper.nextString(reader);
				else if ("ndescpt".equals(name))
					ndescpt = JHelper.nextString(reader);
				else if ("childTbl".equals(name))
					childTbl = JHelper.nextString(reader);
				else if ("taskNvs".equals(name)) 
					taskNvs = JHelper.readLstStrs(reader);
				else if ("childInserts".equals(name)) 
					childInserts = JHelper.readLstLstStrs(reader);
				token = reader.peek();
			}
			reader.endObject();
		}
	}

	public CheapReq nodeDesc(String descpt) {
		this.ndescpt = descpt;
		return this;
	}

	public CheapReq childTabl(String tbl) {
		this.childTbl = tbl;
		return this;
	}

	/**Insert nv into the newly prepared row.
	 * @see {@link #newChildInstRow()}.
	 * @param n
	 * @param v
	 * @return
	 */
	public CheapReq childInsert(String n, String v) {
		childInserts.get(childInserts.size() - 1).add(new String[] {n, v});
		return this;
	}

	/**Prepare a new child table's row inserting.
	 * @return
	 */
	public CheapReq newChildInstRow() {
		if (childInserts == null)
			childInserts = new ArrayList<ArrayList<String[]>>();
		childInserts.add(new ArrayList<String[]>());
		return this;
	}

	public CheapReq taskNv(String n, String v) {
		if (taskNvs == null)
			taskNvs = new ArrayList<String[]>();
		taskNvs.add(new String[] {n, v});
		return this;
	}

	public String req() {
		return a;
	}

	public CheapReq req(Req req) {
		return (CheapReq) a(req.name());
	}


}
