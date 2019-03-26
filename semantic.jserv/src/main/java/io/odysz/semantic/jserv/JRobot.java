package io.odysz.semantic.jserv;

import java.io.IOException;
import java.util.ArrayList;

import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.transact.x.TransException;

public class JRobot implements IUser {

	@Override public ArrayList<String> dbLog(ArrayList<String> sqls) { return sqls; }

	@Override public boolean login(Object request) throws TransException { return true; }

	@Override public String sessionId() { return null; }

	@Override public void touch() { } 

	@Override public String uid() { return "jrobot"; }

	@Override public SemanticObject logout() { return null; }

	@Override public void writeJsonRespValue(Object writer) throws IOException { }

}
