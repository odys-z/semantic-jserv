package io.odysz.semantic.jsession;

import io.odysz.anson.Anson;

public class SessionInf extends Anson {
	String ssid;
	String uid;
	String roleId;
	String userName;
	String roleName; 

	public String device;
	
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

	public String userName() { return userName; }
	public SessionInf userName(String name) {
		this.userName = name;
		return this;
	}

	public String roleName() { return roleName; }
	public SessionInf roleName(String name) {
		this.roleName = name;
		return this;
	}
}
