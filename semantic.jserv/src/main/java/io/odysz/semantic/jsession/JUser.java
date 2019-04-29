package io.odysz.semantic.jsession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import org.apache.commons.io.FilenameUtils;
import org.xml.sax.SAXException;

import com.google.gson.stream.JsonWriter;

import io.odysz.common.AESHelper;
import io.odysz.common.Radix64;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.LoggingUser;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

/**<p>Session user object.</p>
 * This object is usually created when user logged in,
 * and is used for semantics processing like finger print, etc.
 * 
 * @author odys-z@github.com
 *
 */
class JUser extends SemanticObject implements IUser {
	protected String ssid;
	protected String uid;
	private String pswd;
	private String usrName;

	@SuppressWarnings("unused")
	private long touched;
	private String funcId;
	private String funcName;

	private static DATranscxt logsctx;
	static {
		String conn = "local-sqlite";
		try {
			// TODO This is a typical initializing, should moved to subclass of JSingleton.
			DATranscxt.initConfigs(conn, FilenameUtils.concat(JSingleton.rootINF(), "semantic-log.xml"));
			logsctx = new DATranscxt(conn);
		} catch (SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	private static Random random = new Random();

	/**Constructor for session login
	 * @param uid user Id
	 * @param pswd pswd in DB
	 * @param iv iv in DB
	 * @param usrName
	 * @throws TransException 
	 */
	public JUser(String uid, String pswd, String iv, String usrName) throws SemanticException {
		this.uid = uid;
		this.pswd = pswd;
		this.usrName = usrName;
		
		if (SSession.rootK == null)
			// throw new SemanticException("Session rootKey not initialized. Use http GET /login.serv?t=init&k=[key]&header={} to set root key.");
			throw new SemanticException("Session rootKey not initialized. May check context prameter like tomcat context.xml/Parameter/name='io.oz.root-key'?");
		
		// decrypt db-pswd-cipher with sys-key and db-iv => db-pswd
		try {
			if (iv == null || iv.trim().length() == 0) {
				// this record is not encrypted - for robustness
				this.pswd = pswd;
			}
			else {
				byte[] dbiv = AESHelper.decode64(iv);
				String plain = AESHelper.decrypt(pswd, SSession.rootK, dbiv);
				this.pswd = plain;
			}
		}
		catch (Throwable e) { throw new SemanticException (e.getMessage()); }
	}

	/**jmsg should be what the response of {@link SSession}
	 * @param jmsg
	 */
	public JUser(SemanticObject jmsg) {
		uid = jmsg.getString("uid");
	}

	@Override public String uid() { return uid; }

	public String get(String prop) { return "TODO"; }

	@Override
	public ArrayList<String> dbLog(ArrayList<String> sqls) {
		return LoggingUser.genLog(logsctx, sqls, this, funcName, funcId);
	}

	public void touch() {
		touched = System.currentTimeMillis();
	}

	@Override
	public IUser logAct(String funcName, String funcId) {
		this.funcName = funcName;
		this.funcId = funcId;
		return this;
	}
	public String sessionId() {
		if (ssid == null)
			ssid = randomId();
		return ssid;
	}

	@Override
	public boolean login(Object request) throws TransException {
		SessionReq req = (SessionReq)request;

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
	public IUser set(String prop, Object value) {
		return this;
	}

	@Override
	public SemanticObject logout() {
		return null;
	}

	@Override
	public void writeJsonRespValue(Object writer) throws IOException {
		JsonWriter wr = (JsonWriter) writer;
		wr.beginObject();
		wr.name("uid").value(uid);
		wr.name("ssid").value(ssid);
		wr.name("user-name").value(usrName);
		wr.endObject();
	}

	private static String randomId() {
		return String.format("%s%s",
				Radix64.toString(random.nextInt()),
				Radix64.toString(random.nextInt()));
	}
}
