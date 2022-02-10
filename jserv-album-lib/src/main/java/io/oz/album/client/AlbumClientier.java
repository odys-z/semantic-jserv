package io.oz.album.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.odysz.anson.x.AnsonException;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.jclient.tier.Semantier;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.IFileDescriptor;
import io.odysz.semantics.x.SemanticException;
import io.oz.album.AlbumPort;
import io.oz.album.tier.AlbumReq;
import io.oz.album.tier.AlbumReq.A;
import io.oz.album.tier.AlbumResp;
import io.oz.album.tier.Photo;

public class AlbumClientier extends Semantier {

	private SessionClient client;
	private ErrorCtx errCtx;
	private String funcUri;
	private String clientUri;

	/**
	 * @param clientUri - the client function uri this instance will be used for.
	 * @param client
	 * @param errCtx
	 */
	public AlbumClientier(String clientUri, SessionClient client, ErrorCtx errCtx) {
		this.client = client;
		this.errCtx = errCtx;
		this.clientUri = clientUri;
	}

	public AlbumResp getCollect(String collectId) throws SemanticException, IOException, AnsonException {
		AlbumReq req = new AlbumReq(funcUri).collectId("c-001");
		req.a(A.collect);
		AnsonMsg<AlbumReq> q = client.<AlbumReq>userReq(clientUri, AlbumPort.album, req);
		return client.commit(q, errCtx);
	}

	public String download(Photo photo, String localpath)
			throws SemanticException, AnsonException, IOException {
		AlbumReq req = new AlbumReq().download(photo);
		req.a(A.download);
		return client.download(funcUri, AlbumPort.album, req, localpath);
	}

	public AlbumResp insertPhoto(String collId, String fullpath, String clientname) throws SemanticException, IOException, AnsonException {
		AlbumReq req = new AlbumReq()
				.createPhoto(collId, fullpath)
				.photoName(clientname);
		req.a(A.insertPhoto);

		String[] act = AnsonHeader.usrAct("album.java", "create", "c/photo", "create photo");
		AnsonHeader header = client.header().act(act);
		AnsonMsg<AlbumReq> q = client.<AlbumReq>userReq(clientUri, AlbumPort.album, req)
									.header(header);

		return client.commit(q, errCtx);
	}

	public List<DocsResp> syncPhotos(List<? extends IFileDescriptor> photos) throws SemanticException, IOException, AnsonException {
		String[] act = AnsonHeader.usrAct("album.java", "synch", "c/photo", "multi synch");
		AnsonHeader header = client.header().act(act);

		List<DocsResp> reslts = new ArrayList<DocsResp>(photos.size());

		for (IFileDescriptor p : photos) {
			AlbumReq req = new AlbumReq()
					.createPhoto(p);
			req.a(A.insertPhoto);

			AnsonMsg<AlbumReq> q = client.<AlbumReq>userReq(clientUri, AlbumPort.album, req)
									.header(header);

			DocsResp resp = client.commit(q, errCtx);

			reslts.add(resp);
		}
		return reslts;
	}

}
