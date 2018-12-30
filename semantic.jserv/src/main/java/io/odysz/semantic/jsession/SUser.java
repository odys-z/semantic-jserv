package io.odysz.semantic.jsession;

import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;

import io.odysz.semantic.jprotocol.JHeader;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.transact.x.TransException;

/**<p>Session user object.</p>
 * This object is usually created when user logged in,
 * and is used for semantics processing like finger print, etc.
 * @author ody
 *
 */
class SUser extends SemanticObject implements IUser {
	protected String uid;

	/**jmsg should be what the response of {@link SSession}
	 * @param jmsg
	 */
	public SUser(SemanticObject jmsg) {
		uid = jmsg.getString("uid");
	}

	@Override
	public String uid() {
		return uid;
	}

	@Override
	public ArrayList<String> dbLog(ArrayList<String> sqls) {
		return null;
	}

	public SemanticObject logout(JHeader header) {
		// TODO Auto-generated method stub
		return null;
	}

	public void touch() {
		// TODO Auto-generated method stub
		
	}

	public boolean login(SemanticObject jlogin, HttpServletRequest request) {
		// TODO Auto-generated method stub
		return false;
	}

	public String sessionId() {
		// TODO Auto-generated method stub
		return null;
	}

	public String homepage() {
		// TODO Auto-generated method stub
		return null;
	}

	public String json() {
		return "user json ...";
	}

	@Override
	public boolean login() throws TransException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public SemanticObject logout() {
		// TODO Auto-generated method stub
		return null;
	}
}
