package io.oz.jserv.docs.syn;

import java.util.ArrayList;

import io.odysz.common.Configs;
import io.odysz.semantic.jsession.JUser;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.oz.album.tier.Profiles;

/**
 * Album user.
 * 
 * @author odys-z@github.com
 */
public class DocUser extends JUser implements IUser {

	static final String WebRoot = "web-root";

	long touched;

	String roleId;
	String roleName;
	String orgName;

	private String pswd;

	public static JUserMeta userMeta;
	
	static {
		userMeta = new JUserMeta("a_users");
	}

	public DocUser(String userid, String passwd) throws SemanticException {
		super(userid, passwd, userid);
	}

	/**
	 * Reflect constructor
	 * @param userid
	 * @param pswd
	 * @param userName
	 * @throws SemanticException 
	public DocUser(String userid, String pswd, String userName, String syndomain) throws SemanticException {
		super(userid, pswd, userName);
		this.pswd = pswd;
	}
	
	public static class PUserMeta extends JUserMeta {
		String device;
		public PUserMeta(String... conn) {
			super(conn);

			iv = "iv";
			device = "device";
		}
	}
	
	public TableMeta meta() {
		return userMeta;
	}

	@Override
	public IUser onCreate(Anson withSession) throws SsException {
		if (withSession instanceof AnResultset) {
			AnResultset rs = (AnResultset) withSession;
			try {
				rs.beforeFirst().next();
				roleId = rs.getString(userMeta.role);
				userName = rs.getString(userMeta.uname);
				orgId = rs.getString(userMeta.org);
				roleName = rs.getString(userMeta.org);
				orgName = rs.getString(userMeta.orgName);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		if (withSession instanceof AnSessionReq) {
			deviceId = ((AnSessionReq)withSession).deviceId();
			if (LangExt.isblank(deviceId, "/", "\\."))
				Utils.logi("User %s logged in on %s as read only mode.",
						((AnSessionReq)withSession).uid(), new Date().toString());
		}
		return this;
	}
	 */

	@Override public ArrayList<String> dbLog(ArrayList<String> sqls) { return null; }

//	@Override public boolean login(Object reqObj) throws TransException {
//		AnSessionReq req = (AnSessionReq)reqObj;
//		// 1. encrypt db-uid with (db.pswd, j.iv) => pswd-cipher
//		byte[] ssiv = AESHelper.decode64(req.iv());
//		String c = null;
//		try { c = AESHelper.encrypt(userId, pswd, ssiv); }
//		catch (Exception e) { throw new TransException (e.getMessage()); }
//
//		// 2. compare pswd-cipher with j.pswd
//		if (c.equals(req.token())) {
//			touch();
//			return true;
//		}
//
//		return false;
//	}

//	@Override public IUser touch() {
//		touched = System.currentTimeMillis();
//		return this;
//	} 

//	@Override public long touchedMs() { return touched; } 

//	@Override public String uid() { return userId; }
	
//	@Override public String pswd() { return pswd; }

//	@Override public void writeJsonRespValue(Object writer) throws IOException { }

//	@Override public IUser logAct(String funcName, String funcId) { return this; }

//	@Override public String sessionId() { return ssid; }

//	@Override public IUser sessionId(String ssid) { this.ssid = ssid; return this; }

//	@Override public IUser notify(Object note) throws TransException { return this; }

//	@Override public List<Object> notifies() { return null; }

//	@Override public SemanticObject logout() {
//		if (tempDirs != null)
//		for (String temp : tempDirs) {
//			try {
//				Utils.logi("Deleting: %s", temp);
//				FileUtils.deleteDirectory(new File(temp));
//			} catch (IOException e) {
//				Utils.warn("Can not delete folder: %s.\n%s", temp, e.getMessage());
//			}
//		}
//		return null;
//	}

	/**
	 * <p>Get a temp dir, and have it deleted when logout.</p>
	 * Since jserv 1.4.3 and album 0.5.2, deleting temp dir is handled by PhotoRobot.
	 * @param conn
	 * @return the dir
	 * @throws SemanticException
	public String touchTempDir(String conn) throws TransException {

		String extroot = ((ShExtFilev2) DATranscxt
						.getHandler(conn, new PhotoMeta(conn).tbl, smtype.extFilev2))
						.getFileRoot();

		String tempDir = IUser.tempDir(extroot, userId, "uploading-temp", ssid);
		if (tempDirs == null)
			tempDirs= new HashSet<String>(1);
		tempDirs.add(tempDir);
		return tempDir;
	}
	 */

//	@Override
//	public SessionInf getClientSessionInf(IUser login) throws Exception { 
//		// SessionInf inf = new SessionInf(login.sessionId(), login.uid(), login.roleId());
//		SessionInf inf = super.getClientSessionInf(login);
//		inf .device(login.deviceId())
//			.userName(((DocUser)login).userName);
//		return inf;
//	}

	@Override
	public Profiles profile() {
		return new Profiles().webroot(Configs.getCfg(WebRoot));
	}

//	@Override
//	public TableMeta meta(String... connId) {
//		return new JUserMeta("a_users")
//				.clone(Connects.getMeta(
//				isNull(connId) ? null : connId[0], "a_users"));
//	}
}
