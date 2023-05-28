package io.oz.sandbox;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import io.odysz.anson.Anson;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.transact.x.TransException;

/**This robot is only used for test.
 * If you are implementin a servlet without login, subclassing a {@link io.odysz.semantic.jserv.jsession.JUser JUser} instead.
 * @author odys-z@github.com
 */
public class SandRobot extends SemanticObject implements IUser {

	long touched;

	String remote;
	String org;
	String orgName;
	String role;
	String roleName;

	String ssid;

	public SandRobot(String userid) {
		this.remote = userid;
	}

	public SandRobot(String userid, String pswd, String userName) {
		this.remote = userid;
	}

	public static class SandRobotMeta extends JUserMeta {
		public SandRobotMeta(String tbl, String... conn) {
			super(conn);

			this.tbl = "a_users";
			pk = "userId";
			uname = "userName";
			pswd = "pswd";
			iv = "iv";
		}
	}

	public TableMeta meta(String ... connId) {
		// return new SandRobotMeta(""); // no user table as this test is only for sessionless
		JUserMeta m = new JUserMeta();
		m.iv = "iv";
		return m;
	}
	
	@Override
	public IUser onCreate(Anson rs) throws GeneralSecurityException {
		if (rs instanceof AnResultset) {
			JUserMeta m = (JUserMeta) meta();
			try {
				this.org      = ((AnResultset) rs).getString(m.org);
				this.orgName  = ((AnResultset) rs).getString(m.orgName);
				this.role     = ((AnResultset) rs).getString(m.role);
				this.roleName = ((AnResultset) rs).getString(m.roleName);
			} catch (SQLException e) {
				e.printStackTrace();
				throw new GeneralSecurityException(e.getMessage());
			}
		}

		return this;
	}

	@Override public ArrayList<String> dbLog(ArrayList<String> sqls) { return null; }

	@Override public boolean login(Object request) throws TransException { return true; }

	@Override public SandRobot touch() { touched = System.currentTimeMillis(); return this; } 

	@Override public long touchedMs() { return touched; } 

	@Override public String uid() { return remote; }
	@Override public String orgId() { return org; }

	@Override public SemanticObject logout() { return null; }

	@Override public void writeJsonRespValue(Object writer) throws IOException { }

	@Override public IUser logAct(String funcName, String funcId) { return this; }

	@Override public String sessionId() { return ssid; }
	@Override public IUser sessionId(String rad64num) {
		this.ssid = rad64num;
		return this;
	}

	@Override public IUser notify(Object note) throws TransException { return this; }

	@Override public List<Object> notifies() { return null; }

	// @Override public TableMeta meta() { return null; }

	@Override public IUser sessionKey(String string) { return this; }

	@Override public String sessionKey() { return null; }
}
