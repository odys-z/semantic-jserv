package io.odysz.jsample.cheap;

import java.io.IOException;
import java.util.ArrayList;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JOpts;
import io.odysz.semantic.jserv.R.QueryReq;
import io.odysz.semantics.x.SemanticException;
import io.odysz.sworkflow.EnginDesign.Req;

public class CheapReq extends JBody {
	public static CheapReq format(JMessage<QueryReq> parent, Req req, String wfId) {
		CheapReq r = new CheapReq(parent);
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

	protected String[] cmdArgs;
	public String[] args() { return cmdArgs; }

	protected String ndescpt;
	protected String childTbl;
	protected ArrayList<String[]> taskNvs;
	public ArrayList<String[]> taskNvs() { return taskNvs; }
	/** 3d array */
	protected ArrayList<ArrayList<?>> childInserts;

	public CheapReq(JMessage<? extends JBody> parent) {
		super(parent, null); // client can't control engine's connection
	}

	/**This should used only by JHelper, fake is ignored.
	 * @param parent
	 * @param fake
	 */
	public CheapReq(JMessage<? extends JBody> parent, String fake) {
		super(parent, null); // client can't control engine's connection
	}

	@Override
	public void toJson(JsonWriter writer, JOpts opts) throws IOException, SemanticException {
		writer.beginObject();
		writer.name("conn").value(conn);
		writer.name("a").value(a);
		writer.name("wftype").value(wftype);
		writer.name("ndescpt").value(ndescpt);
		writer.name("childTbl").value(childTbl);

		if (taskNvs != null) {
			writer.name("taskNvs");
			JHelper.writeLst(writer, taskNvs, opts);
		}
		if (childInserts != null) {
			writer.name("childInserts");
			JHelper.writeLst(writer, childInserts, opts);
		}
		if (cmdArgs != null) {
			writer.name("cmdArgs");
			JHelper.writeStrings(writer, cmdArgs, opts);
		}
		writer.endObject();
	}

	@SuppressWarnings("unchecked")
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
				else if ("cmdArgs".equals(name)) 
					cmdArgs = JHelper.readStrs(reader);
				else if ("taskNvs".equals(name)) 
					taskNvs = (ArrayList<String[]>) JHelper.readLstStrs(reader);
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
		// childInserts.get(childInserts.size() - 1).add(new String[] {n, v});
		@SuppressWarnings("unchecked")
		ArrayList<String[]> lst = (ArrayList<String[]>) childInserts.get(childInserts.size() - 1);
		lst.add(new String[] {n, v});
		return this;
	}

	/**Prepare a new child table's row inserting.
	 * @return
	 */
	public CheapReq newChildInstRow() {
		if (childInserts == null)
			childInserts = new ArrayList<ArrayList<?>>();
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
	
	/**Ask Req.cmd with cmd name as parameter.
	 * @param cmd
	 * @return the req object
	 */
	public CheapReq reqCmd(String cmd) {
		this.cmdArgs = new String[] {cmd};
		return req(Req.cmd);
	}

	/**Ask the node's right.
	 * @param nodeId
	 * @param usrId
	 * @param taskId
	 * @return the req object
	 */
	public CheapReq cmdsRight(String nodeId, String usrId, String taskId) {
		this.cmdArgs = new String[] {nodeId, usrId, taskId};
		return req(Req.cmdsRight);
	}

	public CheapReq loadFlow(String wfId, String taskId) {
		this.cmdArgs = new String[] {wfId, taskId};
		return req(Req.load);
	}
}
