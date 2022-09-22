package io.oz.album.tier;

import java.io.IOException;
import java.sql.SQLException;

import io.odysz.common.LangExt;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Update;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;

public class DocUtils {
	/**
	 * <p>Create photo - call this after duplication is checked.</p>
	 * <p>TODO: replaced by SyncWorkerTest.createFileB64()</p>
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
	public static String createFile(String conn, Photo photo, IUser usr, TableMeta photoMeta, DATranscxt st, Update onFileCreateSql)
			throws TransException, SQLException, IOException {
		if (LangExt.isblank(photo.clientpath))
			throw new SemanticException("Client path can't be null/empty.");
		
		if (LangExt.isblank(photo.month(), " - - "))
			throw new SemanticException("Month of photo creating is important for saving files. It's required for creating media file.");

		Insert ins = st.insert(photoMeta.tbl, usr)
				.nv("family", usr.orgId())
				.nv("uri", photo.uri).nv("pname", photo.pname)
				.nv("pdate", photo.photoDate())
				.nv("folder", photo.month())
				// .nv("device", ((PhotoRobot) usr).deviceId())
				// .nv("clientpath", photo.clientpath)
				.nv("geox", photo.geox).nv("geoy", photo.geoy)
				.nv("exif", photo.exif)
				// .nv("syncflag", photo.isPublic ? DocsReq.sharePublic : DocsReq.sharePrivate)
				 .nv("shareby", usr.uid())
				 .nv("sharedate", Funcall.now())
				;
		
		if (!LangExt.isblank(photo.mime))
			ins.nv("mime", photo.mime);
		
		// add a synchronizing task
		// - also triggered as private storage jserv, but no statement will be added
		/// Docsyncer.onDocreate(ins, photo, photoMeta.tbl, usr);
		if (onFileCreateSql != null)
			ins.post(onFileCreateSql);

		SemanticObject res = (SemanticObject) ins.ins(st.instancontxt(conn, usr));
		String pid = ((SemanticObject) ((SemanticObject) res.get("resulved"))
				.get(photoMeta.tbl))
				.getString(photoMeta.pk);
		
//		if (photo.geox == null || photo.month == null)
//			onPhotoCreated(pid, conn, usr);

		return pid;
	}


}
