package io.oz.album.peer;

import static io.odysz.common.LangExt.eq;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.odysz.common.AESHelper;
import io.odysz.common.NV;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.tier.docs.Device;
import io.odysz.semantic.tier.DatasetierReq;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.IFileDescriptor;
import io.odysz.semantics.SessionInf;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.PageInf;

/**
 * @author Ody
 */
public class AlbumReq extends DocsReq {

	static public class A {
		public static final String stree = DatasetierReq.A.stree;
		public static final String sk = DatasetierReq.A.sks;

		public static final String album   = "r/collects";
		public static final String collect = "r/photos";
		public static final String rec     = "r/photo";
		public static final String folder  = "r/folder";
		
		public static final String download = "r/download";
		public static final String update = "u";

		public static final String insertPhoto = "c/photo";
		public static final String insertCollect = "c/collect";
		public static final String insertAlbum = "c/album";

		public static final String del = "d";

		/** Query client paths */
		public static final String selectSyncs = DocsReq.A.selectSyncs; // "r/syncflags";

		public static final String getPrefs = "r/prefs";

//		public static final String sharingPolicy = "r/share-relat";

		/** read folder's relationship with org
		 * @deprecated It's better to do with a different A for different sk, e. g. folder-org relatiosn,
		 * but currently @anclient/anreact wrapped data layer in to component, no way to use a different A.
		 * So this is not used for a different stree to r/stree, but it's a better parctice for the
		 * plugin supported version.
		 */
		public static final String folderel = "r/rel-folder-org";

		/**
		 * Update folder sharing policies,
		 * arg: req.photo.folder()
		 */
		public static final String updateFolderel = "u/folder-rel";
	}
	
	String albumId;
	public String collectId;
	public PhotoRec photo;
	/** s-tree's semantic key */
	public String sk;
	
	/** only clear relationships */
	public boolean clearels;
	
	/**
	 * Checked items for insert child relation table
	 */
	public NV[][] checkRels;

	public AlbumReq device(String device) {
		this.device = new Device(device, null);
		return this;
	}

	public AlbumReq() {
		super(null);
	}
	
	public AlbumReq(String funcUri) {
		super(funcUri);
	}

	protected AlbumReq(AnsonMsg<? extends AnsonBody> parent, String uri) {
		super(parent, uri);
	}

	public AlbumReq(ExpSyncDoc p, String uri) {
		super(uri);
		this.doc = p;
	}

	public AlbumReq collectId(String cid) {
		this.collectId = cid;
		return this;
	}

	/**
	 * Create a download request with a photo record.
	 * @param photo
	 * @return request
	 */
	public AlbumReq download(PhotoRec photo) {
		this.albumId = photo.albumId;
		this.collectId = photo.collectId;
		this.photo = photo;
		// this.docId = photo.recId;
		this.a = A.download;
		return this;
	}

	/**
	 * Create a request for inserting a new photo.
	 * <p>FIXME: introducing stream field (uri) of Anson?</p>
	 * TODO: use SyncDoc#loadFile.
	 * 
	 * @param collId
	 * @param fullpath
	 * @return request
	 * @throws IOException 
	 */
	public AlbumReq createPhoto(String collId, String fullpath) throws IOException {
		Path p = Paths.get(fullpath);
		byte[] f = Files.readAllBytes(p);
		String b64 = AESHelper.encode64(f);

		this.photo = new PhotoRec();
		this.photo.collectId = collId;
		this.photo.fullpath(fullpath);
		this.photo.uri64 = b64;
		this.photo.pname = p.getFileName().toString();
		
		this.a = A.insertPhoto;

		return this;
	}

	public AlbumReq photoId(String pid) {
		if (photo == null)
			photo = new PhotoRec();
		photo.recId = pid;
		return this;
	}

	public AlbumReq photoName(String name) {
		photo.pname = name;
		return this;
	}

	/**
	 * Create a photo. Use this for small file.
	 * @param file
	 * @param usr
	 * @return album request
	 * @throws IOException
	 * @throws SemanticException
	 */
	public AlbumReq createPhoto(IFileDescriptor file, SessionInf usr)
			throws IOException, SemanticException {
		return createPhoto(null, file.fullpath());
	}

	public AlbumReq selectPhoto(String docId) {
		this.doc.recId = docId;
		this.a = A.rec;
		return this;
	}

	public AlbumReq del(String device, String clientpath) {
		this.photo = new PhotoRec();
		this.device = new Device(device, null);
		this.photo.clientpath = clientpath;
		this.a = A.del;
		return this;
	}

	/**
	 * @param whereqs (n0, v0), (n1, v1), ..., must be even number of elements.
	 * @return this
	 */
	public AlbumReq page(int page, int size, String... whereqs) {
		pageInf = new PageInf(page, size, whereqs);
		return this;
	}

	public String[] getChecks(String colname) {
		String[] vals = new String[checkRels.length]; 
		for (int x = 0; x < checkRels.length; x++) {
			for (NV nv : checkRels[x]) {
				while (!eq(nv.name, colname))
					continue;
				vals[x] = (String) nv.value;
				break;
			}
		}
		return vals;
	}
}
