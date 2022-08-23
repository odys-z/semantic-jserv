package io.oz.album.tier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.odysz.common.AESHelper;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jsession.SessionInf;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.IFileDescriptor;
import io.odysz.semantics.x.SemanticException;

public class AlbumReq extends DocsReq {

	static public class A {
		public static final String records = "r/collects";
		public static final String collect = "r/photos";
		public static final String rec = "r/photo";
		public static final String download = "r/download";
		public static final String update = "u";

		public static final String insertPhoto = "c/photo";
		public static final String insertCollect = "c/collect";
		public static final String insertAlbum = "c/album";

		public static final String del = "d";

		/** Query client paths */
		public static final String selectSyncs = "r/syncflags";

		public static final String getPrefs = "r/prefs";
	}
	
	String albumId;
	String collectId;
	Photo photo;

	public AlbumReq device(String device) {
		this.device = device;
		return this;
	}

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

	/**
	 * TODO FIXME
	 * TODO FIXME
	 * Bug: some app exported pic won't get the right folder - no create date overriding empty EXIF date?
	 * 
	 * Create request for inserting new photo.
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
		
		// Exif.parseExif(this.photo, fullpath);
		// TODO FIXME
		// TODO FIXME
		// Bug: some app exported pic won't get the right folder - no create date overriding empty EXIF date?

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
	public AlbumReq createPhoto(IFileDescriptor file, SessionInf usr) throws IOException, SemanticException {
		return createPhoto(null, file.fullpath());
	}

	public AlbumReq selectPhoto(String docId) {
		this.docId = docId;
		this.photo = new Photo();
		this.photo.recId = docId;
		this.a = A.rec;

		return this;
	}

	public AlbumReq del(String device, String clientpath) {
		this.photo = new Photo();
		this.device = device;
		this.clientpath = clientpath;
		this.a = A.del;
		return this;
	}

}
