package io.odysz.semantic.jsession;

import java.util.HashMap;

import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantics.IUser;

public class T_AnSessionVerifier implements ISessionVerifier {

	protected boolean verifyToken;
	protected HashMap<String, IUser> users;

	public T_AnSessionVerifier(HashMap<String, IUser> users, boolean enable) {
		this.users = users;
		this.verifyToken = enable;
	}

	@Override
	public IUser verify(AnsonHeader anHeader, int ...seq) {
		return null;
	}
	
}
