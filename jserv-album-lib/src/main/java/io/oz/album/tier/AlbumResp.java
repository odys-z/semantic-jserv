package io.oz.album.tier;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.transact.x.TransException;

public class AlbumResp extends DocsResp {

	String albumId;
	String ownerId;
	String owner;

	/** Album */
	ArrayList<Collect> collectRecords;

	List<?> forest;
	public AlbumResp albumForest(List<?> forest) {
		this.forest = forest;
		return this;
	}

	Profiles profils;
	public Profiles profiles() { return profils; }

	ArrayList<PhotoRec[]> photos;
	public PhotoRec[] photos(int px) { return photos == null ? null : photos.get(px); }

	PhotoRec photo;
	public PhotoRec photo() { return photo; }

	public AlbumResp() { }
	
	public AlbumResp photo(AnResultset rs, PhotoMeta meta) throws SQLException, IOException {
		this.photo = new PhotoRec(rs, meta);
		return this;
	}

	public AlbumResp folder(AnResultset rs, PhotoMeta m) throws SQLException {
		this.photo = new PhotoRec().folder(rs, m);
		return this;
	}

	public AlbumResp photo(PhotoRec photo, String ... pid) {
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
	 * @param conn 
	 * @return this
	 * @throws SQLException 
	 * @throws IOException 
	 * @throws TransException 
	 */
	public AlbumResp collectPhotos(AnResultset rs, String conn) throws SQLException, IOException, TransException {
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
			collect.addPhoto(rs, new PhotoMeta(conn, null, null));
		}
		if (collect != null)
			collectRecords.add(collect);

		return this;
	}

	public AlbumResp photos(String collectId, AnResultset rs, PhotoMeta meta) throws SQLException, IOException {
		if (this.photos == null)
			this.photos = new ArrayList<PhotoRec[]>(1);

		ArrayList<PhotoRec> photos = new ArrayList<PhotoRec>(rs.total());
		rs.beforeFirst();
		while(rs.next()) {
			photos.add(new PhotoRec(collectId, rs, meta));
		}

		this.photos.add(photos.toArray(new PhotoRec[0]));
		return this;
	}
	
	public AlbumResp profiles(Profiles profiles) {
		this.profils = profiles;
		return this;
	}

	public DocsResp collect(String id) {
		collectId = id;
		return this;
	}
}
