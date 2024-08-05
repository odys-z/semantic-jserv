package io.odysz.semantic.tier.docs;

import static org.apache.commons.io_odysz.FilenameUtils.separatorsToUnix;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.util.Date;

import io.odysz.anson.AnsonField;
import io.odysz.common.DateFormat;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.meta.SyntityMeta;
import io.odysz.semantic.syn.SynEntity;
import io.odysz.semantics.ISemantext;
import io.odysz.transact.sql.Insert;
import static io.odysz.common.LangExt.*;

/**
 * A sync object, server side and jprotocol oriented data record,
 * used for docsync.jserv. 
 * 
 * @author ody
 */
public class ExpSyncDoc extends SynEntity implements IFileDescriptor {

	/*
	public String owner;
	public String docId;
	public String docName;
	public String createDate;
	public String mime;
	public String subfolder;

	/**
	 * Either {@link io.odysz.semantic.ext.DocTableMeta.Share#pub pub}
	 * or {@link io.odysz.semantic.ext.DocTableMeta.Share#pub priv}.
	 * /
	public String shareflag;

	public String shareby;
	public DocsReq shareby(String uid) {
		shareby = uid;
		return this;
	}

	public String shareDate;
	public String shareDate() {
		if (isblank(shareDate))
			shareDate = DateFormat.format(new Date());
		return shareDate;
	}
	
String clientpath;
	
	@AnsonField(shortenString = true)
	public String uri64;
	*/

	// protected static String[] synpageCols;

	public String recId;
	public String recId() { return recId; }
	public ExpSyncDoc recId(String did) {
		recId = did;
		return this;
	}

	public String pname;
	public String clientname() { return pname; }
	public ExpSyncDoc clientname(String clientname) {
		pname = clientname;
		return this;
	}

	public String clientpath;
	@Override public String fullpath() { return clientpath; }

	@Override public String mime() { return mime; }

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

	public String shareflag;
	public String shareflag() { return shareflag; }

	/** usally reported by client file system, overriden by exif date, if exits */
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

	public String shareby;
	public String sharedate;
	
	// public String syncFlag;

	/** usually ignored when sending request */
	public long size;

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
	ExpDocTableMeta docMeta;

	@AnsonField(ignoreTo=true, ignoreFrom=true)
	ISemantext semantxt;

	public String mime;
	
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
				meta.pk,
				meta.resname,
				meta.uri,
				meta.createDate,
				meta.shareDate,
				meta.shareby,
				meta.shareflag,
				meta.mime,
				meta.fullpath,
				meta.synoder,
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
					meta.synoder,
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
		this.docMeta = meta;
		this.recId = rs.getString(meta.pk);
		this.org = rs.getString(meta.org);
		this.pname = rs.getString(meta.resname);
		this.uri64 = rs.getString(meta.uri);
		this.createDate = rs.getString(meta.createDate);
		this.size = rs.getLong(meta.size, 0);
		
		this.clientpath =  rs.getString(meta.fullpath);
		this.device =  rs.getString(meta.synoder);
		this.folder = rs.getString(meta.folder);
		
		try {
			this.sharedate = DateFormat.formatime(rs.getDate(meta.shareDate));
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
	}

	/**
	 * Load local file, take current time as sharing date.
	 * @param meta 
	 * @param fullpath
	 * @param owner
	 * @param shareflag
	 * @return this
	 * @throws IOException
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
	 */

//	public SyncDoc(IFileDescriptor d, String fullpath, DocTableMeta meta) throws IOException, SemanticException {
//		this.device = d.device();
//
//		this.docMeta = meta;
//		this.recId = d.recId();
//		this.pname = d.clientname();
//		this.uri = d.uri();
//		this.createDate = d.cdate();
//		this.mime = d.mime();
//		this.fullpath(fullpath);
//	}

	public IFileDescriptor fullpath(String clientpath) throws IOException {
		this.clientpath = clientpath;

		if (isblank(createDate)) {
			try {
				Path p = Paths.get(clientpath);
				FileTime fd = (FileTime) Files.getAttribute(p, "creationTime");
				cdate(fd);
			}
			catch (IOException ex) {
				cdate(new Date());
			}
		}

		return this;
	}

	/**Set (private) jserv node file full path (path replaced with %VOLUME_HOME)
	 * @param path
	 * @return
	 * @throws SemanticException 
	 * @throws IOException 
	public IFileDescriptor uri(String path) throws SemanticException, IOException {
		fullpath(path);
		pname = FilenameUtils.getName(path);
		// throw new SemanticException("TODO");
		this.uri = null;
		return this;
	}
	 */

	protected String folder;
	public String folder() { return folder; }
	public ExpSyncDoc folder(String v) {
		this.folder = v;
		return this;
	}

//	public SyncDoc parseMimeSize(String abspath) throws IOException {
//		mime = isblank(mime)
//				? Files.probeContentType(Paths.get(abspath))
//				: mime;
//
//		File f = new File(abspath);
//		size = f.length();
//		return this;
//	}

//	public SyncDoc parseChain(BlockChain chain) throws IOException {
//		createDate = chain.cdate;
//
//		device = chain.device;
//		clientpath = chain.clientpath;
//		pname = chain.clientname;
//		folder(chain.saveFolder);
//
//		shareby = chain.shareby;
//		sharedate = chain.shareDate;
//		shareflag = chain.shareflag;
//
//		return parseMimeSize(chain.outputPath);
//	}

	/**
	 * @deprecated deleting ...
	 * 
	 * Parse {@link PathsPage#clientPaths}.
	 * 
	 * @param flags
	 * @return this
	 */
	public ExpSyncDoc parseFlags(String[] flags) {
		if (!isNull(flags)) {
			// syncFlag = flags[0];
			shareflag = flags[1];
			shareby = flags[2];
			sharedate(flags[3]);
		}
		return this;
	}
	
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
			.nv(md.synoder, device)
			.nv(md.shareby, shareby)
			.nv(md.shareDate, sharedate)
			.nv(md.shareflag, shareflag)
			.nv(md.fullpath, clientpath);
		return ins;
	}

	public ExpSyncDoc createByChain(BlockChain chain) throws IOException {
		createDate = chain.cdate;
		fullpath(chain.clientpath);
		pname = chain.clientname;
		// uri = null; // accepting new value
		return this;
	}

	public ExpSyncDoc createByReq(DocsReq docreq) {
		// TODO Auto-generated method stub
		return null;
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

	public ExpSyncDoc clientpath(String fullpath) {
		clientpath = separatorsToUnix(fullpath);
		return this;
	}
	
//	public static Query cols(Query q, ExpDocTableMeta meta) throws TransException {
//		return q.cols(
//				q.alias().sql(null) + "." + meta.pk,
//				meta.resname, meta.createDate,
//				meta.folder, meta.fullpath, meta.synoder,
//				meta.uri, meta.shareDate,
//				// meta.tags, meta.geox, meta.css, meta.geoy, 
//				meta.mime)
//			.col(isnull(meta.shareflag, ExprPart.constr(Share.priv)), meta.shareflag);
//	}
}