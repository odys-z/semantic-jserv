package io.odysz.semantic.ext;

import io.odysz.semantics.meta.TableMeta;

public class DocTableMeta extends TableMeta {
	public static final class SyncFlag {
		/**
		 * created at cloud hub ('✩') by client (not jnode),
		 * and to be synchronized by private / main jnode.
		 * 
		 * <p> pub &amp; {@link #hubInit} -- [sync request] --&gt; {@link #publish}
		 * <p> prv &amp; {@link #hubInit} -- [sync request] --&gt; null
		 */
		public static final String hubInit = "✩";
		/**
		 * <p>kept as private file ('🔒')</p>
		 * If the jnode is working on hub mode, the file record can be removed later
		 * according expire and storage limitation. 
		 */
		public static final String priv = "🔒";
		/**
		 * to be pushed (shared) to hub ('⇈')
		 */
		public static final String pushing = "⇈";
	
		/**
		 * synchronized (shared) with hub ('🌎')
		 */
		public static final String publish = "🌎";
		
	}

	public static class Share {
		/** public asset */
		public static final String pub = "pub";
		/** private asset */
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
		size = "size";
		device = "device";
		fullpath = "clientpath";
		shareDate = "sharedate";
		shareby = "shareby";

		syncflag = "sync";
		shareflag = "shareflag";
	}

}
