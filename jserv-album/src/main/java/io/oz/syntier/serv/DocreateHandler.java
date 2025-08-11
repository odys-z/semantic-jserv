package io.oz.syntier.serv;

import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.prefixOneOf;
import static io.odysz.transact.sql.parts.condition.Funcall.now;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.tier.docs.DocUtils;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.transact.sql.Update;
import io.oz.album.helpers.Exiftool;
import io.oz.album.peer.PhotoMeta;
import io.oz.album.peer.PhotoRec;
import io.oz.jserv.docs.syn.ExpDoctier.IOnDocreate;

/**
 * @since 0.2.0
 */
public class DocreateHandler implements IOnDocreate {
	
	public DocreateHandler() throws InterruptedException, IOException, TimeoutException {
		Exiftool.init();
	}

	@Override
	public void onCreate(String conn, String docId, DATranscxt st, IUser usr, ExpDocTableMeta docm, String... path) {

		if (!(docm instanceof PhotoMeta)) return;
		
		PhotoMeta phm = (PhotoMeta) docm;

		try {
			AnResultset rs = (AnResultset) st
				.select(docm.tbl, "p")
				.col(docm.folder).col(docm.fullpath)
				.col(docm.uri)
				.col(docm.resname)
				.col(docm.createDate)
				.col(docm.mime)
				.whereEq(docm.pk, docId)
				.rs(st.instancontxt(conn, usr))
				.rs(0);

			if (rs.next() && isVedioAudio(rs.getString(docm.mime))) {
				ISemantext stx = st.instancontxt(conn, usr);

				String pth = isNull(path)
							// FIXME use ExtFilePath instead
							// ? EnvPath.decodeUri(stx, rs.getString(docm.uri))
							? DocUtils.resolvExtroot(conn, rs.getString(docm.uri), docm)
							: path[0];

				PhotoRec p = new PhotoRec();
				Exiftool.parseExif(p, pth);

				Update u = st
					.update(docm.tbl, usr)
					.nv(phm.css, p.css())
					.nv(docm.size, String.valueOf(p.size))
					.whereEq(docm.pk, docId);

				if (isblank(rs.getDate(docm.createDate)))
					u.nv(docm.createDate, now());


					if (!isblank(p.geox) || !isblank(p.geoy))
						u.nv(phm.geox, p.geox)
						 .nv(phm.geoy, p.geoy);
					if (!isblank(p.exif))
						u.nv(phm.exif, p.exif);
					else // figure out mime with file extension
						;

					if (!isblank(p.mime))
						u.nv(docm.mime, p.mime);
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
