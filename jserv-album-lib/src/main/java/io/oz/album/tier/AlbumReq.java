package io.oz.album.tier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.odysz.common.AESHelper;
import io.odysz.common.LangExt;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.tier.docs.ClientDocUser;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.IFileDescriptor;
import io.odysz.semantics.x.SemanticException;

public class AlbumReq extends DocsReq {

	/** media file state */
	static enum FileState {
		uploading("uploading"),
		valid("valid"),
		synchronizing("synching"),
		archive("archive"),
		shared("shared");

		@SuppressWarnings("unused")
		private String state;

		FileState(String state) {
			this.state = state;
		}
	}
	
	static public class A {
		public static final String records = "r/collects";
		public static final String collect = "r/photos";
		public static final String rec = "r/photo";
		public static final String download = "r/download";
//		public static final String upload = "c/upload";
		public static final String update = "u";
		public static final String insertPhoto = "c/photo";
		public static final String insertCollect = "c/collect";
		public static final String insertAlbum = "c/album";
		public static final String del = "d";
		public static final String selectSyncs = "r/syncflags";

	}
	
	static class args {
	}

	String albumId;
	String collectId;
	Photo photo;

	public AlbumReq device(String device) { this.device = device; return this; }

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
		this.docId = photo.recId;
		this.photo = photo;
		this.a = A.download;
		return this;
	}

	/**Create request for inserting new photo.
	 * <p>FIXME: introducing stream field of Anson?</p>
	 * @param collId
	 * @param fullpath
	 * @return request
	 * @throws IOException 
	 */
	public AlbumReq createPhoto(String collId, String fullpath) throws IOException {
		Path p = Paths.get(fullpath);
		byte[] f = Files.readAllBytes(p);
		String b64 = AESHelper.encode64(f);

		this.photo = new Photo();
		this.photo.collectId = collId;
		this.photo.clientpath = fullpath;
		this.photo.uri = b64;
		this.photo.pname = p.getFileName().toString();
		this.a = A.insertPhoto;

		return this;
	}

	public AlbumReq photoId(String pid) {
		if (photo == null)
			photo = new Photo();
		photo.recId = pid;
		return this;
	}

	public AlbumReq photoName(String name) {
		photo.pname = name;
		return this;
	}

	/**Create a photo. Use this for small file.
	 * @param file
	 * @param usr
	 * @return album request
	 * @throws IOException
	 * @throws SemanticException
	 */
	public AlbumReq createPhoto(IFileDescriptor file, ClientDocUser usr) throws IOException, SemanticException {
		Path p = Paths.get(file.fullpath());
		byte[] f = Files.readAllBytes(p);
		String b64 = AESHelper.encode64(f);

		this.device = usr.device();
		if (LangExt.isblank(this.device, ".", "/"))
			throw new SemanticException("File to be uploaded must come with user's device id - for distinguish files. %s", file.fullpath());
		this.photo = new Photo();
		this.photo.clientpath = file.fullpath(); 
		this.photo.uri = b64;
		this.photo.pname = file.clientname();
		this.a = A.insertPhoto;
		return this;
	}

	public AlbumReq selectPhoto(String docId) {
		this.docId = docId;
		this.photo = new Photo();
		this.photo.recId = docId;
		this.a = A.rec;

		return this;
	}

}