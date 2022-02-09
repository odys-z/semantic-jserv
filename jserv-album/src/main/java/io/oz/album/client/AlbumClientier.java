package io.oz.album.client;

import java.io.IOException;

import io.odysz.anson.x.AnsonException;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.jclient.tier.Semantier;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
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

	public AlbumClientier(SessionClient client, ErrorCtx errCtx) {
		this.client = client;
		this.errCtx = errCtx;
	}

	public AlbumResp getCollect(String collectId) throws SemanticException, IOException, AnsonException {
		AlbumReq req = new AlbumReq(funcUri).collectId("c-001");
		req.a(A.collect);
		AnsonMsg<AlbumReq> q = client.<AlbumReq>userReq("test/collect", AlbumPort.album, req);
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

		String[] act = AnsonHeader.usrAct("AlbumTest", "create photo", "c/photo", "test");
		AnsonHeader header = client.header().act(act);
		AnsonMsg<AlbumReq> q = client.<AlbumReq>userReq("test/collect", AlbumPort.album, req)
									.header(header);

		return client.commit(q, errCtx);
	}

	/*
	public String upload(String pid, String localpath) throws SemanticException, AnsonException, IOException {
		AlbumReq req = new AlbumReq().photoId(pid);
		req.a(A.download);
		AnsonMsg<AnsonResp> resp = client.upload(funcUri, AlbumPort.album, req, localpath);
		return (String) resp.body(0).data().get("pid");
	}
	*/
}
