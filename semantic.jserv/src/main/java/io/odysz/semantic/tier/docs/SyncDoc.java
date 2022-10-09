package io.odysz.semantic.tier.docs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Date;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonField;
import io.odysz.common.AESHelper;
import io.odysz.common.DateFormat;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
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
	
	/** Either {@link io.odysz.semantic.ext.DocTableMeta.Share#pub pub} or {@link io.odysz.semantic.ext.DocTableMeta.Share#pub priv}. */
	public String shareflag;
	@Override
	/** Either {@link io.odysz.semantic.ext.DocTableMeta.Share#pub pub} or {@link io.odysz.semantic.ext.DocTableMeta.Share#pub priv}. */
	public String shareflag() { return shareflag; }

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
	
	public int syncFlag;

	/** usually ignored when sending request */
	public long size;

	public String mime;
	@Override
	public String mime() { return mime; }

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

	@AnsonField(ignoreTo=true, ignoreFrom=true)
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
				meta.shareby,
				meta.shareflag,
				meta.mime,
				meta.fullpath,
				meta.device,
				meta.folder
		};
	}
	
	public SyncDoc(AnResultset rs, DocTableMeta meta) throws SQLException {
		this.docMeta = meta;
		this.recId = rs.getString(meta.pk);
		this.pname = rs.getString(meta.filename);
		this.uri = rs.getString(meta.uri);
		this.createDate = rs.getString(meta.createDate);
		this.mime = rs.getString(meta.mime);
		
		// this.isPublic = Share.pub.equals(rs.getString(meta.shareflag, null));
		this.clientpath =  rs.getString(meta.fullpath);
		this.device =  rs.getString(meta.device);
		
		try {
			this.sharedate = DateFormat.formatime(rs.getDate(meta.shareDate));
		} catch (Exception ex) {
			this.sharedate = rs.getString(meta.createDate);
		}
		this.shareby = rs.getString(meta.shareby);
		this.shareflag = rs.getString(meta.shareflag);
	}

	/**
	 * Load local file, take current time as sharing date.
	 * @param fullpath
	 * @param owner
	 * @param shareflag
	 * @return this
	 * @throws IOException
	 */
	public SyncDoc loadFile(String fullpath, IUser owner, String shareflag) throws IOException {
		Path p = Paths.get(fullpath);
		byte[] f = Files.readAllBytes(p);
		String b64 = AESHelper.encode64(f);
		this.uri = b64;

		fullpath(fullpath);
		this.pname = p.getFileName().toString();
		
		this.shareby = owner.uid();
		this.shareflag = shareflag;
		sharedate(new Date());

		return this;
	}

	/**
	 * Set client path and syncFlag according to rs, where rs columns should have been specified with {@link #nvCols(DocTableMeta)}.
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
	 * @throws SemanticException 
	 */
	public IFileDescriptor uri(String path) throws SemanticException {
		
		// return this;
		throw new SemanticException("TODO");
	}

	protected String folder;
	public String folder() throws IOException, SemanticException {
		return folder;
	}

	public SyncDoc folder(String v) {
		this.folder = v;
		return this;
	}
}