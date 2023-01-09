package io.odysz.semantic.ext;

import io.odysz.semantics.meta.TableMeta;

/**
 * Document records' table meta.
 * <p>For Docsync.jserv, this meta is used for both client and server side.
 * But the client should never use it as a parameter of API - only use a
 * parameter of table name for specifying how the server should handle it.</p>
 *
 * @author odys-z@github.com
 */
public class DocTableMeta extends TableMeta {
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
	public final String syncflag;
	public final String shareflag;
	public final String filename;
	public final String uri;
	public final String createDate;
	public final String mime;
	public final String device;
	public final String fullpath;
	public final String shareDate;
	public final String shareby;
	public final String org;
	public final String folder;
	public final String size;

	// public final SharelogMeta sharelog;
	
	public DocTableMeta(String tbl, String pk, String conn) {
		super(tbl, conn);

		// TODO let's build from sync.xml
		tbl = "h_photos";
		this.pk = pk;

		filename = "pname";
		uri = "uri";
		folder = "folder";
		createDate = "pdate";
		org = "family";
		mime = "mime";
		size = "filesize";
		device = "device";
		fullpath = "clientpath";
		shareDate = "sharedate";
		shareby = "shareby";

		stamp = "syncstamp";
		syncflag = "sync";
		shareflag = "shareflag";
		
		// sharelog = new SharelogMeta(tbl, pk, conn); 
	}

}
