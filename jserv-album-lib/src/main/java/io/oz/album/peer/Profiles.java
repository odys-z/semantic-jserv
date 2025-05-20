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

	/** Personal web page url */
	public String webnode;
	public String servroot;
	public String home;
	
	/** E.g. Synode in jserv-album or docsync.jserv. */
	public String servId;

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

	public Profiles(String synode, AnResultset rs, JUserMeta m, String orgAlbum, String orgWebroot) throws SQLException {
		this.servId = synode;
		this.home = rs.getString(m.org);
		this.uid = rs.getString(m.pk);
		this.defltAlbum = rs.getString(orgAlbum);
		this.webnode = rs.getString(orgWebroot);

		if (isblank(defltAlbum))
			this.defltAlbum = "a-001";
	}

	public Profiles webroot(String webroot) {
		this.webnode = webroot;
		return this;
	}

	public Profiles servroot(String servroot) {
		this.servroot = servroot;
		return this;
	}

}
