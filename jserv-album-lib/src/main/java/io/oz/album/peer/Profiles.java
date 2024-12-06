package io.oz.album.peer;

import java.sql.SQLException;

import io.odysz.anson.Anson;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jsession.JUser.JUserMeta;

import static io.odysz.common.LangExt.isblank;

/**
 * User profile.
 * 
 * @author odys-z@github.com
 */
public class Profiles extends Anson {
	String uid;
	public String defltAlbum;

	public String webroot;
	public String home;

	public Profiles home(String h) {
		home = h;
		return this;
	}

	public int maxUsers;
	public Profiles maxusers(int max) {
		maxUsers = max;
		return this;
	}
	
	public int servtype;
	public Profiles servtype(int t) {
		servtype = t;
		return this;
	}

	public Profiles() {}
	
	public Profiles(String home) {
		this.home = home;
	}

	public Profiles(AnResultset rs, JUserMeta m) throws SQLException {
		this.home = rs.getString(m.org);
		this.uid = rs.getString(m.pk);
		this.defltAlbum = rs.getString("album");
		this.webroot = rs.getString("webroot");

		if (isblank(defltAlbum))
			this.defltAlbum = "a-001";
	}

	public Profiles webroot(String cfg) {
		this.webroot = cfg;
		return this;
	}

}
