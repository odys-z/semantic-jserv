package io.oz.album.peer;

import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.transact.x.TransException;

/**
 * @author ody
 *
 */
public class PhotoMeta extends ExpDocTableMeta {

	public final String tags;
	public final String exif;
	public final String family;
	public final String geox;
	public final String geoy;
	public final String css;

	public PhotoMeta(String conn) throws TransException {
		super("h_photos", "pid", "device", conn);
		
		tags   = "tags";
		exif   = "exif";
		family = "family";
		
		geox = "geox";
		geoy = "geoy";
		css = "css";

		// [2026-08-15] v0.5.20
		// This old way is deprecated. ddlSqlite is hard coded
		// Disable this initialization as this field shouldn't be used, except tests.
		// ddlSqlite = loadSqlite(PhotoMeta.class, "h_photos.sqlite.ddl");
	}
}
