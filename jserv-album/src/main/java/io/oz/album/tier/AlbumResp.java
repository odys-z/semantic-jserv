package io.oz.album.tier;

import java.sql.SQLException;
import java.util.ArrayList;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jprotocol.AnsonResp;

public class AlbumResp extends AnsonResp {

	String albumId;
	String ownerId;
	String owner;
	ArrayList<Collect> collectRecords;
	ArrayList<Photo[]> photos;
	Photo photo;

	public AlbumResp() throws SQLException {
	}
	
	public AlbumResp rec(AnResultset rs) throws SQLException {
		this.photo = new Photo(rs);
		return this;
	}

	/**Initialize album record
	 * @param rs
	 * @return
	 * @throws SQLException 
	 */
	public AlbumResp album(AnResultset rs) throws SQLException {
		this.albumId = rs.getString("aid");
		this.ownerId = rs.getString("ownerId");
		this.owner = rs.getString("owner");
		return this;
	}

	public AlbumResp collects(AnResultset rs) throws SQLException {
		this.collectRecords = new ArrayList<Collect>(rs.total());
		rs.beforeFirst();
		while(rs.next()) {
			collectRecords.add(new Collect(rs));
		}
		return this;
	}

	public AlbumResp photos(String collectId, AnResultset rs) throws SQLException {
		if (this.photos == null)
			this.photos = new ArrayList<Photo[]>(1);

		ArrayList<Photo> photos = new ArrayList<Photo>(rs.total());
		rs.beforeFirst();
		while(rs.next()) {
			photos.add(new Photo(collectId, rs));
		}

		this.photos.add(photos.toArray(new Photo[0]));
		return this;
	}

}
