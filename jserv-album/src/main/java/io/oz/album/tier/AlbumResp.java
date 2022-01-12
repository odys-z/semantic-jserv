package io.oz.album.tier;

import java.sql.SQLException;

import io.odysz.common.DateFormat;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jprotocol.AnsonResp;

public class AlbumResp extends AnsonResp {

	String fileName;
	String id;
	String cdate;
	String geox;
	String geoy;

	public AlbumResp(AnResultset rs) throws SQLException {
		this.id = rs.getString("id");
		this.fileName = rs.getString("fileName");
		this.cdate = DateFormat.formatime(rs.getDate("cdate"));
		this.geox = rs.getString("geox");
		this.geoy = rs.getString("geoy");
	}

}
