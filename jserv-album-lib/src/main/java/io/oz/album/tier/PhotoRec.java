package io.oz.album.tier;

import static io.odysz.common.LangExt.isblank;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;

import io.odysz.anson.AnsonField;
import io.odysz.common.AESHelper;
import io.odysz.common.DateFormat;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.meta.ExpDocTableMeta.Share;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.IFileDescriptor;
import io.odysz.semantics.ISemantext;

/**
 * A sync object, server side and jprotocol oriented data record,
 * used for docsync.jserv. 
 * 
 * @author ody
 */
public class PhotoRec extends ExpSyncDoc implements IFileDescriptor {

	public String albumId;
	public String collectId;

	@AnsonField(ignoreTo=true, ignoreFrom=true)
	ISemantext semantxt;

	public String mime;
	
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
					meta.device,
					meta.fullpath,
					meta.shareby,
					meta.shareDate,
					meta.shareflag,
					meta.mime
			};
		return synpageCols;
	}

	public PhotoRec(AnResultset rs, PhotoMeta meta) throws SQLException {
		super(rs, meta);
	}

	public PhotoRec() { }

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

	public Exifield exif;
	public String geox;
	public String geoy;
	public String css;
	private String img;
	private String mov;
	private String wav;
	public String rotation;
	/** image size */
	public int[] widthHeight;
	/** reduction of image size */
	public int[] wh;
	
	protected String folder;
	public String folder() { return folder; }
	public ExpSyncDoc folder(String v) {
		this.folder = v;
		return this;
	}

	public PhotoRec collect(String cid) {
		collectId = cid;
		return this;
	}

	public PhotoRec createTest(String fullpath) throws IOException {
		File png = new File(fullpath);
		FileInputStream ifs = new FileInputStream(png);
		pname = png.getName();

		String b64 = AESHelper.encode64(ifs, 216); // 12 | 216, length = 219
		uri64 = b64;
		while (b64 != null) {
			b64 = AESHelper.encode64(ifs, 216); // FIXME this will padding useless bytes, what is happening when the file is saved at server side?
			if (b64 != null)
				uri64 += b64;
		}
		ifs.close();

		fullpath(fullpath);
		share("ody@kyiv", Share.pub, new Date());

		exif = new Exifield()
				.add("location", "вулиця Лаврська' 27' Київ")
				.add("camera", "Bayraktar TB2");

		return this;
	}

	public PhotoRec folder(AnResultset rs, PhotoMeta m) throws SQLException {
		super.folder(rs, m);
		this.css = rs.getString(m.css);
		this.img = rs.getString("img");
		this.mov = rs.getString("mov");
		this.wav = rs.getString("wav");
		return this;
	}

	
	public void month(Date d) {
		folder = DateFormat.formatYYmm(d);
	}

	public void month(FileTime d) {
		folder = DateFormat.formatYYmm(d);
	}

	public void month(String d) {
		try {
			folder = isblank(d) ?
				DateFormat.formatYYmm(new Date()) :
				DateFormat.formatYYmm(DateFormat.parse(d));
		} catch (ParseException e) {
			e.printStackTrace();
			folder = DateFormat.formatYYmm(new Date());
		}
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
	 * Parse {@link PathsPage#clientPaths}.
	 * 
	 * @param flags
	 * @return this
	public ExpSyncDoc parseFlags(String[] flags) {
		if (!isNull(flags)) {
			syncFlag = flags[0];
			shareflag = flags[1];
			shareby = flags[2];
			sharedate(flags[3]);
		}
		return this;
	}
	 */
	
//	@Override
//	public Insert insertEntity(SyntityMeta m, Insert ins) {
//		ExpDocTableMeta md = (ExpDocTableMeta) m;
//		ins // .nv(md.domain, domain)
//			.nv(md.folder, folder)
//			.nv(md.org, org)
//			.nv(md.mime, mime)
//			.nv(md.uri, uri64)
//			.nv(md.size, size)
//			.nv(md.createDate, createDate)
//			.nv(md.resname, pname)
//			.nv(md.synoder, device)
//			.nv(md.shareby, shareby)
//			.nv(md.shareDate, sharedate)
//			.nv(md.shareflag, shareflag)
//			.nv(md.fullpath, clientpath);
//		return ins;
//	}

//	public ExpSyncDoc createByChain(BlockChain chain) throws IOException {
//		createDate = chain.cdate;
//		fullpath(chain.clientpath);
//		pname = chain.clientname;
//		return this;
//	}

//	public ExpSyncDoc createByReq(DocsReq docreq) {
//		// TODO Auto-generated method stub
//		return null;
//	}
}