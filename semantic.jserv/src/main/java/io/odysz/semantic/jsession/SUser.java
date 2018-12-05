package io.odysz.semantic.jsession;

import java.util.ArrayList;

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

}
