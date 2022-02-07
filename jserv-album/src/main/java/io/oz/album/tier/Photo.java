package io.oz.album.tier;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;

import org.apache.tika.parser.image.ImageMetadataExtractor;
import org.xml.sax.SAXException;
import org.apache.james.mime4j.dom.datetime.DateTime;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

import io.odysz.common.DateFormat;
import io.odysz.common.LangExt;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.parts.AbsPart;
import io.odysz.transact.sql.parts.condition.ExprPart;
import io.odysz.transact.sql.parts.condition.Funcall;

public class Photo extends FileRecord {
	String pid;
	String pname;
	String uri;
	String shareby;
	String sharedate;
	String geox;
	String geoy;
	String exif;
	String sharer;

	String collectId;
	String albumId;

	
	String month;
	
	public Photo() {}
	
	public Photo(AnResultset rs) throws SQLException {
		this.pid = rs.getString("pid");
		this.pname = rs.getString("pname");
		this.uri = rs.getString("uri");
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

	public String month() throws IOException, SemanticException  {
		if (month == null)
			photoDate();
		return month;
	}

	public AbsPart photoDate() throws IOException, SemanticException {
		try {
			String pdate = null;
			Date d;
			if (exif != null) {
				Metadata meta = new Metadata();
				new ImageMetadataExtractor(meta).parseRawExif(exif.getBytes());
				d = meta.getDate(TikaCoreProperties.CREATED);
			}
			else {
//				pdate = sharedate;
//				d = DateFormat.parse(pdate);
				d = new Date();
			}

			if (LangExt.isblank(pdate)) {
				month = DateFormat.formatYYmm(new Date());
				return Funcall.now();
			}
			else {
				month = DateFormat.formatYYmm(d);
				return new ExprPart(pdate);
			}
		} catch (IOException | SAXException | TikaException e) {
			e.printStackTrace();
			throw new SemanticException(e.getMessage());
		}
	}


}