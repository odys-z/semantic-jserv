//package io.odysz.jsample.cheap;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//
//import io.odysz.semantic.jprotocol.AnsonBody;
//import io.odysz.semantic.jprotocol.AnsonMsg;
//import io.odysz.semantic.jserv.R.AnQueryReq;
//import io.odysz.semantic.jserv.U.AnUpdate;
//import io.odysz.semantic.jserv.U.AnUpdateReq;
//import io.odysz.semantics.IUser;
//import io.odysz.sworkflow.EnginDesign.Req;
//import io.odysz.transact.sql.Statement;
//import io.odysz.transact.x.TransException;
//
//public class CheapReq extends AnsonBody {
//	public static CheapReq format(AnsonMsg<AnQueryReq> parent, Req req, String wfId) {
//		CheapReq r = new CheapReq(parent);
//		r.a = req.name();
//		r.wftype = wfId;
//		return r;
//	}
//
//	protected String wftype;
//	public String wftype() { return wftype; }
//	public CheapReq wftype(String wfid) {
//		wftype = wfid;
//		return this;
//	}
//
//	protected HashMap<String,Object> cmdArgs;
//	public Object args(String n) { return cmdArgs == null ? null : cmdArgs.get(n); }
//
//	private CheapReq args(String n, String v) {
//		if (cmdArgs == null)
//			cmdArgs = new HashMap<String, Object>();
//		cmdArgs.put(n, v);
//		return this;
//	}
//	
//	public CheapReq taskId(String tid) { return args("taskId", tid); }
//	public String taskId() { return (String) args("taskId"); }
//	
//	public CheapReq nodeId(String nid) { return args("nodeId", nid); }
//	public String nodeId() { return (String) args("nodeId"); }
//	
//	public CheapReq usrId(String uid) { return args("usrId", uid); }
//	public String usrId() { return (String) args("usrId"); }
//
//	public CheapReq instId(String iid) { return args("instId", iid); }
//	public String instId() { return (String) args("instId"); }
//
//	protected String ndescpt;
//	public CheapReq nodeDesc(String descpt) {
//		this.ndescpt = descpt;
//		return this;
//	}
//
//	protected ArrayList<Object[]> taskNvs;
//	public ArrayList<Object[]> taskNvs() { return taskNvs; }
//	public CheapReq taskNv(String n, Object v) {
//		if (taskNvs == null)
//			taskNvs = new ArrayList<Object[]>();
//			
//		taskNvs.add(new Object[] {n, v});
//		return this;
//	}
//
//	protected String cmd;
//	public String cmd() { return cmd; }
//	
//	/** 3d array of post insertings */
//	protected ArrayList<AnUpdateReq> postUpds;
//	public CheapReq post(AnUpdateReq p) {
//		if (postUpds == null)
//			postUpds = new ArrayList<AnUpdateReq>();
//		postUpds.add(p);
//		return this;
//	}
//
//	public CheapReq(AnsonMsg<? extends AnsonBody> parent) {
//		super(parent, null); // client can't control engine's connection
//	}
//
//	/**This should used only by JHelper, fake is ignored.
//	 * @param parent
//	 * @param fake
//	 */
//	public CheapReq(AnsonMsg<? extends AnsonBody> parent, String fake) {
//		super(parent, null); // client can't control engine's connection
//	}
//
//	public String req() { return a; }
//
//	/**calling super.a(req)
//	 * @param req
//	 * @return this
//	 */
//	public CheapReq req(Req req) {
//		return (CheapReq) a(req.name());
//	}
//	
//	/**Ask Req.cmd with cmd name as parameter.
//	 * @param cmd
//	 * @return the req object
//	 */
//	public CheapReq reqCmd(String cmd) {
//		// return args("req", cmd);
//		this.cmd = cmd;
//		return this;
//	}
//
//	/**Ask the node's right.
//	 * @param nodeId
//	 * @param usrId
//	 * @param taskId
//	 * @return the req object
//	 */
//	public CheapReq cmdsRight(String nodeId, String usrId, String taskId) {
//		nodeId(nodeId);
//		usrId(usrId);
//		taskId(taskId);
//		return req(Req.rights);
//	}
//
//	public CheapReq loadFlow(String wfId, String taskId) {
//		this.wftype(wfId);
//		taskId(taskId);
//		return req(Req.load);
//	}
//
//	/**format post children delete, inserts, updates by users
//	 * 
//	 * @return update / delete / insert
//	 * @throws TransException 
//	 */
//	public ArrayList<Statement<?>> posts(IUser usr) throws TransException {
//		return AnUpdate.postUpds(postUpds, usr);
//	}
//}
