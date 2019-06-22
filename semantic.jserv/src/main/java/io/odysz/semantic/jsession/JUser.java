package io.odysz.semantic.jsession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.xml.sax.SAXException;

import com.google.gson.stream.JsonWriter;

import io.odysz.common.AESHelper;
import io.odysz.common.Configs;
import io.odysz.common.LangExt;
import io.odysz.common.Radix64;
import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.LoggingUser;
import io.odysz.semantic.jprotocol.JMessage.MsgCode;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
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
	protected String ssid;
	protected String uid;
	private String pswd;
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

	private static Random random = new Random();

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
			// throw new SemanticException("Session rootKey not initialized. Use http GET /login.serv?t=init&k=[key]&header={} to set root key.");
			throw new SemanticException("Session rootKey not initialized. May check context prameter like tomcat context.xml/Parameter/name='io.oz.root-key'?");
		
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

	public String sessionId() {
		if (ssid == null)
			ssid = randomId();
		return ssid;
	}

	@Override
	public String sessionKey() { 
		// FIXME
		return ssid;
	}

	@Override
	public IUser sessionKey(String skey) {
		return (IUser) put("s-key", skey);
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
		// return (List<Object>) props.get("_notifies_");
		return (List<Object>) get("_notifies_");
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
	public SemanticObject logout() {
		return new SemanticObject().code(MsgCode.ok.name());
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

	public IUser notify(String name) {
		// TODO Auto-generated method stub
		return null;
	}
}
