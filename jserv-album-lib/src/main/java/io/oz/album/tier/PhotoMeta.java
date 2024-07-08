package io.oz.album.tier;

import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.transact.x.TransException;

public class PhotoMeta extends DocTableMeta {

	public final String tags;
	public final String exif;
	public final String family;
	public final String geox;
	public final String geoy;
	public final String css;

	public PhotoMeta(String conn, String domain, String synode) throws TransException {
		super("h_photos", "pid", domain, synode, conn);
		
		tags   = "tags";
		exif   = "exif";
		family = "family";
		
		geox = "geox";
		geoy = "geoy";
		css = "css";
	}

}
