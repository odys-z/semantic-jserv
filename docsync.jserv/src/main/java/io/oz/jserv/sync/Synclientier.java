package io.oz.jserv.sync;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.AESHelper;
import io.odysz.common.Utils;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.jclient.tier.Semantier;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.JProtocol.OnProcess;
import io.odysz.semantic.jsession.SessionInf;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.x.SemanticException;
import io.oz.album.tier.Photo;

public class Synclientier extends Semantier {

	SessionClient client;
	ErrorCtx errCtx;
	String clientUri;
	DocTableMeta meta;

	static int blocksize = 3 * 1024 * 1024;
	public static void bloksize(int s) throws SemanticException {
		if (s % 12 != 0)
			throw new SemanticException("Block size must be multiple of 12.");
		blocksize = s;
	}
	
	public Synclientier blockSize(int size) {
		blocksize = size;
		return this;
	}
	
	/**
	 * @param clientUri - the client function uri this instance will be used for.
	 * @param client
	 * @param tblMeta 
	 * @param errCtx
	 */
	public Synclientier(String clientUri, SessionClient client, DocTableMeta tblMeta, ErrorCtx errCtx) {
		this.client = client;
		this.errCtx = errCtx;
		this.clientUri = clientUri;
		this.meta = tblMeta;
	}
	
	/**
	 * 
	 * @param videos
	 * @param user
	 * @param proc
	 * @param onErr
	 * @return
	 */
	public List<DocsResp> syncVideos(List<? extends SyncDoc> videos,
				SessionInf user, OnProcess proc, ErrorCtx ... onErr) {

		ErrorCtx errHandler = onErr == null || onErr.length == 0 ? errCtx : onErr[0];

        DocsResp resp = null;
		try {
			String[] act = AnsonHeader.usrAct("album.java", "synch", "c/photo", "multi synch");
			AnsonHeader header = client.header().act(act);

			List<DocsResp> reslts = new ArrayList<DocsResp>(videos.size());

			for ( int px = 0; px < videos.size(); px++ ) {

				SyncDoc p = videos.get(px);
				DocsReq req = new DocsReq(meta.tbl)
						.folder("synctest")
						.share(p)
						.blockStart(p, user);

				AnsonMsg<DocsReq> q = client.<DocsReq>userReq(clientUri, Port.docsync, req)
										.header(header);

				resp = client.commit(q, errHandler);

				String pth = p.fullpath();
				if (!pth.equals(resp.fullpath()))
					Utils.warn("Resp is not replied with exactly the same path: %s", resp.fullpath());

				int totalBlocks = (int) ((Files.size(Paths.get(pth)) + 1) / blocksize);
				if (proc != null) proc.proc(videos.size(), px, 0, totalBlocks, resp);

				int seq = 0;
				FileInputStream ifs = new FileInputStream(new File(p.fullpath()));
				try {
					String b64 = AESHelper.encode64(ifs, blocksize);
					while (b64 != null) {
						req = new DocsReq(meta.tbl).blockUp(seq, resp, b64, user);
						seq++;

						q = client.<DocsReq>userReq(clientUri, Port.docsync, req)
									.header(header);

						resp = client.commit(q, errHandler);
						if (proc != null) proc.proc(videos.size(), px, seq, totalBlocks, resp);

						b64 = AESHelper.encode64(ifs, blocksize);
					}
					req = new DocsReq(meta.tbl).blockEnd(resp, user);

					q = client.<DocsReq>userReq(clientUri, Port.docsync, req)
								.header(header);
					resp = client.commit(q, errHandler);
					if (proc != null) proc.proc(videos.size(), px, seq, totalBlocks, resp);
				}
				catch (Exception ex) {
					Utils.warn(ex.getMessage());

					req = new DocsReq(meta.tbl).blockAbort(resp, user);
					req.a(DocsReq.A.blockAbort);
					q = client.<DocsReq>userReq(clientUri, Port.docsync, req)
								.header(header);
					resp = client.commit(q, errHandler);
					if (proc != null) proc.proc(videos.size(), px, seq, totalBlocks, resp);

					throw ex;
				}
				finally { ifs.close(); }

				reslts.add(resp);
			}

			return reslts;
		} catch (IOException e) {
			errHandler.onError(MsgCode.exIo, e.getClass().getName() + " " + e.getMessage());
		} catch (AnsonException | SemanticException e) { 
			errHandler.onError(MsgCode.exGeneral, e.getClass().getName() + " " + e.getMessage());
		}
		return null;
	}

	public String download(Photo photo, String localpath)
			throws SemanticException, AnsonException, IOException {
		DocsReq req = (DocsReq) new DocsReq(meta.tbl).uri(clientUri);
		req.docId = photo.recId;
		req.a(A.download);
		return client.download(clientUri, Port.docsync, req, localpath);
	}

	/**
	 * Get a doc record (will also synchronize file's base64 content)
	 * 
	 * @param docId
	 * @param onErr
	 * @return response
	 */
	public DocsResp selectDoc(String docId, ErrorCtx ... onErr) {
		ErrorCtx errHandler = onErr == null || onErr.length == 0 ? errCtx : onErr[0];
		String[] act = AnsonHeader.usrAct("album.java", "synch", "c/photo", "multi synch");
		AnsonHeader header = client.header().act(act);

		DocsReq req = new DocsReq(meta.tbl);
		req.a(A.rec);
		req.docId = docId;

		DocsResp resp = null;
		try {
			AnsonMsg<DocsReq> q = client.<DocsReq>userReq(clientUri, Port.docsync, req)
										.header(header);

			resp = client.commit(q, errCtx);
		} catch (AnsonException | SemanticException e) {
			errHandler.onError(MsgCode.exSemantic, e.getMessage() + " " + (e.getCause() == null ? "" : e.getCause().getMessage()));
		} catch (IOException e) {
			errHandler.onError(MsgCode.exIo, e.getMessage() + " " + (e.getCause() == null ? "" : e.getCause().getMessage()));
		}
		return resp;
	}
	
	public DocsResp del(String device, String clientpath) {
		DocsReq req = (DocsReq) new DocsReq(meta.tbl)
				.device(device)
				.clientpath(clientpath)
				.a(A.del);

		DocsResp resp = null;
		try {
			String[] act = AnsonHeader.usrAct("album.java", "del", "d/photo", "");
			AnsonHeader header = client.header().act(act);
			AnsonMsg<DocsReq> q = client.<DocsReq>userReq(clientUri, Port.docsync, req)
										.header(header);

			resp = client.commit(q, errCtx);
		} catch (AnsonException | SemanticException e) {
			errCtx.onError(MsgCode.exSemantic, e.getMessage() + " " + (e.getCause() == null ? "" : e.getCause().getMessage()));
		} catch (IOException e) {
			errCtx.onError(MsgCode.exIo, e.getMessage() + " " + (e.getCause() == null ? "" : e.getCause().getMessage()));
		}
		return resp;
	}
}
