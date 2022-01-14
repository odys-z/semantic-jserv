package io.oz.album.tier;

import java.sql.SQLException;

import io.odysz.module.rs.AnResultset;

public class Collect {

	String tags;
	String cdate;
	String shareby;
	String cname;
	String cid;

	public Collect(AnResultset rs) throws SQLException {
		this.cid = rs.getString("cid");
		this.cname = rs.getString("cname");
		this.shareby = rs.getString("shareby");
		this.cdate = rs.getString("cdate");
		this.tags = rs.getString("tags");
	}

}
