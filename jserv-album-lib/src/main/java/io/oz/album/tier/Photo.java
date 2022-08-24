package io.oz.album.tier;

import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.stream.Collectors;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonField;
import io.odysz.common.DateFormat;
import io.odysz.common.LangExt;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jserv.file.ISyncFile;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.parts.AbsPart;
import io.odysz.transact.sql.parts.condition.ExprPart;
import io.odysz.transact.sql.parts.condition.Funcall;

/**Server side and jprotocol oriented data record - not BaseFile used by file picker. 
 * @author ody
 *
 */
public class Photo extends Anson implements ISyncFile {
	public String recId;
	public String recId() { return recId; }

	public String pname;

	public String clientpath;

	public String device;
	
	public int syncFlag;
	/** usally reported by client file system, overriden by exif date, if exits */
	public String createDate;
	
	@AnsonField(shortenString=true)
	public String uri;
	public String shareby;
	public String sharedate;
	public String geox;
	public String geoy;
	public String sharer;

	public ArrayList<String> exif;
	public String exif() {
		return exif == null ? null
				: exif.stream()
				 .collect(Collectors.joining(","));
	}

	boolean isPublic;
	@Override
	public boolean isPublic() {
		return isPublic;
	}

	public String mime;
	/** image size */
	public int[] widthHeight;
	/** reduction of image size */
	public int[] wh;
	
	/**
	 * Composed css json, saved as string.
	 * @see #css()
	 * */
	public String css;

	/**
	 * Compose a string representing json object for saving in DB.
	 * The type "io.oz.album.tier.PhotoCSS" doesn't exist at server side (v0.4.18)
	 * 
	 * @return string of json for saving
	 */
	public String css() {
		if (widthHeight != null)
			return String.format("{\"type\":\"io.oz.album.tier.PhotoCSS\", \"size\":[%s,%s,%s,%s]}",
				widthHeight[0], widthHeight[1], wh[0], wh[1]);
		else return "";
	}

	public String collectId;
	public String collectId() { return collectId; }

	public String albumId;
	
	String month;
	
	public Photo() {}
	
	public Photo(AnResultset rs) throws SQLException {
		this.recId = rs.getString("pid");
		this.pname = rs.getString("pname");
		this.uri = rs.getString("uri");
		this.month = rs.getString("folder");
		this.createDate = rs.getString("pdate");
		this.shareby = rs.getString("owner");
		this.geox = rs.getString("geox");
		this.geoy = rs.getString("geoy");
		this.mime = rs.getString("mime");
		
		this.css = rs.getString("css");
		
		// TODO debug
		this.clientpath =  rs.getString("clientpath");
		this.device =  rs.getString("device");
		
		try {
			this.sharedate = DateFormat.formatime(rs.getDate("sharedate"));
		} catch (SQLException ex) {
			this.sharedate = rs.getString("pdate");
		}
		this.geox = rs.getString("geox");
		this.geoy = rs.getString("geoy");
		
	}

	public Photo(String collectId, AnResultset rs) throws SQLException {
		this(rs);
		this.collectId = collectId;
	}

	/**Set client path and syncFlag
	 * @param rs
	 * @return this
	 * @throws SQLException
	 */
	public Photo asSyncRec(AnResultset rs) throws SQLException {
		this.clientpath = rs.getString("clientpath"); 
		this.syncFlag = rs.getInt("syncFlag"); 
		return this;
	}

	public String month() throws IOException, SemanticException  {
		if (month == null)
			photoDate();
		return month;
	}

	public AbsPart photoDate() throws IOException, SemanticException {
		try {
			if (!LangExt.isblank(createDate)) {
				Date d = DateFormat.parse(createDate); 
				month = DateFormat.formatYYmm(d);
				return new ExprPart("'" + createDate + "'");
			}
			else {
				Date d = new Date();
				month = DateFormat.formatYYmm(d);
				return Funcall.now();
			}
		} catch (ParseException e ) {
			e.printStackTrace();
			throw new SemanticException(e.getMessage());
		}
	}

	public void month(Date d) {
		month = DateFormat.formatYYmm(d);
	}

	public void month(FileTime d) {
		month = DateFormat.formatYYmm(d);
	}

	@Override
	public String fullpath() {
		return clientpath;
	}

	@Override
	public String device() {
		return device;
	}


}