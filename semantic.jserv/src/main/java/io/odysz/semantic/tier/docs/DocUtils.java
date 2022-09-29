package io.odysz.semantic.tier.docs;

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
	 * @param meta 
	 * @param st 
	 * @param onFileCreateSql 
	 * @return pid
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException
	 */
	public static String createFile(String conn, SyncDoc photo, IUser usr, DocTableMeta meta, DATranscxt st, Update onFileCreateSql)
			throws TransException, SQLException, IOException {
		if (LangExt.isblank(photo.clientpath))
			throw new SemanticException("Client path can't be null/empty.");
		
		if (LangExt.isblank(photo.folder(), " - - "))
			throw new SemanticException("Folder of managed doc can not be empty - which is important for saving file. It's required for creating media file.");

		Insert ins = st.insert(meta.tbl, usr)
				.nv(meta.org, usr.orgId())
				.nv(meta.uri, photo.uri)
				.nv(meta.filename, photo.pname)
				.nv(meta.device, photo.device)
				.nv(meta.fullpath, photo.fullpath())
				.nv(meta.createDate, photo.createDate)
				.nv(meta.folder, photo.folder())
				.nv(meta.shareflag, photo.shareflag)
				.nv(meta.shareby, photo.shareby)
				.nv(meta.shareDate, photo.sharedate)
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
				.get(meta.tbl))
				.getString(meta.pk);
		
//		if (photo.geox == null || photo.month == null)
//			onPhotoCreated(pid, conn, usr);

		return pid;
	}
}
