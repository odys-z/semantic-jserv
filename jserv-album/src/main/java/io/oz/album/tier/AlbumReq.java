package io.oz.album.tier;

import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;

public class AlbumReq extends AnsonBody {
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

	String fileId;
	
	public AlbumReq(String funcUri) {
		super(null, funcUri);
	}

	protected AlbumReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}

}
