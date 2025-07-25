package io.odysz.semantic.tier.docs;

import static io.odysz.common.LangExt.ifnull;
import static io.odysz.common.LangExt.isNull;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Date;

import io.odysz.common.DateFormat;
import io.odysz.common.EnvPath;
import io.odysz.common.LangExt;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.ShExtFilev2;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.meta.ExpDocTableMeta;
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
	 * @deprecated replaced by {@link #createFileBy64(DATranscxt, String, ExpSyncDoc, IUser, ExpDocTableMeta, Update...)}
	 * @param st
	 * @param conn
	 * @param photo
	 * @param usr
	 * @param meta
	 * @param onFileCreateSql
	 * @return doc id
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException
	 */
	public static String createFileB64(DATranscxt st, String conn, ExpSyncDoc photo,
			IUser usr, ExpDocTableMeta meta, Update onFileCreateSql)
			throws TransException, SQLException, IOException {
		if (LangExt.isblank(photo.fullpath()))
			throw new SemanticException("The client path can't be null/empty.");
		
		if (LangExt.isblank(photo.folder(), " - - "))
			throw new SemanticException("Folder of managed docs cannot be empty - which is required for creating media files.");

		Insert ins = st
			.insert(meta.tbl, usr)
			// .nv(meta.domain, usr.orgId())
			.nv(meta.org, photo.org)
			.nv(meta.uri, photo.uri64)
			.nv(meta.resname, photo.pname)
			.nv(meta.device, usr.deviceId())
			.nv(meta.fullpath, photo.fullpath())
			.nv(meta.createDate, photo.createDate)
			.nv(meta.folder, photo.folder())
			.nv(meta.shareflag, photo.shareflag)
			.nv(meta.shareby, photo.shareby)
			.nv(meta.shareDate, photo.sharedate)
			.nv(meta.size, photo.size)
			// .nv(meta.syncflag, SyncFlag.publish) // temp for MVP 0.2.1
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
	 * Create doc record in table meta.tbl, and trigger syn-change semantics,
	 * if configured semantics.xml/t/s [id = syn-change, t = meta.tbl]
	 * 
	 * @param st
	 * @param conn
	 * @param doc
	 * @param usr
	 * @param meta
	 * @param onFileCreateSql
	 * @return doc-id
	 * @throws TransException
	 * @throws SQLException
	 */
	public static String createFileBy64(DATranscxt st, String conn,
			ExpSyncDoc doc, IUser usr, ExpDocTableMeta meta, Update... onFileCreateSql) throws TransException, SQLException {
		if (LangExt.isblank(doc.fullpath()))
			throw new SemanticException("The client path can't be null/empty.");
		
		if (LangExt.isblank(doc.folder(), " - - "))
			throw new SemanticException("Folder of managed docs cannot be empty - which is required for creating media files.");

		Insert ins = st
			.insert(meta.tbl, usr)
			.nv(meta.org, ifnull(doc.org, usr.orgId()))
			.nv(meta.uri, ifnull(doc.uri64, ""))
			.nv(meta.resname, doc.pname)
			.nv(meta.device, doc.device)
			.nv(meta.fullpath, doc.fullpath())
			.nv(meta.createDate, ifnull(doc.createDate, DateFormat.formatime(new Date())))
			.nv(meta.folder, doc.folder())
			.nv(meta.shareflag, ifnull(doc.shareflag, ShareFlag.prv.name()))
			.nv(meta.shareby, ifnull(doc.shareby, usr.uid()))
			.nv(meta.shareDate, ifnull(doc.sharedate, DateFormat.format(new Date())))
			.nv(meta.size, doc.size)
			;
		
		if (!LangExt.isblank(doc.mime))
			ins.nv(meta.mime, doc.mime);
		
		// add a synchronizing task
		// - also triggered as private storage jserv, but no statement will be added
		if (!isNull(onFileCreateSql))
			ins.post(onFileCreateSql[0]);

		ISemantext insCtx = st.instancontxt(conn, usr);
		SemanticObject res = (SemanticObject) ins.ins(insCtx);
		return res.resulve(meta.tbl, meta.pk, -1);
	}

	/**
	 * Resolve file uri with configured Semantics handler, {@link smtype#extFile}.
	 * @param uri
	 * @param meta
	 * @param conn
	 * @return decode then concatenated absolute path, for file accessing.
	 * @see EnvPath#decodeUri(String, String)
	 */
	public static String resolvePrivRoot(String uri, ExpDocTableMeta meta, String conn) {
		String extroot = ((ShExtFilev2) DATranscxt
				.getHandler(conn, meta.tbl, smtype.extFilev2))
				.getFileRoot();
		return EnvPath.decodeUri(extroot, uri);
	}

	public static String resolvExtroot(DATranscxt st, String conn, String docId, IUser usr, ExpDocTableMeta meta)
			throws TransException, SQLException {
		ISemantext stx = st.instancontxt(conn, usr);
		AnResultset rs = (AnResultset) st
				.select(meta.tbl)
				.col(meta.uri).col(meta.folder)
				.whereEq(meta.pk, docId)
				.rs(stx)
				.rs(0);
	
		if (!rs.next())
			throw new SemanticException("Can't find file for id: %s (permission of %s)", docId, usr.uid());
	
		return resolvExtroot(conn, rs.getString(meta.uri), meta);
	}

	/**
	 * According to smtype.extFilev2's configuration for meta.tbl,
	 * get path by concatenating and replacing env.
	 * 
	 * @param conn
	 * @param extUri
	 * @param meta
	 * @return (smtype.extFilev2[meta.tbl]'s arg0 / extUri).replace-env
	 * @throws TransException
	 * @throws SQLException
	 */
	public static String resolvExtroot(String conn, String extUri, ExpDocTableMeta meta)
			throws TransException, SQLException {

		ShExtFilev2 h2 = ((ShExtFilev2) DATranscxt.getHandler(conn, meta.tbl, smtype.extFilev2));
		if (h2 == null)
			throw new SemanticException(
				"To resolv ext-root on db conn %s, table %s, this method need semantics extFilev2, to keep file path consists.",
				conn, meta.tbl);
		String extroot = h2.getFileRoot();
		return EnvPath.decodeUri(extroot, extUri);
	}
}
