package io.odysz.semantic.jserv;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.transact.x.TransException;

/**This robot is only used for test.
 * If you are implementin a servlet without login, subclassing a
 * {@link io.odysz.semantic.jsession.JUser JUser} instead.
 * @author odys-z@github.com
 */
public class JRobot implements IUser {

	long touched;

	@Override public ArrayList<String> dbLog(ArrayList<String> sqls) { return null; }

	@Override public boolean login(Object request) throws TransException { return true; }

	@Override public JRobot touch() { touched = System.currentTimeMillis(); return this; } 

	@Override public long touchedMs() { return touched; } 

	@Override public String uid() { return "jrobot"; }

	@Override public SemanticObject logout() { return null; }

	@Override public void writeJsonRespValue(Object writer) throws IOException { }

	@Override public IUser logAct(String funcName, String funcId) { return this; }

	@Override public String sessionId() { return null; }

	@Override public IUser notify(Object note) throws TransException { return this; }

	@Override public List<Object> notifies() { return null; }

	@Override public TableMeta meta(String... conn) { return null; }

	@Override public IUser sessionKey(String string) { return this; }

	@Override public String sessionKey() { return null; }
}
