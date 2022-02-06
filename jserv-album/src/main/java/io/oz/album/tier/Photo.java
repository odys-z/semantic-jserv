package io.oz.album.tier;

import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.Date;

import org.apache.tika.parser.image.ImageMetadataExtractor;
import org.xml.sax.SAXException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

import io.odysz.common.DateFormat;
import io.odysz.common.LangExt;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jsession.SessionInf;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.parts.AbsPart;
import io.odysz.transact.sql.parts.condition.ExprPart;
import io.odysz.transact.sql.parts.condition.Funcall;

public class Photo extends FileRecord {
	/**<h5>Design Note</h5>
	 * The session info is typically not a DB entity, but it's necessary for protocol.
	 * So the {@link Photo} type doesn't means the same as DB record. 
	 * @see #collectId
	 */
	SessionInf ssInf;

	String pid;
	String pname;
	String uri;
	String shareby;
	String cdate;
	String geox;
	String geoy;
	String exif;
	String sharer;

	/**<h5>Design Note:</h5>
	 * The DB photo table doesn't has this filed. But the collection view need this for presentation.
	 * If provided, the presentation tier doesn't need to care the DB semantics anymore.
	 * So is this where the semantics stopped propagating?
	 * @see #ssInf
	 */
	String collectId;
	String albumId;

	
	String month;
	
	public Photo() {}
	
	public Photo(AnResultset rs) throws SQLException {
		this.pid = rs.getString("pid");
		this.pname = rs.getString("pname");
		this.uri = rs.getString("uri");
		try {
			this.cdate = DateFormat.formatime(rs.getDate("cdate"));
		} catch (SQLException ex) {
			this.cdate = rs.getString("cdate");
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
				pdate = cdate;
				d = DateFormat.parse(pdate);
			}

			if (LangExt.isblank(pdate)) {
				month = DateFormat.formatYYmm(new Date());
				return Funcall.now();
			}
			else {
				month = DateFormat.formatYYmm(d);
				return new ExprPart(pdate);
			}
		} catch (IOException | SAXException | TikaException | ParseException e) {
			e.printStackTrace();
			throw new SemanticException(e.getMessage());
		}
	}


}