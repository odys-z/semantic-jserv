package io.oz.album.tier;

import java.sql.SQLException;

import io.odysz.common.DateFormat;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jsession.SessionInf;

public class Photo implements FileRecord {
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

	@Override
	public String recId() { return pid; }

	@Override
	public String uri() { return uri; }

	@Override
	public String token() { return ssInf == null ? null : ssInf.ssid(); }
}