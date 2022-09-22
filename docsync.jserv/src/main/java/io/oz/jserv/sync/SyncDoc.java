package io.oz.jserv.sync;

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
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.tier.docs.IFileDescriptor;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.parts.AbsPart;
import io.odysz.transact.sql.parts.condition.ExprPart;
import io.odysz.transact.sql.parts.condition.Funcall;

/**
 * Server side and jprotocol oriented data record,
 * and sync object used for docsync.jserv. 
 * 
 * @author ody
 *
 */
public class SyncDoc extends Anson implements IFileDescriptor {
	public String recId;
	public String recId() { return recId; }

	public String pname;
	@Override
	public String clientname() { return pname; }

	public String clientpath;
	@Override
	public String fullpath() { return clientpath; }

	public String device;
	@Override
	public String device() { return device; }
	
	public int syncFlag;
	/** usally reported by client file system, overriden by exif date, if exits */
	public String createDate;
	@Override
	public String cdate() { return createDate; }
	
	@AnsonField(shortenString=true)
	public String uri;
	@Override
	public String uri() { return uri; }

	public String shareby;
	public String sharedate;
	
	/** usually ignored when sending request */
	public long size;

	/** usually ignored when sending request */
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
	@Override
	public String mime() { return mime; }

	String month;
	
	public SyncDoc() {}
	
	public SyncDoc(AnResultset rs, DocTableMeta meta) throws SQLException {
		/*
		this.recId = rs.getString("pid");
		this.pname = rs.getString("pname");
		this.uri = rs.getString("uri");
		this.month = rs.getString("folder");
		this.createDate = rs.getString("pdate");
		this.mime = rs.getString("mime");
		this.clientpath =  rs.getString("clientpath");
		this.device =  rs.getString("device");
		*/
		this.recId = rs.getString(meta.pk);
		this.pname = rs.getString(meta.filename);
		this.uri = rs.getString(meta.uri);
		this.createDate = rs.getString(meta.createDate);
		this.mime = rs.getString(meta.mime);
		
		// TODO debug
		this.clientpath =  rs.getString(meta.fullpath);
		this.device =  rs.getString(meta.device);
		
		try {
			this.sharedate = DateFormat.formatime(rs.getDate(meta.shareDate));
		} catch (SQLException ex) {
			this.sharedate = rs.getString(meta.createDate);
		}
		
	}

	public SyncDoc(String collectId, AnResultset rs, DocTableMeta meta) throws SQLException {
		this(rs, meta);
	}

	/**Set client path and syncFlag
	 * @param rs
	 * @return this
	 * @throws SQLException
	 */
	public SyncDoc asSyncRec(AnResultset rs) throws SQLException {
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
	public IFileDescriptor fullpath(String clientpath) throws IOException {
		this.clientpath = clientpath;
		return this;
	}

	/**Set (private) jserv node file full path (path replaced with %VOLUME_HOME)
	 * @param path
	 * @return
	 */
	public IFileDescriptor uri(String path) {
		
		return this;
	}

}