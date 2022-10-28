package io.odysz.semantic.ext;

import io.odysz.semantics.meta.TableMeta;

public class DocTableMeta extends TableMeta {
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

		syncflag = "sync";
		shareflag = "shareflag";
	}

}
