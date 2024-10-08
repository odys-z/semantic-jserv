package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.replacele;
import static io.odysz.common.Utils.loadTxt;
import static io.odysz.transact.sql.parts.condition.Funcall.constr;
import static io.odysz.transact.sql.parts.condition.Funcall.extfile;

import java.sql.SQLException;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.meta.SynChangeMeta;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Query;
import io.odysz.transact.x.TransException;

public class T_PhotoMeta extends ExpDocTableMeta {

	public final String exif;

	public T_PhotoMeta(String conn) throws TransException {
		super("h_photos", "pid", "device", conn);

		exif = "exif";

		try { ddlSqlite = loadTxt(T_PhotoMeta.class, "h_photos.sqlite.ddl"); }
		catch (Exception e) { e.printStackTrace(); }
	}

	@Override
	public Object[] insertSelectItems(SynChangeMeta chgm, String entid,
			AnResultset entities, AnResultset changes)
			throws SemanticException, SQLException {
		Object[] cols = entCols();
		Object[] selects = new Object[cols.length];
		for (int cx = 0; cx < cols.length; cx++) {
			String val = entities.getStringByIndex((String)cols[cx], entid);
			if (val != null)
				selects[cx] = constr(val);
		}
		return selects;
	}

	@Override
	public Query onselectSyntities(Query select) throws TransException {
		String a = tbl; 
		if (select.alias() != null)
			a = select.alias().toString();

		return select
				.clos_clear()
				.cols_byAlias(a,
				replacele(entCols(), uri, extfile(a + "." + uri)));
	}

}
