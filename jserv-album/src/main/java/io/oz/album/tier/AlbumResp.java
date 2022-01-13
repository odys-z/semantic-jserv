package io.oz.album.tier;

import java.sql.SQLException;

import io.odysz.common.DateFormat;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jprotocol.AnsonResp;

public class AlbumResp extends AnsonResp {

	String fileName;
	String pid;
	String fileUri;
	String cdate;
	String geox;
	String geoy;

	public AlbumResp(AnResultset rs) throws SQLException {
		this.pid = rs.getString("pid");
		this.fileName = rs.getString("pname");
		this.fileUri = rs.getString("uri");
		try {
			this.cdate = DateFormat.formatime(rs.getDate("cdate"));
		} catch (SQLException ex) {
			this.cdate = rs.getString("cdate");
		}
		this.geox = rs.getString("geox");
		this.geoy = rs.getString("geoy");
	}

}
