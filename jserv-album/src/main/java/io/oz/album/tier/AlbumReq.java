package io.oz.album.tier;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

public class AlbumReq extends AnsonBody {

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
	
	static class A {
		static final String records = "r/collects";
		static final String collect = "r/photos";
		static final String rec = "r/photo";
		static final String download = "r/download";
		static final String upload = "c/upload";
		static final String update = "u";
		static final String insert = "c";
		static final String del = "d";
	}
	
	static class args {
	}
	
	String albumId;
	String collectId;
	String fileId;
	Photo photo; 

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

}
