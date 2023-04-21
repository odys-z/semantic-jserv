package io.oz.album.tier;

import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.transact.x.TransException;

public class PhotoMeta extends DocTableMeta {

	public final String exif;
	public final String folder;
	public final String family;

	public PhotoMeta(String conn) throws TransException {
		super("h_photos", "pid", conn);
		
		exif = "exif";
		folder = "folder";
		family = "family";
	}

}
