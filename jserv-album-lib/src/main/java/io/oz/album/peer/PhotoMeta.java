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

		ddlSqlite = loadSqlite(PhotoMeta.class, "h_photos.sqlite.ddl");
	}

//	@Override
//	public Object[] insertSelectItems(SynChangeMeta chgm, String entid, AnResultset entities, AnResultset changes)
//			throws TransException, SQLException {
//		throw new SemanticException("PhotoMeta should be deprecated in 2.0.0");
//	}

}
