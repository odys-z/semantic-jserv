package io.oz.album.tier;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.tier.docs.DocsReq;

public class AlbumReq extends DocsReq {

	/** media file state */
	static enum fileState {
		uploading("uploading"),
		valid("valid"),
		synchronizing("synching"),
		archive("archive"),
		shared("shared");

		@SuppressWarnings("unused")
		private String state;

		fileState(String state) {
			this.state = state;
		}
	}
	
	static public class A {
		public static final String records = "r/collects";
		public static final String collect = "r/photos";
		public static final String rec = "r/photo";
		public static final String download = "r/download";
		public static final String upload = "c/upload";
		public static final String update = "u";
		public static final String insert = "c";
		public static final String del = "d";
	}
	
	static class args {
	}
	
	String albumId;
	String collectId;
	// String fileId;
	Photo photo; 

	public AlbumReq() {
		super(null, null);
	}
	
	public AlbumReq(String funcUri) {
		super(null, funcUri);
	}

	protected AlbumReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}

	public AlbumReq collectId(String cid) {
		this.collectId = cid;
		return this;
	}

	public AlbumReq download(Photo photo) {
		this.albumId = photo.albumId;
		this.collectId = photo.collectId;
		this.docId = photo.pid;
		this.photo = photo;
		this.a = A.download;
		return this;
	}

}
