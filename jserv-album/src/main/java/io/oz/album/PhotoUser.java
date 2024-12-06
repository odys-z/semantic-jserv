package io.oz.album;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.io.FileUtils;

import io.odysz.anson.Anson;
import io.odysz.common.AESHelper;
import io.odysz.common.Configs;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.ShExtFilev2;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.AnSessionReq;
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantic.syn.SyncUser;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.SessionInf;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.album.peer.PhotoMeta;
import io.oz.album.peer.Profiles;

/**
 * Album user.
 * 
 * @deprecated
 * @author odys-z@github.com
 */
public class PhotoUser extends SyncUser implements IUser {

	static final String WebRoot = "web-root";

	long touched;

	String roleId;
	String roleName;
	String orgName;

	private String pswd;

	public static PUserMeta userMeta;
	
	static {
		userMeta = new PUserMeta("a_users");
	}

	public PhotoUser(String userid, String domain) {
		super(userid, "Photo Robot", domain, null);
	}

	/**
	 * Reflect constructor
	 * @param userid
	 * @param pswd
	 * @param userName
	 */
	public PhotoUser(String userid, String pswd, String userName, String syndomain) {
		super(userid, userName, syndomain, null);
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
				roleId   = rs.getString(userMeta.role);
				userName = rs.getString(userMeta.uname);
				org      = rs.getString(userMeta.org);
				roleName = rs.getString(userMeta.org);
				orgName  = rs.getString(userMeta.orgName);
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

	@Override public ArrayList<String> dbLog(ArrayList<String> sqls) { return null; }

	@Override public boolean login(Object reqObj) throws TransException {
		AnSessionReq req = (AnSessionReq)reqObj;
		// 1. encrypt db-uid with (db.pswd, j.iv) => pswd-cipher
		byte[] ssiv = AESHelper.decode64(req.iv());
		String c = null;
		try { c = AESHelper.encrypt(userId, pswd, ssiv); }
		catch (Exception e) { throw new TransException (e.getMessage()); }

		// 2. compare pswd-cipher with j.pswd
		if (c.equals(req.token())) {
			touch();
			return true;
		}

		return false;
	}

	@Override public IUser touch() {
		touched = System.currentTimeMillis();
		return this;
	} 

	@Override public long touchedMs() { return touched; } 

	@Override public String uid() { return userId; }
	
	@Override public String pswd() { return pswd; }

	@Override public void writeJsonRespValue(Object writer) throws IOException { }

	@Override public IUser logAct(String funcName, String funcId) { return this; }

	@Override public String sessionId() { return ssid; }

	@Override public IUser sessionId(String ssid) { this.ssid = ssid; return this; }

	@Override public IUser notify(Object note) throws TransException { return this; }

	@Override public List<Object> notifies() { return null; }

	@Override public SemanticObject logout() {
		if (tempDirs != null)
		for (String temp : tempDirs) {
			try {
				Utils.logi("Deleting: %s", temp);
				FileUtils.deleteDirectory(new File(temp));
			} catch (IOException e) {
				Utils.warn("Can not delete folder: %s.\n%s", temp, e.getMessage());
			}
		}
		return null;
	}

	/**
	 * <p>Get a temp dir, and have it deleted when logout.</p>
	 * Since jserv 1.4.3 and album 0.5.2, deleting temp dir is handled by PhotoRobot.
	 * @param conn
	 * @return the dir
	 * @throws SemanticException
	 */
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

	@Override
	public SessionInf getClientSessionInf(IUser login) throws Exception { 
		// SessionInf inf = new SessionInf(login.sessionId(), login.uid(), login.roleId());
		SessionInf inf = super.getClientSessionInf(login);
		inf .device(login.deviceId())
			.userName(((PhotoUser)login).userName);
		return inf;
	}

	@Override
	public Profiles profile() {
		return new Profiles().webroot(Configs.getCfg(WebRoot));
	}
}
