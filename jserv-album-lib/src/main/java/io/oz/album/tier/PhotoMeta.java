package io.oz.album.tier;

import java.sql.SQLException;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.meta.SynChangeMeta;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

public class PhotoMeta extends ExpDocTableMeta {

	public final String tags;
	public final String exif;
	public final String family;
	public final String geox;
	public final String geoy;
	public final String css;

	public PhotoMeta(String conn) throws TransException {
		super("h_photos", "pid", "synode", conn);
		
		tags   = "tags";
		exif   = "exif";
		family = "family";
		
		geox = "geox";
		geoy = "geoy";
		css = "css";
	}

	@Override
	public Object[] insertSelectItems(SynChangeMeta chgm, String entid, AnResultset entities, AnResultset changes)
			throws TransException, SQLException {
		throw new SemanticException("PhotoMeta should be deprecated in 2.0.0");
	}

}
