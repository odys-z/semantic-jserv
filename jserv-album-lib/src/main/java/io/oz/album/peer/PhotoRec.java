package io.oz.album.peer;

import static io.odysz.common.LangExt.isblank;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;

import io.odysz.anson.AnsonField;
import io.odysz.common.AESHelper;
import io.odysz.common.DateFormat;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.ShareFlag;
import io.odysz.semantics.ISemantext;

/**
 * A sync object, server side and jprotocol oriented data record,
 * used for docsync.jserv. 
 * 
 * @author ody
 */
public class PhotoRec extends ExpSyncDoc {

	@AnsonField(ignoreTo=true, ignoreFrom=true)
	ISemantext semantxt;

	public PhotoRec(AnResultset rs, PhotoMeta meta) throws SQLException {
		super(rs, meta);
	}

	public PhotoRec() { }

	public Exifield exif;
//	public String mime;
	public String geox;
	public String geoy;
	public String css;
//	private String img;
//	private String mov;
//	private String wav;
	public String rotation;
	/** image size */
	public int[] widthHeight;
	/** reduction of image size */
	public int[] wh;
	
	public PhotoRec exifTest(String fullpath) throws IOException {
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
		share("ody@kyiv", ShareFlag.publish.name(), new Date());

		exif = new Exifield()
				.add("location", "вулиця Лаврська' 27' Київ")
				.add("camera", "Bayraktar TB2");

		return this;
	}

	public PhotoRec folder(AnResultset rs, PhotoMeta m) throws SQLException {
		super.folder(rs, m);
		this.css = rs.getString(m.css);
//		this.img = rs.getString("img");
//		this.mov = rs.getString("mov");
//		this.wav = rs.getString("wav");
		return this;
	}

	
	public void month(Date d) {
		folder = DateFormat.formatYY_mm(d);
	}

	public void month(FileTime d) {
		folder = DateFormat.formatYY_mm(d);
	}

	public void month(String d) {
		try {
			folder = isblank(d) ?
				DateFormat.formatYY_mm(new Date()) :
				DateFormat.formatYY_mm(DateFormat.parse(d));
		} catch (ParseException e) {
			e.printStackTrace();
			folder = DateFormat.formatYY_mm(new Date());
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
}