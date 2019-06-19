package io.odysz.semantic.jserv;

import java.io.IOException;
import java.util.ArrayList;

import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.transact.x.TransException;

/**This robot is only used for test.
 * If you are implementin a servlet without login, subclassing a {@link io.odysz.semantic.jserv.jsession.JUser JUser} instead.
 * @author odys-z@github.com
 */
public class JRobot implements IUser {

	long touched;

	@Override public ArrayList<String> dbLog(ArrayList<String> sqls) { return sqls; }

	@Override public boolean login(Object request) throws TransException { return true; }

	@Override public String sessionId() { return null; }

	@Override public void touch() { touched = System.currentTimeMillis(); } 
	@Override public long touchedMs() { return touched; } 

	@Override public String uid() { return "jrobot"; }

	@Override public SemanticObject logout() { return null; }

	@Override public void writeJsonRespValue(Object writer) throws IOException { }

	@Override public IUser logAct(String funcName, String funcId) { return this; }

}
