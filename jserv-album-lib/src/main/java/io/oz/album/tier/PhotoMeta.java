package io.oz.album.tier;

import io.odysz.semantic.ext.DocTableMeta;

public class PhotoMeta extends DocTableMeta {

	public final String device;

	public PhotoMeta(String conn) {
		super("h_phots", conn);
		
		pk = "pid";
		device = "device";
	}

}
