package io.odysz.semantic.ext;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;

import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.meta.SynChangeMeta;
import io.odysz.semantic.meta.SyntityMeta;
import io.odysz.transact.x.TransException;

/**
 * Document records' table meta.
 * <p>For Docsync.jserv, this meta is used for both client and server side.
 * But the client should never use it as a parameter of API - only use a
 * parameter of table name for specifying how the server should handle it.</p>
 *
 * FIXME shouldn't subclassed from SynTableMeta?
 * 
 * @author odys-z@github.com
 */
public class DocTableMeta extends SyntityMeta {
	/**
	 * consts of share type: pub | priv 
	 */
	public static class Share {
		/** public asset */
		public static final String pub = "pub";
		/** private asset */
		public static final String priv = "priv";

		public static boolean isPub(String s) {
			if (pub.equals(s)) return true;
			return false;
		}

		public static boolean isPriv(String s) {
			if (priv.equals(s)) return true;
			return false;
		}
	}

	/** DB column for automantic timesamp. 
	 * Sqlite:<pre>syncstamp DATETIME DEFAULT CURRENT_TIMESTAMP not NULL</pre>
	 */
	public final String stamp;
	/** resource's creating node's device id, originally named as device */
	// public final String synoder;
	public final String fullpath;
	/** aslo named as pname, clientname or filename previously */
	public final String clientname;
	/**
	 * Resource identity, reading with {@link io.odysz.transact.sql.parts.condition.Funcall.Func#extFile extFile}
	 * and updating with {@link io.odysz.semantic.DASemantics.ShExtFilev2 ShExtFile}.
	 */
	public final String uri;
	public final String createDate;
	public final String mime;
	public final String shareDate;
	public final String shareby;
	public final String folder;
	public final String size;

	public final String syncflag;
	public final String shareflag;
	final HashSet<String> globalIds;

	public DocTableMeta(String tbl, String pk, String conn) throws TransException {
		super("h_photos", pk, "family", conn);

		// TODO let's build from sync.xml
		// this.tbl = "h_photos";
		this.pk = pk;

		clientname = "pname";
		uri = "uri";
		folder = "folder";
		createDate = "pdate";
		mime = "mime";
		size = "filesize";
		// synoder = "device";
		fullpath = "clientpath";
		shareDate = "sharedate";
		shareby = "shareby";

		stamp = "syncstamp";
		syncflag = "sync";
		shareflag = "shareflag";
		
		// sharelog = new SharelogMeta(tbl, pk, conn); 
		globalIds = new HashSet<String>() { {add(synoder);}; {add(fullpath);}; };
	}

	@Override
	public HashSet<String> globalIds() {
		return globalIds;
	}

	@Override
	public ArrayList<Object[]> updateEntNvs(SynChangeMeta chgm, String entid, AnResultset entities,
			AnResultset challenges) throws TransException, SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Object[] insertSelectItems(SynChangeMeta chgm, String entid, AnResultset entities, AnResultset changes)
			throws TransException, SQLException {
		// TODO Auto-generated method stub
		return null;
	}

}
