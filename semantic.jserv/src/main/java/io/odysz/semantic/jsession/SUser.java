package io.odysz.semantic.jsession;

import java.util.ArrayList;

import javax.servlet.http.HttpServletRequest;

import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;

public class SUser extends SemanticObject implements IUser {
	protected String uid;

	@Override
	public String getUserId() {
		return uid;
	}

	@Override
	public ArrayList<String> dbLog(ArrayList<String> sqls) {
		return null;
	}

	public SemanticObject logout(SemanticObject header) {
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

}
