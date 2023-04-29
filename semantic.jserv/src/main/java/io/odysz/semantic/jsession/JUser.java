package io.odysz.semantic.jsession;

import static io.odysz.common.LangExt.isNull;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.xml.sax.SAXException;

import io.odysz.anson.Anson;
import io.odysz.common.AESHelper;
import io.odysz.common.Configs;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.LoggingUser;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

/**<p>IUser implementation supporting session.</p>
 * <p>This object is usually created when user logged in,
 * and is used for semantics processing like finger print, etc.</p>
 * <p>The logging connection is configured in configs.xml/k=log-connId.</p>
 * <p>A subclass can be used for handling serv without login.</p>
 *
 * @author odys-z@github.com
 */
public class JUser extends SemanticObject implements IUser {
	/**Hard coded field string of user table information.
	 *
	 * @author odys-z@github.com
	 */
	public static class JUserMeta extends TableMeta {

		public JUserMeta(String... conn) {
			super("a_users", conn);
			this.tbl = "a_users";
			this.pk = "userId";
			this.uname = "userName";
			this.pswd = "pswd";
			this.iv = "encAuxiliary";
			this.org = "orgId";
			this.orgName = "orgName";
			this.role = "roleId";
			this.roleName = "roleName";
		}

		/**key in config.xml for class name, this class implementing IUser is used as user object's type. */
		public String pk; // = "userId";
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

		public String orgTbl = "a_orgs";
		public String roleTbl = "a_roles";

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

	protected String ssid;
	protected String uid;
	protected String org;
	protected String role;
	private String pswd;

	/**@since 1.4.11 */
	@Override
	public String orgId() { return org; }
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

	private static DATranscxt logsctx;
	private static String[] connss;
	public static final String sessionSmtXml;
	public static final String logTabl;
	static {
		String conn = Configs.getCfg("log-connId");
		if (LangExt.isblank(conn))
			Utils.warn("ERROR\nERROR JUser need a log connection id configured in configs.xml, but get: ", conn);
		try {
			connss = conn.split(","); // [conn-id, log.xml, a_logs]
			// logsctx = new DATranscxt(connss[0]);
			logsctx = new LogTranscxt(connss[0], connss[1], connss[2]);
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
		finally {
			sessionSmtXml  = connss != null ? connss[1] : "";
			logTabl = connss != null ? connss[2] : "";
		}
	}

	/**Constructor for session login
	 * @param uid user Id
	 * @param pswd pswd in DB (plain text)
	 * @param usrName
	 * @throws TransException
	 */
	public JUser(String uid, String pswd, String usrName) throws SemanticException {
		this.uid = uid;
		this.pswd = pswd;

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

	public TableMeta meta(String ...conn) {
		// return new JUserMeta("a_user", AnSession.sctx.getSysConnId());
		return new JUserMeta("a_user", isNull(conn) ? AnSession.sctx.getSysConnId() : conn[0]);
	}

	/**jmsg, the response of {@link AnSession}
	 * @param jmsg
	 */
	public JUser(SemanticObject jmsg) {
		uid = jmsg.getString("uid");
	}

	@Override public String uid() { return uid; }

	@Override
	public ArrayList<String> dbLog(ArrayList<String> sqls) {
		return LoggingUser.genLog(logsctx, logTabl, sqls, this, funcName, funcId);
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

	/**Add notifyings
	 * @param note
	 * @return this
	 * @throws TransException
	 */
	public JUser notify(Object note) throws TransException {
		return (JUser) add("_notifies_", note);
	}

	/**Get notified string list.
	 * @return notifyings
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
