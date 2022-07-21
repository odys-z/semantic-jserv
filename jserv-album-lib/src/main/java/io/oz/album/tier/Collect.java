package io.oz.album.tier;

import java.sql.SQLException;

import io.odysz.anson.Anson;
import io.odysz.module.rs.AnResultset;

public class Collect extends Anson {

	String tags;
	String cdate;
	String shareby;
	String cname;
	String cid;
	
	public Collect() {}

	/**
	 * set fields: cid, cname, shareby, cdate, tags
	 * 
	 * @param rs
	 * @throws SQLException
	 */
	public Collect(AnResultset rs) throws SQLException {
		this.cid = rs.getString("cid");
		this.cname = rs.getString("cname");
		this.shareby = rs.getString("shareby");
		this.cdate = rs.getString("cdate");
		this.tags = rs.getString("tags");
	}

}
