package io.odysz.jsample.cheap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JOpts;
import io.odysz.semantic.jserv.R.QueryReq;
import io.odysz.semantic.jserv.U.JUpdate;
import io.odysz.semantic.jserv.U.UpdateReq;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.sworkflow.EnginDesign.Req;
import io.odysz.transact.sql.Statement;
import io.odysz.transact.x.TransException;

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

	protected HashMap<String,Object> cmdArgs;
	public Object args(String n) { return cmdArgs == null ? null : cmdArgs.get(n); }

	private CheapReq args(String n, String v) {
		if (cmdArgs == null)
			cmdArgs = new HashMap<String, Object>();
		cmdArgs.put(n, v);
		return this;
	}
	
	public CheapReq taskId(String tid) { return args("taskId", tid); }
	public String taskId() { return (String) args("taskId"); }
	
	public CheapReq nodeId(String nid) { return args("nodeId", nid); }
	public String nodeId() { return (String) args("nodeId"); }
	
	public CheapReq usrId(String uid) { return args("usrId", uid); }
	public String usrId() { return (String) args("usrId"); }

	public CheapReq instId(String iid) { return args("instId", iid); }
	public String instId() { return (String) args("instId"); }

	protected String ndescpt;
//	protected String childTbl;
	protected ArrayList<Object[]> taskNvs;
	public ArrayList<Object[]> taskNvs() { return taskNvs; }
	public CheapReq taskNv(String n, Object v) {
		if (taskNvs == null)
			taskNvs = new ArrayList<Object[]>();
		taskNvs.add(new Object[] {n, v});
		return this;
	}

	protected String cmd;
	public String cmd() { return cmd; }
	
	/** 3d array of post insertings */
	protected ArrayList<UpdateReq> postUpds;
	public CheapReq post(UpdateReq p) {
		if (postUpds == null)
			postUpds = new ArrayList<UpdateReq>();
		postUpds.add(p);
		return this;
	}
	// protected ArrayList<ArrayList<?>> childInserts;

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
//		writer.name("childTbl").value(childTbl);

		if (cmd != null)
			writer.name("cmd").value(cmd);
//		if (childInsertabl != null)
//			writer.name("childInsertabl").value(childInsertabl);
		if (taskNvs != null) {
			writer.name("taskNvs");
			JHelper.writeLst(writer, taskNvs, opts);
		}
		if (postUpds != null) {
			writer.name("postUpds");
			JHelper.writeLst(writer, postUpds, opts);
		}
		if (cmdArgs != null) {
			writer.name("cmdArgs");
			JHelper.writeMap(writer, cmdArgs, opts);
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
				else if ("cmd".equals(name))
					cmd = JHelper.nextString(reader);
				else if ("port".equals(name))
					// tolerate client redundant
					JHelper.nextString(reader);
				else if ("ndescpt".equals(name))
					ndescpt = JHelper.nextString(reader);
//				else if ("childTbl".equals(name))
//					childTbl = JHelper.nextString(reader);
				else if ("cmdArgs".equals(name)) 
					// cmdArgs = JHelper.readStrs(reader);
					cmdArgs = JHelper.readMap(reader);
				else if ("taskNvs".equals(name)) 
					taskNvs = (ArrayList<Object[]>) JHelper.readLst_StrObj(reader, null); // null: shouldn't used for any v.
				else if ("postUpds".equals(name)) 
					// childInserts = JHelper.readLstLstStrs(reader);
					postUpds = (ArrayList<UpdateReq>) JHelper.readLstUpdateReq(reader);
				token = reader.peek();
			}
			reader.endObject();
		}
	}

	public CheapReq nodeDesc(String descpt) {
		this.ndescpt = descpt;
		return this;
	}

//	public CheapReq childTabl(String tbl) {
//		this.childTbl = tbl;
//		return this;
//	}

	/**Insert nv into the newly prepared row.
	 * @see {@link #newChildInstRow()}.
	 * @param n
	 * @param v
	 * @return
	public CheapReq childInsert(String n, String v) {
		// childInserts.get(childInserts.size() - 1).add(new String[] {n, v});
		@SuppressWarnings("unchecked")
		ArrayList<String[]> lst = (ArrayList<String[]>) childInserts.get(childInserts.size() - 1);
		lst.add(new String[] {n, v});
		return this;
	}
	 */

	/**Prepare a new child table's row inserting.
	 * @return
	public CheapReq newChildInstRow() {
		if (childInserts == null)
			childInserts = new ArrayList<ArrayList<?>>();
		childInserts.add(new ArrayList<String[]>());
		return this;
	}
	 */

	public String req() { return a; }

	/**calling super.a(req)
	 * @param req
	 * @return this
	 */
	public CheapReq req(Req req) {
		return (CheapReq) a(req.name());
	}
	
	/**Ask Req.cmd with cmd name as parameter.
	 * @param cmd
	 * @return the req object
	 */
	public CheapReq reqCmd(String cmd) {
		// return args("req", cmd);
		this.cmd = cmd;
		return this;
	}

	/**Ask the node's right.
	 * @param nodeId
	 * @param usrId
	 * @param taskId
	 * @return the req object
	 */
	public CheapReq cmdsRight(String nodeId, String usrId, String taskId) {
		// this.cmdArgs = new String[] {nodeId, usrId, taskId};
		nodeId(nodeId);
		usrId(usrId);
		taskId(taskId);
		return req(Req.rights);
	}

	public CheapReq loadFlow(String wfId, String taskId) {
		// this.cmdArgs = new String[] {wfId, taskId};
		this.wftype(wfId);
		taskId(taskId);
		return req(Req.load);
	}

	/**format post children delete, inserts, updates by users
	 * 
	 * @return update / delete / insert
	 * @throws TransException 
	 */
	public ArrayList<Statement<?>> posts(IUser usr) throws TransException {
		return JUpdate.postUpds(postUpds, usr);
	}
}
