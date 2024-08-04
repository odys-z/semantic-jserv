package io.odysz.semantic.jsession;

import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.split;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import io.odysz.anson.Anson;
import io.odysz.common.AESHelper;
import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.LoggingUser;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.meta.SemanticTableMeta;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

/**
 * <p>IUser implementation supporting session.</p>
 * <p>This object is usually created when user logged in,
 * and is used for semantics processing like finger print, etc.</p>
 * <p>The logging connection is configured in configs.xml/k=log-connId.</p>
 * <p>A subclass can be used for handling serv without login.</p>
 * 
 * @author odys-z@github.com
 */
public class JUser extends SemanticObject implements IUser {
	/**
	 * Hard coded field string of user table information.
	 *
	 * which will use this for data synchronizing robot's creation,
	 * {@link io.odysz.semantic.meta.JUserMeta} 
	 * 
	 * @author odys-z@github.com
	 */
	public static class JUserMeta extends SemanticTableMeta {

		public JUserMeta(String... conn) {
			super("a_users", conn);
			rm = new JRoleMeta(conn);
			om = new JOrgMeta(conn);

			this.pk      = "userId";
			this.uname   = "userName";
			this.pswd    = "pswd";
			this.iv      = "iv"; // since 2.0.0
			this.org     = "orgId";
			this.orgName = "orgName";
			this.role    = "roleId";
			this.roleName= "roleName";
		}
		
		public final JRoleMeta rm;
		public final JOrgMeta  om;
		
		/**key in config.xml for class name, this class implementing IUser is used as user object's type. */
		// public final String pk; // = "userId";
		public String uname; // = "userName";
		public String pswd; // = "pswd";
		public String iv; // = "encAuxiliary";
		/** v1.4.11, column of org id */
		public String org;
		/** v1.4.11, column of org name */
		public String orgName;
		/** v1.4.11, column of role id */
		public String role;
		/** v1.4.11, column of role name */
		public String roleName;

		// public String orgTbl = "a_orgs";
		// public String roleTbl = "a_roles";

		public JUserMeta userName(String unamefield) {
			uname = unamefield;
			return this;
		}

		public JUserMeta iv(String ivfield) {
			iv = ivfield;
			return this;
		}

		public JUserMeta pswd(String pswdfield) {
			pswd = pswdfield;
			return this;
		}
	}

	public static class JRoleMeta extends SemanticTableMeta {
		public final String roleName;
		public final String remarks;
		public final String org;

		public JRoleMeta (String... conn) {
			super("a_roles", conn);
			
			pk = "roleId";
			roleName = "roleName";
			remarks  = "remarks";
			org      = "org";
			
			ddlSqlite = "CREATE TABLE a_roles(\r\n"
						+ "roleId TEXT(20) not null, \r\n"
						+ "roleName TEXT(50), \r\n"
						+ "remarks TEXT(200),\r\n"
						+ "orgId TEXT(20),\r\n"
						+ "CONSTRAINT a_roles_pk PRIMARY KEY (roleId)"
						+ ");";
		}
	}
	
	public static class JOrgMeta extends SemanticTableMeta {
		public final String orgName;
		public final String orgType;
		public final String parent;
		public final String sort;
		public final String fullpath;

		public JOrgMeta(String... conn) {
			super("a_orgs", conn);
			
			pk = "orgId";
			orgName = "orgName";
			orgType = "orgType";
			parent  = "parent";
			sort    = "sort";
			fullpath= "fullpath";

			ddlSqlite =
					"CREATE TABLE a_orgs (\r\n"
					+ "	orgId   varchar2(12) NOT NULL,\r\n"
					+ "	orgName varchar2(50),\r\n"
					+ "	orgType varchar2(40) , -- a reference to a_domain.domainId (parent = 'a_orgs')\r\n"
					+ "	parent  varchar2(12),\r\n"
					+ "	sort    int DEFAULT 0,\r\n"
					+ "	fullpath varchar2(200), webroot TEXT, album0 varchar2(16),\r\n"
					+ "\r\n"
					+ "	PRIMARY KEY (orgId)\r\n"
					+ ");";
		}
	}

	protected String ssid;
	protected String uid;
	protected String org;
	protected String role;
	private String pswd;
	
	/**@since 1.4.11 */
	@Override
	public String orgId() { return org; }

	/**@since 2.0.0 */
	public JUser orgId(String id) {
		org = id;
		return this;
	}

	/**@since v1.4.11 */
	@Override
	public String roleId() { return role; }

	private long touched;

	/** current action's business function */
	String funcId;
	String funcName;
	String userName;
	String roleName;

	String orgName;
	public IUser orgName(String n) {
		orgName = n;
		return this;
	}

	private static DATranscxt logsctx;
	private static String logConn;
	public static final String logTabl;

	static {
		String[] connss = null;
		try {
			String conn = Configs.getCfg(Configs.keys.logConnId); // "log-connId"
			if (isblank(conn))
				; // Utils.warn("ERROR JUser need a log connection id configured in configs.xml, but get: ", conn);
			else
				connss = split(conn, ","); // [conn-id, a_logs]

			if (isNull(connss))
				// throw new SemanticException("Parsing log connection config error: %s", conn);
				Utils.logi(
					"JUser uses a log connection id configured in configs.xml, but get an empty conn-id.\n" +
					"DB log is disabled.",
					conn);
			else {
				// logsctx = new LogTranscxt(connss[0], connss[1], connss[2]);
				logsctx = new LogTranscxt(connss[0]);
				logConn = connss[0];
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			logTabl = connss != null ? connss[1] : "a_logs";
		}
	}

	/**
	 * Constructor for session login
	 * 
	 * @param uid user Id
	 * @param pswd pswd in DB (plain text)
	 * @param usrName
	 * @throws TransException
	 */
	public JUser(String uid, String pswd, String usrName) throws SemanticException {
		this.uid = uid;
		this.pswd = pswd == null ? this.pswd : pswd;

		String rootK = DATranscxt.key("user-pswd");
		if (rootK == null)
			throw new SemanticException("Session rootKey not initialized. Have checked context prameter like server's context.xml/Parameter/name='io.oz.root-key'?");

		// decrypt db-pswd-cipher with sys-key and db-iv => db-pswd
//		try {
//			if (iv == null || iv.trim().length() == 0) {
//				// this record is not encrypted - for robustness
//				this.pswd = pswd;
//			}
//			else {
//				byte[] dbiv = AESHelper.decode64(iv);
//				String plain = AESHelper.decrypt(pswd, rootK, dbiv);
//				this.pswd = plain;
//			}
//		}
//		catch (Throwable e) { throw new SemanticException (e.getMessage()); }

		this.pswd = pswd;
	}

	public TableMeta meta(String ... connId) {
		// return new JUserMeta("a_user", AnSession.sctx.getSysConnId());
		return new JUserMeta("a_user", isNull(connId) ? null : connId[0]);
	}

	/**
	 * Handle jmsg.uid, the response of {@link AnSession}
	 * 
	 * @param jmsg
	 */
	public JUser(SemanticObject jmsg) {
		uid = jmsg.getString("uid");
	}

	@Override public String uid() { return uid; }

	@Override
	public ArrayList<String> dbLog(ArrayList<String> sqls) {
		return LoggingUser.genLog(logConn, logsctx, logTabl, sqls, this, funcName, funcId);
	}

	public JUser touch() {
		touched = System.currentTimeMillis();
		return this;
	}

	@Override
	public long touchedMs() { return touched; }

	@Override
	public IUser logAct(String funcName, String funcId) {
		this.funcName = funcName;
		this.funcId = funcId;
		return this;
	}

	@Override
	public String sessionId() {
		return ssid;
	}

	@Override
	public IUser sessionId(String sessionId) {
		this.ssid = sessionId;
		return this;
	}

	/** Session Token Knowledge */
	String knoledge;
	@Override public String sessionKey() { return knoledge; }

	@Override
	public IUser sessionKey(String k) {
		this.knoledge = k;
		return this;
	}

	/**
	 * Add notifying
	 * @param note
	 * @return this
	 * @throws TransException
	 */
	public JUser notify(Object note) throws TransException {
		return (JUser) add("_notifies_", note);
	}

	/**
	 * Get notified string list.
	 * @return notifying
	 */
	@SuppressWarnings("unchecked")
	public List<Object> notifies() {
		return (List<Object>) get("_notifies_");
	}

	@Override
	public boolean login(Object reqObj) throws TransException {
		AnSessionReq req = (AnSessionReq)reqObj;
		// 1. encrypt db-uid with (db.pswd, j.iv) => pswd-cipher
		byte[] ssiv = AESHelper.decode64(req.iv);
		String c = null;
		try { c = AESHelper.encrypt(uid, pswd, ssiv); }
		catch (Exception e) { throw new TransException (e.getMessage()); }

		// 2. compare pswd-cipher with j.pswd
		if (c.equals(req.token())) {
			touch();
			return true;
		}

		return false;
	}

	@Override
	public boolean guessPswd(String pswd64, String iv64)
			throws TransException, GeneralSecurityException, IOException {
		return pswd != null && pswd.equals(AESHelper.decrypt(pswd64, this.ssid, AESHelper.decode64(iv64)));
	}

	@Override
	public String pswd() { return pswd; }

	@Override
	public SemanticObject logout() {
		return new SemanticObject().code(MsgCode.ok.name());
	}
	
	@Override
	public IUser validatePassword() throws SsException, SQLException, TransException {
		return this;
	}
	
	@Override
	public IUser onCreate(Anson with) throws SsException {
		if (with instanceof AnResultset) {
			JUserMeta meta = (JUserMeta) meta();
			AnResultset rs = (AnResultset) with;
			try {
				rs.beforeFirst().next();
				userName = rs.getString(meta.uname);
				role = rs.getString(meta.role);
				org = rs.getString(meta.org);
				roleName = rs.getString(meta.roleName);
				orgName = rs.getString(meta.orgName);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		return this;
	}
}
