package io.odysz.semantic.ext;

import io.odysz.semantics.meta.TableMeta;

public class DocTableMeta extends TableMeta {
	public static class Share {
		public static final String pub = "pub";
		public static final String priv = "priv";
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
	
	public DocTableMeta(String tbl, String pk, String conn) {
		super(tbl, conn);

		// TODO let's build from sync.xml
		tbl = "h_photos";
		this.pk = pk;

		filename = "pname";
		uri = "uri";
		createDate = "pdate";
		org = "family";
		mime = "mime";
		device = "device";
		fullpath = "clientpath";
		shareDate = "sharedate";
		shareby = "shareby";

		syncflag = "sync";
		shareflag = "shareflag";
	}

}
