package io.oz.album.tier;

import java.sql.SQLException;

import io.odysz.common.DateFormat;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jsession.SessionInf;

public class Photo implements FileRecord {
	SessionInf ssInf;
	String pid;
	String pname;
	String uri;
	String shareby;
	String cdate;
	String geox;
	String geoy;
	String exif;
	String sharer;
	
	public Photo(AnResultset rs) throws SQLException {
		this.pid = rs.getString("pid");
		this.pname = rs.getString("pname");
		this.uri = rs.getString("uri");
		try {
			this.cdate = DateFormat.formatime(rs.getDate("cdate"));
		} catch (SQLException ex) {
			this.cdate = rs.getString("cdate");
		}
		this.geox = rs.getString("geox");
		this.geoy = rs.getString("geoy");
	}

	@Override
	public String recId() { return pid; }

	@Override
	public String uri() { return uri; }

	@Override
	public String token() { return ssInf == null ? null : ssInf.ssid(); }
}