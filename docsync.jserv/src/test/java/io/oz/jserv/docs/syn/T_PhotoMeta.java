package io.oz.jserv.docs.syn;

import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.transact.x.TransException;

public class T_PhotoMeta extends ExpDocTableMeta {

	public final String exif;

	public T_PhotoMeta(String conn) throws TransException {
		super("h_photos", "pid", "device", conn);

		exif = "exif";

		try { ddlSqlite = loadSqlite(T_PhotoMeta.class, "h_photos.sqlite.ddl"); }
		catch (Exception e) { e.printStackTrace(); }
	}
}
