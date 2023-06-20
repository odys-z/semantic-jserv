package io.oz.album.tier;

import java.sql.SQLException;

import io.odysz.anson.Anson;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jsession.JUser.JUserMeta;

import static io.odysz.common.LangExt.isblank;

public class Profiles extends Anson {
	String uid;
	String defltAlbum;

	String home;
	public String home() { return home; }

	int maxUsers;
	public int maxusers() { return maxUsers; }
	
	int servtype;
	public int servtype() { return servtype; }

	public Profiles() {}
	
	public Profiles(String home) {
		this.home = home;
	}

	public Profiles(AnResultset rs, JUserMeta m) throws SQLException {
		this.home = rs.getString(m.org);
		this.uid = rs.getString(m.pk);
		this.defltAlbum = rs.getString("album");
		
		if (isblank(defltAlbum))
			this.defltAlbum = "a-001";
	}

	String webroot;
	public Profiles webroot(String cfg) {
		this.webroot = cfg;
		return this;
	}

}
