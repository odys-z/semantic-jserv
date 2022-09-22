package io.odysz.semantic.ext;

import io.odysz.semantics.meta.TableMeta;

public class DocTableMeta extends TableMeta {

	public final String syncflag;
	public final String shareflag;
	public final String filename;
	public final String uri;
	public final String createDate;
	public final String mime;
	public final String device;
	public final String fullpath;
	public final String shareDate;
	
	public DocTableMeta(String tbl, String conn) {
		super(tbl, conn);

		// TODO let's build from sync.xml
		tbl = "h_photos";
		pk = "pid";

		filename = "pname";
		uri = "uri";
		createDate = "pdate";
		mime = "mime";
		device = "device";
		fullpath = "clientpath";
		shareDate = "sharedate";

		syncflag = "syncflag";
		shareflag = "shareflag";
	}

}
