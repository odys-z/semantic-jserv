package io.oz.album.tier;

import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantics.x.SemanticException;

public class PhotoMeta extends DocTableMeta {

	public final String exif;
	public final String folder;
	public final String family;

	public PhotoMeta(String conn) throws SemanticException {
		super("h_photos", "pid", conn);
		
		exif = "exif";
		folder = "folder";
		family = "family";
	}

}
