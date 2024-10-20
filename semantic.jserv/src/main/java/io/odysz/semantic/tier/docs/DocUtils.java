package io.odysz.semantic.tier.docs;

import java.io.IOException;
import java.sql.SQLException;

import io.odysz.common.EnvPath;
import io.odysz.common.LangExt;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.ShExtFilev2;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.tier.docs.SyncDoc.SyncFlag;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;

public class DocUtils {
	/**
	 * <p>Create a doc record with a local file, e.g. h_photos - call this after duplication is checked.</p>
	 * <p>This method will insert record, and can trigger ExtFilev2 handling.</p>
	 * <p>Doc is created as in the folder of user/[photo.folder]/;<br>
	 * Doc's device and family are replaced with session information.</p>
	 * 
	 * @since 1.4.19, this method need DB triggering timestamp ({@link DocTableMeta#stamp}).
	 * <pre>
	 * sqlite example:
	 * syncstamp DATETIME DEFAULT CURRENT_TIMESTAMP not NULL
	 * </pre>
	 * @param conn
	 * @param photo with photo.uri that is the entire base-64 encoded string
	 * @param usr
	 * @param meta 
	 * @param st 
	 * @param onFileCreateSql 
	 * @return doc id, e.g. pid
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException
	 */
	public static String createFileB64(String conn, SyncDoc photo, IUser usr, DocTableMeta meta, DATranscxt st, Update onFileCreateSql)
			throws TransException, SQLException, IOException {
		if (LangExt.isblank(photo.fullpath()))
			throw new SemanticException("Client path can't be null/empty.");
		
		if (LangExt.isblank(photo.folder(), " - - "))
			throw new SemanticException("Folder of managed doc can not be empty - which is important for saving file. It's required for creating media file.");

		Insert ins = st.insert(meta.tbl, usr)
				.nv(meta.org(), usr.orgId())
				.nv(meta.uri, photo.uri)
				.nv(meta.clientname, photo.pname)
				.nv(meta.synoder, usr.deviceId())
				.nv(meta.fullpath, photo.fullpath())
				.nv(meta.createDate, photo.createDate)
				.nv(meta.folder, photo.folder())
				.nv(meta.shareflag, photo.shareFlag)
				.nv(meta.shareby, photo.shareby)
				.nv(meta.shareDate, photo.sharedate)
				.nv(meta.size, photo.size)
				.nv(meta.syncflag, SyncFlag.publish) // temp for MVP 0.2.1
				;
		
		if (!LangExt.isblank(photo.mime))
			ins.nv(meta.mime, photo.mime);
		
		// add a synchronizing task
		// - also triggered as private storage jserv, but no statement will be added
		if (onFileCreateSql != null)
			ins.post(onFileCreateSql);

		ISemantext insCtx = st.instancontxt(conn, usr);
		SemanticObject res = (SemanticObject) ins.ins(insCtx);
		String pid = res.resulve(meta.tbl, meta.pk, -1);
		return pid;
	}

	/**
	 * Resolve file uri with configured Semantics handler, {@link smtype#extFile}.
	 * @param uri
	 * @param meta
	 * @param conn
	 * @return decode then concatenated absolute path, for file accessing.
	 * @see EnvPath#decodeUri(String, String)
	 */
	public static String resolvePrivRoot(String uri, DocTableMeta meta, String conn) {
		String extroot = ((ShExtFilev2) DATranscxt
				.getHandler(conn, meta.tbl, smtype.extFilev2))
				.getFileRoot();
		return EnvPath.decodeUri(extroot, uri);
	}

	public static String resolvExtroot(DATranscxt st, String conn, String docId, IUser usr, DocTableMeta meta) throws TransException, SQLException {
		ISemantext stx = st.instancontxt(conn, usr);
		AnResultset rs = (AnResultset) st
				.select(meta.tbl)
				.col("uri").col("folder")
				.whereEq("pid", docId).rs(stx)
				.rs(0);
	
		if (!rs.next())
			throw new SemanticException("Can't find file for id: %s (permission of %s)", docId, usr.uid());
	
		return resolvExtroot(conn, rs.getString("uri"), meta);
	}

	public static String resolvExtroot(String conn, String extUri, DocTableMeta meta) throws TransException, SQLException {
		ShExtFilev2 h2 = ((ShExtFilev2) DATranscxt.getHandler(conn, meta.tbl, smtype.extFilev2));
		if (h2 == null)
			throw new SemanticException("To resolv ext-root on db conn %s, table %s, this method need semantics extFilev2, to keep file path consists.",
					conn, meta.tbl);
		String extroot = h2.getFileRoot();
		return EnvPath.decodeUri(extroot, extUri);
	}
}
