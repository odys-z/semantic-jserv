package io.oz.album.tier;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import io.odysz.anson.Anson;
import io.odysz.module.rs.AnResultset;

/**
 * TODO  to be renamed to PhotoCollect,
 * 
 * see Anclient/js/anreact/src/photo-gallery/src/tier/photo-rec.ts
 * 
 * @author Ody
 */
public class Collect extends Anson {

	String tags;
	String cdate;
	String shareby;
	String cname;
	String cid;

	ArrayList<PhotoRec> photos;
	
	public Collect() {}

	/**
	 * set fields: cid, cname, shareby, cdate, tags
	 * 
	 * @param rs
	 * @throws SQLException
	 */
	public Collect(AnResultset rs) throws SQLException {
		this.cid = rs.getString("cid");
		this.cname = rs.getString("cname");
		this.shareby = rs.getString("shareby");
		this.cdate = rs.getString("cdate");
		this.tags = rs.getString("tags");
	}

	public Collect addPhoto(AnResultset rs) throws SQLException, IOException {
		if (photos == null)
			photos = new ArrayList<PhotoRec>();
		PhotoRec p = new PhotoRec(rs);
		photos.add(p);
		return this;
	}

}
