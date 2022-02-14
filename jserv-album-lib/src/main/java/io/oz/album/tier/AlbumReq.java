package io.oz.album.tier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.odysz.common.AESHelper;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.IFileDescriptor;

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
		public static final String insertPhoto = "c/photo";
		public static final String insertCollect = "c/collect";
		public static final String insertAlbum = "c/album";
		public static final String del = "d";
	}
	
	static class args {
	}

	String albumId;
	String collectId;
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

	/**Create download request with photo record.
	 * @param photo
	 * @return request
	 */
	public AlbumReq download(Photo photo) {
		this.albumId = photo.albumId;
		this.collectId = photo.collectId;
		this.docId = photo.pid;
		this.photo = photo;
		this.a = A.download;
		return this;
	}

	/**Create request for inserting new photo.
	 * <p>FIXME: introducing stream field of Anson?</p>
	 * @param collId
	 * @param localname
	 * @return request
	 * @throws IOException 
	 */
	public AlbumReq createPhoto(String collId, String localname) throws IOException {
		Path p = Paths.get(localname);
		byte[] f = Files.readAllBytes(p);
		String b64 = AESHelper.encode64(f);

		this.photo = new Photo();
		this.photo.collectId = collId;
		this.photo.uri = b64;
		this.photo.pname = p.getFileName().toString();
		this.a = A.insertPhoto;

		return this;
	}

	public AlbumReq photoId(String pid) {
		if (photo == null)
			photo = new Photo();
		photo.pid = pid;
		return this;
	}

	public AlbumReq photoName(String name) {
		photo.pname = name;
		return this;
	}

	public AlbumReq createPhoto(IFileDescriptor file) throws IOException {
		Path p = Paths.get(file.fullpath());
		// FIXME performance problem
		byte[] f = Files.readAllBytes(p);
		String b64 = AESHelper.encode64(f);

		this.photo = new Photo();
		this.photo.uri = b64;
		this.photo.pname = file.clientname();
		this.a = A.insertPhoto;
		return this;
	}

}
