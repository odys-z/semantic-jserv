package io.oz.album;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.io.FileUtils;

import io.odysz.anson.Anson;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.ShExtFilev2;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.AnSessionReq;
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.SessionInf;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.album.tier.PhotoMeta;
import io.oz.jserv.docsync.SyncRobot;

/**A robot used session-less service.
 * 
 * @author odys-z@github.com
 */
public class PhotoRobot extends SyncRobot implements IUser {

	long touched;

	String roleId;
	String roleName;
	String orgName;

	RobotMeta userMeta;

	public PhotoRobot(String userid) {
		super(userid, null, "Photo Robot");
		userMeta = (RobotMeta) meta();
	}

	/**
	 * Reflect constructor
	 * @param userid
	 * @param pswd
	 * @param userName
	 */
	public PhotoRobot(String userid, String pswd, String userName) {
		super(userid, null, "Photo Robot");
		userMeta = (RobotMeta) meta();
	}
	
	public static class RobotMeta extends JUserMeta {
		String device;
		public RobotMeta(String... conn) {
			super(conn);

			iv = "iv";
			device = "device";
		}
	}

	public TableMeta meta() {
		return new RobotMeta("a_users");
	}

	// TODO move this to JUser?
	@Override
	public IUser onCreate(Anson with) throws SsException {
		if (with instanceof AnResultset) {
			AnResultset rs = (AnResultset) with;
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
		if (with instanceof AnSessionReq) {
			deviceId = ((AnSessionReq)with).deviceId();
			if (LangExt.isblank(deviceId, "/", "\\."))
				throw new SsException("Photo user's device Id can not be null - used for distinguish files.");
		}
		return this;
	}

	@Override public ArrayList<String> dbLog(ArrayList<String> sqls) { return null; }

	@Override public boolean login(Object request) throws TransException { return true; }

	@Override
	public IUser touch() {
		touched = System.currentTimeMillis();
		return this;
	} 

	@Override public long touchedMs() { return touched; } 

	@Override public String uid() { return userId; }

	@Override public void writeJsonRespValue(Object writer) throws IOException { }

	@Override public IUser logAct(String funcName, String funcId) { return this; }

	@Override public String sessionId() { return ssid; }

	@Override public IUser sessionId(String ssid) { this.ssid = ssid; return this; }

	@Override public IUser notify(Object note) throws TransException { return this; }

	@Override public List<Object> notifies() { return null; }

	@Override
	public SemanticObject logout() {
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
	 * Get a temp dir, and have it deleted when logout.
	 * 
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

	public String defaultAlbum() {
		return "a-001";
	}

	@Override
	public SessionInf getClientSessionInf(IUser login) { 
		SessionInf inf = new SessionInf(login.sessionId(), login.uid(), login.roleId());
		inf.device(login.deviceId());
		return inf;
	}
}
