package io.odysz.semantic.jsession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.xml.sax.SAXException;

import io.odysz.common.AESHelper;
import io.odysz.common.Configs;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.LoggingUser;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
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
		public JUserMeta(String tbl, String... conn) {
			super(tbl, conn);
			this.tbl = "a_user";
			this.pk = "userId";
			this.uname = "userName";
			this.pswd = "pswd";
			this.iv = "encAuxiliary";
		}

		/**key in config.xml for class name, this class implementing IUser is used as user object's type. */
//		String clzz = "class-IUser";
		protected String tbl; // = "a_user";
		protected String pk; // = "userId";
		protected String uname; // = "userName";
		protected String pswd; // = "pswd";
		protected String iv; // = "encAuxiliary";

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
	private String pswd;
	@SuppressWarnings("unused")
	private String usrName;

	private long touched;
	private String funcId;
	private String funcName;

	private static DATranscxt logsctx;
	static {
		String conn = Configs.getCfg("log-connId");
		if (LangExt.isblank(conn))
			Utils.warn("ERROR\nERROR JUser need a log connection id configured in configs.xml, but get: ", conn);
		try {
			logsctx = new DATranscxt(conn);
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
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
		this.usrName = usrName;

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

	public TableMeta meta() {
		return new JUserMeta("a_user", AnSession.sctx.basiconnId());
	}

	/**jmsg should be what the response of {@link SSession}
	 * @param jmsg
	 */
	public JUser(SemanticObject jmsg) {
		uid = jmsg.getString("uid");
	}

	@Override public String uid() { return uid; }

	@Override
	public ArrayList<String> dbLog(ArrayList<String> sqls) {
		return LoggingUser.genLog(logsctx, sqls, this, funcName, funcId);
	}

	public void touch() {
		touched = System.currentTimeMillis();
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
	 * @param n
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
	public SemanticObject logout() {
		return new SemanticObject().code(MsgCode.ok.name());
	}

	@Override
	public IUser sessionKey(String skey) {
		// ssid = skey;
		return this;
	}

	@Override
	public String sessionKey() {
		return null;
	}
}
