package io.odysz.semantic.jsession;

import io.odysz.anson.Anson;

public class SessionInf extends Anson {
	String ssid;
	String uid;
	String roleId; 

	public SessionInf () {
	}
	
	public SessionInf (String ssid, String uid, String... roleId) {
		this.ssid = ssid;
		this.uid = uid;
		this.roleId = roleId == null || roleId.length == 0 ? null : roleId[0];
	}
	
	public String ssid() { return ssid; }
	public String uid() { return uid; }
	public String roleId() { return roleId; }
}
