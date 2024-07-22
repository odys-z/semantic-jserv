package io.oz.album.tier;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.eq;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;

import io.odysz.anson.AnsonField;
import io.odysz.common.AESHelper;
import io.odysz.common.CheapMath;
import io.odysz.common.DateFormat;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.ext.DocTableMeta.Share;
import io.odysz.semantic.tier.docs.IFileDescriptor;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.parts.AbsPart;
import io.odysz.transact.sql.parts.condition.ExprPart;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;

/**
 * Server side and jprotocol oriented data record - not BaseFile used by file picker (at Android client).
 *
 * @author ody
 *
 */
public class PhotoRec extends SyncDoc implements IFileDescriptor {
	public String geox;
	public String geoy;

	/** usually ignored when sending request */
	public Exifield exif;
	public Exifield exif() { return exif; }

	/** image size */
	public int[] widthHeight;
	/** reduction of image size */
	public int[] wh;
	
	public String rotation;

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
		if (widthHeight != null) {
			if (isblank(wh))
				wh = eq(rotation, "90") || eq(rotation, "270") 
						? CheapMath.reduceFract(widthHeight[1], widthHeight[0])
						: CheapMath.reduceFract(widthHeight[0], widthHeight[1]);

			return String.format("{\"type\":\"io.oz.album.tier.PhotoCSS\", \"size\":[%s,%s,%s,%s], \"roation\": \"%s\"}",
						widthHeight[0], widthHeight[1], wh[0], wh[1], isblank(rotation) ? "" : rotation);
		}
		else return "";
	}

	public String collectId;
	public String collectId() { return collectId; }

	public String albumId;

	int orgs;

	@AnsonField(ignoreTo=true)
	PhotoMeta meta;

	public PhotoRec() { }

	public PhotoRec(AnResultset rs, PhotoMeta m) throws SQLException, IOException {
		this.meta = m;
		this.recId = rs.getString(m.pk);
		this.pname = rs.getString(m.clientname);
		this.folder = rs.getString(m.folder);
		this.createDate = rs.getString(m.createDate);
		this.shareby = rs.getString(m.shareby);
		this.shareFlag = rs.getString(m.shareflag);
		this.geox = rs.getString(m.geox);
		this.geoy = rs.getString(m.geoy);
		this.mime = rs.getString(m.mime);

		this.css = rs.getString(m.css);

		fullpath(rs.getString(m.fullpath));
		this.device =  rs.getString(m.synoder);

		try {
			this.sharedate = DateFormat.formatime(rs.getDate("sharedate"));
		} catch (SQLException ex) {
			this.sharedate = rs.getString("pdate");
		}
		this.geox = rs.getString("geox");
		this.geoy = rs.getString("geoy");
		
		this.orgs = rs.getInt("orgs", 0);
	}
	
	public PhotoRec(String collectId, AnResultset rs, PhotoMeta m) throws SQLException, IOException {
		this(rs, m);
		this.collectId = collectId;
	}
	
	public static Query cols(Query q, PhotoMeta meta) throws TransException {
		return q.cols(q.alias().sql(null) + "." + meta.pk,
					meta.clientname, meta.createDate,
					meta.folder, meta.fullpath, meta.synoder,
					meta.uri, meta.shareDate, meta.tags,
					meta.geox, meta.geoy,
					meta.mime, meta.css)
				.col(Funcall.isnull(meta.shareflag, ExprPart.constr(Share.priv)), meta.shareflag);
	}

	/**
	 * Set client path and syncFlag
	 * 
	 * @param rs
	 * @return this
	 * @throws SQLException
	 * @throws IOException
	 */
	public PhotoRec asSyncRec(AnResultset rs) throws SQLException, IOException {
		fullpath(rs.getString(meta.fullpath));
		this.syncFlag = rs.getString(meta.syncflag);
		return this;
	}

	public String folder() {
		if (folder == null)
			photoDate();
		return folder;
	}

	public AbsPart photoDate() {
		try {
			if (!isblank(createDate)) {
				Date d = DateFormat.parse(createDate);
				folder = DateFormat.formatYYmm(d);
				return new ExprPart("'" + createDate + "'");
			}
			else {
				Date d = new Date();
				folder = DateFormat.formatYYmm(d);
				return Funcall.now();
			}
		} catch (ParseException e ) {
			e.printStackTrace();
			Date d = new Date();
			folder = DateFormat.formatYYmm(d);
			return Funcall.now();
		}
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

	@Override
	public IFileDescriptor fullpath(String clientpath) throws IOException {
		super.fullpath(clientpath);

		if (isblank(folder)) {
			month(cdate());
		}
		return this;
	}

	public SyncDoc shareflag(String share) {
		shareFlag = share;
		return this;
	}

	public PhotoRec createTest(String fullpath) throws IOException {
		File png = new File(fullpath);
		FileInputStream ifs = new FileInputStream(png);
		pname = png.getName();

		String b64 = AESHelper.encode64(ifs, 216); // 12 | 216, length = 219
		uri = b64;
		while (b64 != null) {
			b64 = AESHelper.encode64(ifs, 216); // FIXME this will padding useless bytes, what is happening when the file is saved at server side?
			if (b64 != null)
				uri += b64;
		}
		ifs.close();

		fullpath(fullpath);
		share("ody@kyiv", Share.pub, new Date());

		exif = new Exifield()
				.add("location", "вулиця Лаврська' 27' Київ")
				.add("camera", "Bayraktar TB2");

		return this;
	}

	/** folder image count */
	String img; 
	/** folder video count */
	String mov; 
	/** folder audio count */
	String wav; 

	public PhotoRec folder(AnResultset rs, PhotoMeta m) throws SQLException {
		this.recId = rs.getString(m.pk);
		this.css = rs.getString(m.css);
		this.clientname(rs.getString(m.clientname));
		this.img = (rs.getString("img"));
		this.mov = (rs.getString("mov"));
		this.wav = (rs.getString("wav"));
		this.mime = rs.getString(m.mime);
		this.clientname(rs.getString(m.clientname));
		this.createDate = rs.getString(m.createDate);
		this.folder(rs.getString("pid"));
		this.shareby(rs.getString(m.shareby));
		return this;
	}

}
