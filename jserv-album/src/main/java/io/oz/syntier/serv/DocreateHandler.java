package io.oz.syntier.serv;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.prefixOneOf;
import static io.odysz.transact.sql.parts.condition.Funcall.now;

import io.odysz.common.EnvPath;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.transact.sql.Update;
import io.oz.album.helpers.Exiftool;
import io.oz.album.peer.PhotoMeta;
import io.oz.album.peer.PhotoRec;
import io.oz.jserv.docs.syn.ExpDoctier.IOnDocreate;

public class DocreateHandler implements IOnDocreate {

	@Override
	public void onCreate(String conn, String docId, IUser usr, ExpDocTableMeta docm, String... path) {

		if (!(docm instanceof PhotoMeta)) return;
		
		PhotoMeta m = (PhotoMeta) docm;

		try {
			DATranscxt st = new DATranscxt(conn);
			AnResultset rs = (AnResultset) st
				.select(m.tbl, "p")
				.col(m.folder).col(m.fullpath)
				.col(m.uri)
				.col(m.resname)
				.col(m.createDate)
				.col(m.mime)
				.whereEq(m.pk, docId)
				.rs(st.instancontxt(conn, usr))
				.rs(0);

			if (rs.next() && isVedioAudio(rs.getString(m.mime))) {
				ISemantext stx = st.instancontxt(conn, usr);

				String pth = isNull(path)
							? EnvPath.decodeUri(stx, rs.getString(docm.uri))
							: path[0];

				PhotoRec p = new PhotoRec();
				Exiftool.parseExif(p, pth);

				Update u = st
					.update(m.tbl, usr)
					.nv(m.css, p.css)
					.nv(m.size, String.valueOf(p.size))
					.whereEq(m.pk, docId);

				if (isblank(rs.getDate(m.createDate)))
					u.nv(m.createDate, now());


					if (!isblank(p.geox) || !isblank(p.geoy))
						u.nv(m.geox, p.geox)
						 .nv(m.geoy, p.geoy);
					if (!isblank(p.exif))
						u.nv(m.exif, p.exif);
					else // figure out mime with file extension
						;

					if (!isblank(p.mime))
						u.nv(m.mime, p.mime);
				u.u(stx);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	static boolean isVedioAudio(String mime) {
		return isblank(mime) || prefixOneOf(mime, "audio/", "image/");
	}
}
