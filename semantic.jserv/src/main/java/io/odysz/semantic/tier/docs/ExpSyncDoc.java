package io.odysz.semantic.tier.docs;

import static io.odysz.common.DateFormat.formatYYmm;
import static io.odysz.common.DateFormat.parse;
import static io.odysz.common.FilenameUtils.separatorsToUnix;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;

import io.odysz.anson.AnsonField;
import io.odysz.common.DateFormat;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.meta.SyntityMeta;
import io.odysz.transact.sql.Insert;
import io.oz.syn.SynEntity;

import static io.odysz.common.LangExt.*;

/**
 * A sync object, server side and jprotocol oriented data record,
 * used for docsync.jserv. 
 * 
 * @author ody
 */
public class ExpSyncDoc extends SynEntity implements IFileDescriptor {

	@Override
	public ExpSyncDoc recId(String did) {
		super.recId(did);
		return this;
	}

	public String pname;
	public String clientname() { return pname; }
	public ExpSyncDoc clientname(String clientname) {
		pname = clientname;
		return this;
	}

	public String clientpath;
	@Override
	public String fullpath() { return clientpath; }

	/** Non-public: doc' device id is managed by session. */
	protected String device;
	public String device() { return device; }
	public ExpSyncDoc device(String device) {
		this.device = device;
		return this;
	}
	public ExpSyncDoc device(Device device) {
		this.device = device.id;
		return this;
	}
	
	public String org;

	/** A constant field of {@link io.oz.album.peer.ShareFlag}. */
	public String shareflag;
	public String shareflag() { return shareflag; }
	public ExpSyncDoc shareflag(String f) {
		shareflag = f;
		return this;
	}

	/** Only for status report while uploading. */
	String shareMsg;
	/**
	 * @param f
	 * @param msg Only for status report while uploading.
	 * @return
	 */
	public ExpSyncDoc shareflag(ShareFlag f, String... msg) {
		shareflag(f.name());
		shareMsg = _0(msg);
		return this;
	}	

	/** Usually reported by client file system, and be overriden by exif date, if exits */
	public String createDate;
	public String cdate() { return createDate; }
	public ExpSyncDoc cdate(String cdate) {
		createDate = cdate;
		return this;
	}
	public ExpSyncDoc cdate(FileTime fd) {
		createDate = DateFormat.formatime(fd);
		return this;
	}
	public ExpSyncDoc cdate(Date date) {
		createDate = DateFormat.format(date);
		return this;
	}
	
	@AnsonField(shortenString=true)
	public String uri64;
	public String uri64() { return uri64; }
	public ExpSyncDoc uri64(String v64) {
		uri64 = v64;
		return this;
	}

	public String shareby;
	public String sharedate;
	
	public long size;
	public ExpSyncDoc size(long size) {
		this.size = size;
		return this;
	}

	public ExpSyncDoc shareby(String share) {
		this.shareby = share;
		return this;
	}

	public ExpSyncDoc sharedate(String format) {
		sharedate = format;
		return this;
	}

	public ExpSyncDoc sharedate(Date date) {
		return sharedate(DateFormat.format(date));
	}
	
	public ExpSyncDoc share(String shareby, String flag, String sharedate) {
		this.shareflag = flag;
		this.shareby = shareby;
		sharedate(sharedate);
		return this;
	}

	public ExpSyncDoc share(String shareby, String s, Date sharedate) {
		this.shareflag = s;
		this.shareby = shareby;
		sharedate(sharedate);
		return this;
	}

	@AnsonField(ignoreTo=true)
	// ExpDocTableMeta docMeta;

	public String mime;
	public ExpSyncDoc mime(String mime) {
		this.mime = mime;
		return this;
	}
	
	public ExpSyncDoc(SyntityMeta m, String orgId) {
		super(m);
		org = orgId;
	}
	
	public ExpSyncDoc() {
		super(null);
		this.org = "";
	}

	/**
	 * A helper used to make sure query fields are correct.
	 * @param meta
	 * @return cols for Select.cols()
	 */
	public static String[] nvCols(ExpDocTableMeta meta) {
		return new String[] {
				meta.org,
				meta.pk,
				meta.resname,
				meta.uri,
				meta.createDate,
				meta.shareDate,
				meta.shareby,
				meta.shareflag,
				meta.mime,
				meta.fullpath,
				meta.device,
				meta.folder,
				meta.size
		};
	}
	
	/**
	 * @param meta
	 * @return String [meta.pk, meta.shareDate, meta.shareflag, meta.syncflag]
	 */
	public static String[] synPageCols(ExpDocTableMeta meta) {
		if (synpageCols == null)
			synpageCols = new String[] {
					meta.pk,
					meta.device, // FIXME meta.synoder ?
					meta.fullpath,
					meta.shareby,
					meta.shareDate,
					meta.shareflag,
					meta.mime
			};
		return synpageCols;
	}

	public ExpSyncDoc(AnResultset rs, ExpDocTableMeta meta) throws SQLException {
		super(meta);
		// this.entMeta = meta;
		this.recId = rs.getString(meta.pk);
		this.org = rs.getString(meta.org);
		this.pname = rs.getString(meta.resname);
		this.uri64 = rs.getString(meta.uri);
		this.createDate = rs.getString(meta.createDate);
		this.size = rs.getLong(meta.size, 0);
		
		this.clientpath =  rs.getString(meta.fullpath);
		this.device =  rs.getString(meta.device);
		this.folder = rs.getString(meta.folder);
		
		try {
			this.sharedate = DateFormat.formatime_utc(rs.getDate(meta.shareDate));
		} catch (Exception ex) {
			this.sharedate = rs.getString(meta.createDate);
		}
		this.shareby = rs.getString(meta.shareby);
		this.shareflag = rs.getString(meta.shareflag);
		this.mime = rs.getString(meta.mime);
	}

	public ExpSyncDoc(IFileDescriptor file) {
		super(null);
		this.org = "";
		recId = file.recId();
		device = file.device();
		pname = file.clientname();
		createDate = file.cdate();
		clientpath = file.fullpath();
	}

	public ExpSyncDoc(ExpDocTableMeta m) {
		super(m);
		this.org = "";
	}

	public IFileDescriptor fullpath(String clientpath) throws IOException {
		this.clientpath = clientpath;
		Path p = Paths.get(clientpath);
		this.pname = p.getFileName().toString();

		if (isblank(createDate)) {
			try {
				FileTime fd = (FileTime) Files.getAttribute(p, "creationTime");
				cdate(fd);
			}
			catch (IOException ex) {
				cdate(new Date());
			}
		}

		return this;
	}

	protected String folder;
	public String folder() { return folder; }
	
	/**
	 * Set saving folder name. This method will trigger default folder name generation.
	 * 
	 * @param v
	 * @return this
	 * @since 0.5.16
	 */
	public ExpSyncDoc folder(String v) {
		this.folder = v;

        if (isblank(this.folder))
			try {
				folder = formatYYmm(isblank(createDate) ? new Date() : parse(createDate));
			} catch (ParseException e) {
				folder = formatYYmm(new Date());
			}
 
		return this;
	}
	
	/**
	 * @see io.oz.syn.SynEntity#insertEntity(io.odysz.semantic.meta.SyntityMeta, io.odysz.transact.sql.Insert)
	 */
	@Override
	public Insert insertEntity(SyntityMeta m, Insert ins) {
		ExpDocTableMeta md = (ExpDocTableMeta) m;
		ins // .nv(md.domain, domain)
			.nv(md.folder, folder)
			.nv(md.org, org)
			.nv(md.mime, mime)
			.nv(md.uri, uri64)
			.nv(md.size, size)
			.nv(md.createDate, createDate)
			.nv(md.resname, pname)
			.nv(md.device, device)
			.nv(md.shareby, shareby)
			.nv(md.shareDate, sharedate)
			.nv(md.shareflag, shareflag)
			.nv(md.fullpath, clientpath);
		return ins;
	}

	public ExpSyncDoc folder(AnResultset rs, ExpDocTableMeta m) throws SQLException {
		this.recId = rs.getString(m.pk);
		this.clientname(rs.getString(m.resname));
		this.mime = rs.getString(m.mime);
		this.clientname(rs.getString(m.resname));
		this.createDate = rs.getString(m.createDate);
		this.folder(rs.getString("pid"));
		this.shareby(rs.getString(m.shareby));
		return this;
	}

	/**
	 * @see #escapeClientpath()
	 * @param fullpath
	 * @return
	 */
	public ExpSyncDoc clientpath(String fullpath) {
		clientpath = separatorsToUnix(fullpath);
		return this;
	}

	/**
	 * Converts all separators to the Unix separator of forward slash,
	 * which is a valid json character.
	 * 
	 * @return this
	 */
	public ExpSyncDoc escapeClientpath() {
		clientpath = separatorsToUnix(clientpath);
		return this;
	}
}