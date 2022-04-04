package io.oz.album;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;

import io.odysz.anson.Anson;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.semantic.DASemantics.ShExtFile;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.AnSessionReq;
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.album.tier.Albums;

/**A robot is only used for test.
 * 
 * @author odys-z@github.com
 */
public class PhotoRobot extends SemanticObject implements IUser {

	long touched;

	String userId;
	
	String deviceId;
	public String deviceId() { return deviceId; }

	private String ssid;

	private Set<String> tempDirs;

	public PhotoRobot(String userid) {
		this.userId = userid;
	}

	public PhotoRobot(String userid, String pswd, String userName) {
		this.userId = userid;
	}
	
	public static class RobotMeta extends JUserMeta {
		public RobotMeta(String tbl, String... conn) {
			super(tbl, conn);

			this.tbl = "a_users";
			pk = "userId";
			uname = "userName";
			pswd = "pswd";
			iv = "iv";
		}
	}

	public TableMeta meta() {
		return new RobotMeta("");
	}

	@Override
	public IUser onCreate(Anson reqBody) throws SsException {
		deviceId = ((AnSessionReq)reqBody).deviceId();
		if (LangExt.isblank(deviceId, "/", "\\."))
			throw new SsException("Photo user's device Id can not be null - used for distinguish files.");
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

	/**Get a temp dir, and have it deleted when logout.
	 * @param conn
	 * @return the dir
	 * @throws SemanticException
	 */
	public String touchTempDir(String conn) throws SemanticException {

		String extroot = ((ShExtFile) DATranscxt
						.getHandler(conn, Albums.tablPhotos, smtype.extFile))
						.getFileRoot();

		String tempDir = IUser.tempDir(extroot, userId, "uploading-temp", ssid);
		if (tempDirs == null)
			tempDirs= new HashSet<String>(1);
		tempDirs.add(tempDir);
		return tempDir;
	}
}
