package io.oz.album.tier;

import java.io.IOException;
import java.sql.SQLException;

import io.odysz.common.LangExt;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Update;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;

public class DocUtils {
	/**
	 * <p>Create a file, e.g. photo - call this after duplication is checked.</p>
	 * <p>TODO: to be replaced by SyncWorkerTest.createFileB64()</p>
	 * <p>Photo is created as in the folder of user/month/.</p>
	 * 
	 * @param conn
	 * @param photo
	 * @param usr
	 * @param photoMeta 
	 * @param st 
	 * @param onFileCreateSql 
	 * @return pid
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException
	 */
	public static String createFile(String conn, Photo photo, IUser usr, PhotoMeta photoMeta, DATranscxt st, Update onFileCreateSql)
			throws TransException, SQLException, IOException {
		if (LangExt.isblank(photo.clientpath))
			throw new SemanticException("Client path can't be null/empty.");
		
		if (LangExt.isblank(photo.month(), " - - "))
			throw new SemanticException("Month of photo creating is important for saving files. It's required for creating media file.");

		Insert ins = st.insert(photoMeta.tbl, usr)
				.nv(photoMeta.family, usr.orgId())
				.nv(photoMeta.uri, photo.uri)
				.nv(photoMeta.filename, photo.pname)
				.nv(photoMeta.device, photo.device)
				.nv(photoMeta.fullpath, photo.fullpath())
				.nv(photoMeta.createDate, photo.photoDate())
				.nv(photoMeta.folder, photo.month())
				.nv("geox", photo.geox).nv("geoy", photo.geoy)
				.nv(photoMeta.exif, photo.exif)
				.nv(photoMeta.shareflag, photo.isPublic ? DocTableMeta.Share.pub : DocTableMeta.Share.priv)
				.nv(photoMeta.shareby, usr.uid())
				.nv(photoMeta.shareDate, Funcall.now())
				;
		
		if (!LangExt.isblank(photo.mime))
			ins.nv("mime", photo.mime);
		
		// add a synchronizing task
		// - also triggered as private storage jserv, but no statement will be added
		/// Docsyncer.onDocreate(ins, photo, photoMeta.tbl, usr);
		if (onFileCreateSql != null)
			ins.post(onFileCreateSql);

		ISemantext insCtx = st.instancontxt(conn, usr);
		// photo.semantext(insCtx);
		SemanticObject res = (SemanticObject) ins.ins(insCtx);
		String pid = ((SemanticObject) ((SemanticObject) res.get("resulved"))
				.get(photoMeta.tbl))
				.getString(photoMeta.pk);
		
//		if (photo.geox == null || photo.month == null)
//			onPhotoCreated(pid, conn, usr);

		return pid;
	}
}
