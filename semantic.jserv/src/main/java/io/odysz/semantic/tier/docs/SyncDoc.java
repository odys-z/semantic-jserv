package io.odysz.semantic.tier.docs;

import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static org.apache.commons.io_odysz.FilenameUtils.separatorsToUnix;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.util.Date;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonField;
import io.odysz.common.DateFormat;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.ext.DocTableMeta.Share;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.x.SemanticException;

/**
 * A sync object, server side and jprotocol oriented data record,
 * used for docsync.jserv. 
 * 
 * TODO extending SyncEntity
 * 
 * @author ody
 */
public class SyncDoc extends Anson implements IFileDescriptor {
	/** Temporary type for album's MVP version */
	public final static class SyncFlag extends Anson {
		/** kept as private file ('🔒') at private node.
		 * TODO rename as jnode */
		public static final String priv = "🔒";

		/** to be pushed (shared) to hub ('⇈')
		 * <p>This is a temporary state and is handled the same as the {@link #priv}
		 * for {@link io.odysz.semantic.tier.docs.SyncDoc SyncDoc}'s state.
		 * The only difference is the UI and broken link handling.
		 * It's complicate but nothing about FSM.</p> */
		public static final String pushing = "⇈";

		/**
		 * synchronized (shared) with hub ('🌎')
		 * */
		public static final String publish = "🌎";
		/**created at cloud hub ('✩') by both client and jnode pushing, */
		public static final String hub = "✩";
		
		/**created at a device (client) node ('📱') */
		public static final String device = "📱";
		/**The doc is removed, and this record is a propagating record for
		 * worldwide synchronizing ('Ⓓ')*/
		public static final String deleting = "Ⓓ";
		/**The doc is locally removed, and the task is waiting to push to a jnode ('Ⓛ') */
		public static final String loc_remove = "Ⓛ";
		/**The deleting task is denied by a device ('ⓧ')*/
		public static final String del_deny = "ⓧ";
		/** hub buffering expired or finished ('Ⓒ') */
		public static final String close = "Ⓒ";
		/** This state can not present in database */ 
		public static final String end = "";

		public static final String deny = "⛔";
		public static final String invalid = "⚠";
		
		public static String start(SynodeMode mode, String share) throws SemanticException {
			if (SynodeMode.peer == mode)
				return Share.isPub(share) ? publish : hub;
//			else if (SynodeMode.bridge == mode || SynodeMode.main == mode)
//				return priv;
			throw new SemanticException("Unhandled state starting: mode %s : share %s.", mode, share);
	}
	}
	
	protected static String[] synpageCols;

	public String recId;
	public String recId() { return recId; }
	public SyncDoc recId(String did) {
		recId = did;
		return this;
	}

	public String pname;
	@Override
	public String clientname() { return pname; }
	public SyncDoc clientname(String clientname) {
		pname = clientname;
		return this;
	}

	protected String clientpath;
	@Override
	public String fullpath() { return clientpath; }

	/** Non-public: doc' device id is managed globally.
	 * @since 0.6.50:temp-try, a device has an auto-key and a name.
	 */
	protected String device;
	@Override
	public String device() { return device; }
	public SyncDoc device(String device) {
		this.device = device;
		return this;
	}

	public SyncDoc device(Device device) {
		this.device = device.id;
		return this;
	}
	
	/** Non-public: doc' device id is managed globally.
	 * @since 0.6.50:temp-try, a device has an auto-key and a name.
	 */
	protected String devname;
	public String devname() { return devname; }
	public SyncDoc devname(String devname) {
		this.devname = devname;
		return this;
	}

	/** Either {@link io.odysz.semantic.ext.DocTableMeta.Share#pub pub} or {@link io.odysz.semantic.ext.DocTableMeta.Share#pub priv}. */
	public String shareFlag;
	@Override
	/** Either {@link io.odysz.semantic.ext.DocTableMeta.Share#pub pub} or {@link io.odysz.semantic.ext.DocTableMeta.Share#pub priv}. */
	public String shareflag() { return shareFlag; }

	/** usally reported by client file system, overriden by exif date, if exits */
	public String createDate;
	@Override
	public String cdate() { return createDate; }
	public SyncDoc cdate(String cdate) {
		if (isblank(cdate))
			return cdate(new Date()); 
		createDate = cdate;
		return this;
	}
	public SyncDoc cdate(FileTime fd) {
		createDate = DateFormat.formatime(fd);
		return this;
	}

	public SyncDoc cdate(Date date) {
		createDate = DateFormat.format(date);
		return this;
	}

	
	@AnsonField(shortenString=true)
	public String uri;
	@Override
	public String uri() { return uri; }

	public String shareby;
	public String sharedate;
	
	/**
	 * Const string values of {@link SyncFlag}.
	 */
	public String syncFlag;

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
	
	public SyncDoc share(String shareby, String flag, String sharedate) {
		this.shareFlag = flag;
		this.shareby = shareby;
		sharedate(sharedate);
		return this;
	}

	public SyncDoc share(String shareby, String flag, Date sharedate) {
		this.shareFlag = flag;
		this.shareby = shareby;
		sharedate(sharedate);
		return this;
	}

	public SyncDoc share(String shareby, String flag) {
		return share(shareby, flag, new Date());
	}
	
	@AnsonField(ignoreTo=true)
	protected DocTableMeta docMeta;

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
				meta.clientname,
				meta.uri,
				meta.createDate,
				meta.shareDate,
				meta.shareby,
				meta.shareflag,
				meta.syncflag,
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
	public static String[] synPageCols(DocTableMeta meta) {
		if (synpageCols == null)
			synpageCols = new String[] {
					meta.pk,
					meta.synoder,
					meta.fullpath,
					meta.shareby,
					meta.shareDate,
					meta.shareflag,
					meta.syncflag
			};
		return synpageCols;
	}

	public SyncDoc(AnResultset rs, DocTableMeta meta) throws SQLException {
		this.docMeta = meta;
		this.recId = rs.getString(meta.pk);
		this.pname = rs.getString(meta.clientname);
		this.uri = rs.getString(meta.uri);
		this.createDate = rs.getString(meta.createDate);
		this.mime = rs.getString(meta.mime);
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
		this.shareFlag = rs.getString(meta.shareflag);
		this.syncFlag = rs.getString(meta.syncflag);
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

	/**
	 * @param d
	 * @param fullpath
	 * @param meta
	 * @throws IOException checking local file failed
	 * @throws SemanticException device is null
	 */
	public SyncDoc(IFileDescriptor d, String fullpath, DocTableMeta meta) throws IOException, SemanticException {
		this.device = d.device();

		this.docMeta = meta;
		this.recId = d.recId();
		this.pname = d.clientname();
		this.uri = d.uri();
		this.createDate = d.cdate();
		this.mime = d.mime();
		this.fullpath(fullpath);
		
        this.shareFlag = Share.pub;
        this.syncFlag = SyncFlag.device;
	}

	@Override
	public IFileDescriptor fullpath(String clientpath) throws IOException {
		this.clientpath = separatorsToUnix(clientpath);
		return this;
	}

	protected String folder;
	public String folder() { return folder; }
	public SyncDoc folder(String v) {
		this.folder = v;
		return this;
	}

	public SyncDoc parseMimeSize(String abspath) throws IOException {
		mime = isblank(mime)
				? Files.probeContentType(Paths.get(abspath))
				: mime;

		File f = new File(abspath);
		size = f.length();
		return this;
	}

	public SyncDoc parseChain(BlockChain chain) throws IOException {
		createDate = chain.cdate;

		device = chain.device;
		clientpath = chain.clientpath;
		pname = chain.clientname;
		folder(chain.saveFolder);

		shareby = chain.shareby;
		sharedate = chain.shareDate;
		shareFlag = chain.shareflag;

		return parseMimeSize(chain.outputPath);
	}

	/**
	 * Parse {@link PathsPage#clientPaths}.
	 * 
	 * @param flags
	 * @return this
	 */
	public SyncDoc parseFlags(String[] flags) {
		if (!isNull(flags)) {
			syncFlag = flags[0];
			shareFlag = flags[1];
			shareby = flags[2];
			sharedate(flags[3]);
		}
		return this;
	}
	
	public SyncDoc syncFlag(String f) {
		syncFlag = f;
		return this;
	}
}