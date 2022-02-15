package io.oz.album.tier;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.image.ImageMetadataExtractor;
import org.xml.sax.SAXException;

import io.odysz.common.DateFormat;
import io.odysz.common.LangExt;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.parts.AbsPart;
import io.odysz.transact.sql.parts.condition.ExprPart;
import io.odysz.transact.sql.parts.condition.Funcall;

/**Server side and jprotocol oriented data record - not BaseFile used by file picker. 
 * @author ody
 *
 */
public class Photo extends FileRecord {
	public String pid;
	public String pname;
	public String uri;
	/** usally reported by client file system, overriden by exif date, if exits */
	public String createDate;
	public String shareby;
	public String sharedate;
	public String geox;
	public String geoy;
	public String exif;
	public String sharer;

	public String collectId;
	public String albumId;

	
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
			Date d = null;
			if (exif != null) {
				Metadata meta = new Metadata();
				new ImageMetadataExtractor(meta).parseRawExif(exif.getBytes());
				d = meta.getDate(TikaCoreProperties.CREATED);
			}
			else {
				pdate = createDate;
				if (pdate == null)
					d = new Date();
				else {
					try {
						d = DateFormat.parse(pdate);
					} catch (ParseException e) {
						d = new Date();
					}
				}
			}

			if (LangExt.isblank(pdate)) {
				month = DateFormat.formatYYmm(d);
				return Funcall.now();
			}
			else {
				month = DateFormat.formatYYmm(d);
				return new ExprPart(pdate);
			}
		} catch (SAXException | TikaException e ) {
			e.printStackTrace();
			throw new SemanticException(e.getMessage());
		}
	}
}