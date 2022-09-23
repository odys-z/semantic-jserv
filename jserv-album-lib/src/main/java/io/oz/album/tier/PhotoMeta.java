package io.oz.album.tier;

import io.odysz.semantic.ext.DocTableMeta;

public class PhotoMeta extends DocTableMeta {

	public final String exif;
	public final String folder;
	public final String family;

	public PhotoMeta(String conn) {
		super("h_photos", "pid", conn);
		
		exif = "exif";
		folder = "folder";
		family = "family";
	}

}
