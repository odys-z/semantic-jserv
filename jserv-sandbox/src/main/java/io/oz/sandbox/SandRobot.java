package io.oz.sandbox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.transact.x.TransException;

import static io.odysz.common.LangExt.isNull;;

/**This robot is only used for test.
 * If you are implementin a servlet without login, subclassing a {@link io.odysz.semantic.jserv.jsession.JUser JUser} instead.
 * @author odys-z@github.com
 */
public class SandRobot implements IUser {

	long touched;

	String remote;

	public SandRobot(String userid) {
		this.remote = userid;
	}

	public SandRobot(String userid, String pswd, String userName) {
		this.remote = userid;
	}

	public static class SandRobotMeta extends JUserMeta {
		public SandRobotMeta(String tbl, String... conn) {
			super(tbl, isNull(conn) ? null : conn[0]);

			this.tbl = "a_users";
			pk = "userId";
			uname = "userName";
			pswd = "pswd";
			iv = "iv";
		}
	}

	public TableMeta meta() {
		return new SandRobotMeta("");
	}

	@Override public ArrayList<String> dbLog(ArrayList<String> sqls) { return null; }

	@Override public boolean login(Object request) throws TransException { return true; }

	@Override public SandRobot touch() { touched = System.currentTimeMillis(); return this; } 

	@Override public long touchedMs() { return touched; } 

	@Override public String uid() { return remote; }

	@Override public SemanticObject logout() { return null; }

	@Override public void writeJsonRespValue(Object writer) throws IOException { }

	@Override public IUser logAct(String funcName, String funcId) { return this; }

	@Override public String sessionId() { return null; }

	@Override public IUser notify(Object note) throws TransException { return this; }

	@Override public List<Object> notifies() { return null; }

	// @Override public TableMeta meta() { return null; }

	@Override public IUser sessionKey(String string) { return this; }

	@Override public String sessionKey() { return null; }
}
