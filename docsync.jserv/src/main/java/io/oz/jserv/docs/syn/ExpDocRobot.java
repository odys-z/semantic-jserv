package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.isNull;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import io.odysz.anson.Anson;
import io.odysz.common.Utils;
import io.odysz.semantic.DASemantics.ShExtFilev2;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.AnSessionReq;
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.SessionInf;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

/**
 * A robot for background pushing.
 * 
 * @author odys-z@github.com
 */
public class ExpDocRobot implements IUser {

	protected long touched;
	protected String userId;
	protected String userName;

	protected String orgId;
	public String orgId() { return orgId; }
	public ExpDocRobot orgId(String org) {
		orgId = org;
		return this;
	}

	protected String deviceId;
	public String deviceId() { return deviceId; }

	protected String ssid;

	protected Set<String> tempDirs;
	public String orgName;
	public ExpDocRobot orgName (String org) {
		orgName = org;
		return this;
	}

	public ExpDocRobot(String userid) {
		this.userId = userid;
	}

	/**
	 * for jserv construction
	 * 
	 * @param userid
	 * @param pswd
	 * @param userName
	 */
	public ExpDocRobot(String userid, String pswd, String userName) {
		this.userId = userid;
		this.userName = userName;
	}
	
	public static class RobotMeta extends JUserMeta {
		String device;
		public RobotMeta(String tbl, String conn) {
			super(conn);

			iv = "iv";
			device = "device";
		}
	}

	/**
	 * User table's meta, not doc table's meta.
	 * 
	 * @throws TransException 
	 */
	@Override
	public TableMeta meta(String ... connId) throws SQLException, TransException {
		return new RobotMeta("a_users", isNull(connId) ? null : connId[0])
				.clone(Connects.getMeta(
				isNull(connId) ? null : connId[0], "a_users"));
	}
	
	@Override
	public IUser onCreate(Anson reqBody) throws SsException {
		deviceId = ((AnSessionReq)reqBody).deviceId();
		if (isblank(deviceId, "/", "\\."))
			throw new SsException("Photo user's device Id can not be null - used for distinguish files.");
		return this;
	}

	@Override
	public ArrayList<String> dbLog(ArrayList<String> sqls) throws TransException { return null; }

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
				// Utils.logi("Deleting: %s", temp);
				FileUtils.deleteDirectory(new File(temp));
			} catch (IOException e) {
				Utils.warn("Can not delete folder: %s.\n%s", temp, e.getMessage());
			}
		}
		return null;
	}

	/**
	 * Get a temporary folder, and will be deleted when logout.
	 * 
	 * @param conn DB connect
	 * @param tabl task category, e. g. entity table name
	 * @return the folder path
	 * @throws SemanticException
	 */
	public String touchTempDir(String conn, String tabl) throws SemanticException {

		String extroot = ((ShExtFilev2) DATranscxt
						.getHandler(conn, tabl, smtype.extFilev2))
						.getFileRoot();

		String tempDir = IUser.tempDir(extroot, userId, "uploading-temp", ssid);
		if (tempDirs == null)
			tempDirs= new HashSet<String>(1);
		tempDirs.add(tempDir);
		return tempDir;
	}

//	public String defaultAlbum() {
//		return "a-001";
//	}

	public SessionInf sessionInf() {
		return new SessionInf().device(deviceId);
	}

	public ExpDocRobot device(String dev) {
		deviceId = dev;
		return this;
	}
}
