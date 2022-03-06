package io.oz.album;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.odysz.anson.Anson;
import io.odysz.common.LangExt;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.AnSessionReq;
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.transact.x.TransException;

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

	@Override public SemanticObject logout() { return null; }

	@Override public void writeJsonRespValue(Object writer) throws IOException { }

	@Override public IUser logAct(String funcName, String funcId) { return this; }

	@Override public String sessionId() { return ssid; }

	@Override public IUser sessionId(String ssid) { this.ssid = ssid; return this; }

	@Override public IUser notify(Object note) throws TransException { return this; }

	@Override public List<Object> notifies() { return null; }
}
