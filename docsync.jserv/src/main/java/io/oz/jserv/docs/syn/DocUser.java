package io.oz.jserv.docs.syn;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import io.odysz.common.Configs;
import io.odysz.semantic.DASemantics.ShExtFilev2;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jsession.JUser;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.album.tier.Profiles;

/**
 * Doc User.
 * 
 * @author odys-z@github.com
 */
public class DocUser extends JUser implements IUser {
	public static JUserMeta userMeta;
	
	static {
		userMeta = new JUserMeta("a_users");
	}

	static final String WebRoot = "web-root";

	long touched;

	String roleId;
	String roleName;
	String orgName;

	protected String deviceId;
	public String deviceId() { return deviceId; }
	public DocUser deviceId(String id) {
		deviceId = id;
		return this;
	}

	public DocUser(String userid, String passwd, String userName) throws SemanticException {
		super(userid, passwd, userName);
	}

	public DocUser(String userid, String passwd) throws SemanticException {
		super(userid, passwd, userid);
	}

	public DocUser(String userid) throws SemanticException {
		super(userid, null, userid);
	}
	
	/*
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

	protected Set<String> tempDirs;
	/**
	 * <p>Get a temp dir, and have it deleted when logout.</p>
	 * Since jserv 1.4.3 and album 0.5.2, deleting temp dir is handled by PhotoRobot.
	 * @param conn
	 * @return the dir
	 * @throws SemanticException
	 */
	public String touchTempDir(String conn, String doctbl) throws TransException {

		String extroot = ((ShExtFilev2) DATranscxt
						.getHandler(conn, doctbl, smtype.extFilev2))
						.getFileRoot();

		String tempDir = IUser.tempDir(extroot, uid(), "uploading-temp", ssid);
		if (tempDirs == null)
			tempDirs= new HashSet<String>(1);
		tempDirs.add(tempDir);
		return tempDir;
	}

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
