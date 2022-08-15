package io.oz.album.tier;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.tier.docs.DocsResp;

public class AlbumResp extends DocsResp {

	String albumId;
	String ownerId;
	String owner;

	/** Album */
	ArrayList<Collect> collectRecords;

	Profiles profils;

	ArrayList<Photo[]> photos;
	public Photo[] photos(int px) { return photos == null ? null : photos.get(px); }

	HashMap<String, Object> clientPaths;
	public HashMap<String, Object> syncPaths() { return clientPaths; }
	
	Photo photo;
	public Photo photo() { return photo; }

	public AlbumResp() { }
	
	public AlbumResp rec(AnResultset rs) throws SQLException {
		this.photo = new Photo(rs);
		return this;
	}

	public AlbumResp photo(Photo photo, String ... pid) {
		this.photo = photo;
		if (pid != null && pid.length > 0)
			this.photo.recId = pid[0];
		return this;
	}

	/**Initialize album record
	 * @param rs
	 * @return response object
	 * @throws SQLException 
	 */
	public AlbumResp album(AnResultset rs) throws SQLException {
		this.albumId = rs.getString("aid");
		this.ownerId = rs.getString("ownerId");
		this.owner = rs.getString("owner");
		return this;
	}

	/**
	 * Construct an array of {@link Collect}.
	 * @param rs
	 * @return
	 * @throws SQLException
	 */
	public AlbumResp setCollects(AnResultset rs) throws SQLException {
		this.collectRecords = new ArrayList<Collect>(rs.total());
		rs.beforeFirst();
		while(rs.next()) {
			collectRecords.add(new Collect(rs));
		}
		return this;
	}

	/**
	 * Construct a 2D array of photos: [collect: photo[]]
	 * 
	 * @param rs photos ordered by cid
	 * @return this
	 * @throws SQLException 
	 */
	public AlbumResp collectPhotos(AnResultset rs) throws SQLException {
		String cid = "";
		Collect collect = null;
		
		this.collectRecords = new ArrayList<Collect>(rs.total());
		rs.beforeFirst();
		while(rs.next()) {
			if (collect == null || !cid.equals(rs.getString("cid"))) {
				if (collect != null)
					collectRecords.add(collect);
				collect = new Collect(rs);
				cid = collect.cid;
			}
			collect.addPhoto(rs);
		}
		// collectRecords.add(new Collect(rs));
		if (collect != null)
			collectRecords.add(collect);

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
	
	public AlbumResp syncRecords(String collectId, AnResultset rs) throws SQLException {
		clientPaths = new HashMap<String, Object>();

		rs.beforeFirst();
		while(rs.next()) {
			clientPaths.put(rs.getString("clientpath"), rs.getString("syncFlag"));
		}

		return this;
	}

	public AlbumResp profiles(Profiles profiles) {
		this.profils = profiles;
		return this;
	}
}
