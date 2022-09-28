package io.odysz.semantic.tier.docs;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonField;
import io.odysz.common.DateFormat;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.x.SemanticException;

/**
 * A sync object, server side and jprotocol oriented data record,
 * used for docsync.jserv. 
 * 
 * @author ody
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

	/** usually ignored when sending request
	public ArrayList<String> exif;
	public String exif() {
		return exif == null ? null
				: exif.stream()
				 .collect(Collectors.joining(","));
	}
	*/

	boolean isPublic;
	@Override
	public boolean isPublic() {
		return isPublic;
	}
	public SyncDoc isPublic(boolean pub) {
		isPublic = pub;
		return this;
	}

	public String mime;
	@Override
	public String mime() { return mime; }

	String month;
	
	public SyncDoc shareby(String share) {
		this.shareby = share;
		return this;
	}

	public SyncDoc sharedate(String format) {
		sharedate = format;
		return this;
	}

	public SyncDoc sharedate(Date date) {
		return sharedate(DateFormat.format(date));
	}

	@AnsonField(ignoreTo=true)
	DocTableMeta docMeta;

	ISemantext semantxt;
	
	public SyncDoc() {}
	
	/**
	 * A helper used to make sure query fields are correct.
	 * @param meta
	 * @return cols for Select.cols()
	 */
	public static String[] nvCols(DocTableMeta meta) {
		return new String[] {
				meta.pk,
				meta.filename,
				meta.uri,
				meta.createDate,
				meta.shareDate,
				meta.mime,
				meta.fullpath,
				meta.device
		};
	}
	
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
		this.docMeta = meta;
		this.recId = rs.getString(meta.pk);
		this.pname = rs.getString(meta.filename);
		this.uri = rs.getString(meta.uri);
		this.createDate = rs.getString(meta.createDate);
		this.mime = rs.getString(meta.mime);
		
		this.clientpath =  rs.getString(meta.fullpath);
		this.device =  rs.getString(meta.device);
		
		try {
			this.sharedate = DateFormat.formatime(rs.getDate(meta.shareDate));
		} catch (Exception ex) {
			this.sharedate = rs.getString(meta.createDate);
		}
	}

	/**Set client path and syncFlag
	 * @param rs
	 * @return this
	 * @throws SQLException
	 */
	public SyncDoc asSyncRec(AnResultset rs) throws SQLException {
		this.clientpath = rs.getString(docMeta.fullpath); 
		this.syncFlag = rs.getInt(docMeta.syncflag); 
		return this;
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

	protected String folder;
	public String folder() throws IOException, SemanticException {
		return folder;
	}

	public SyncDoc folder(String v) {
		this.folder = v;
		return this;
	}

//	@Override
//	public IFileDescriptor semantext(ISemantext stmtCtx) {
//		this.semantxt = stmtCtx;
//		return this;
//	}
//	
//	@Override
//	public ISemantext semantext() { return semantxt; }
	
}