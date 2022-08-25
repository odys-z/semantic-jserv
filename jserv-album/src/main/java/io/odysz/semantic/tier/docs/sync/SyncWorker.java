package io.odysz.semantic.tier.docs.sync;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.sql.SQLException;

import org.apache.commons.io.FileUtils;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.EnvPath;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DASemantics.ShExtFile;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.file.ISyncFile;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.album.PhotoRobot;
import io.oz.album.helpers.Exif;
import io.oz.album.tier.Albums;
import io.oz.album.tier.Photo;

public class SyncWorker implements Runnable {

	public static final int hub = 0;
	public static final int main = 1;
	public static final int priv = 2;
	
	int mode;

	SessionClient client;
	
	String uri;
	String workerId;
	/** connection for update task records at private storage node */
	String connPriv;
	/** document's table name - table for saving records of local files */
	String targetablPriv;
	String tempDir;
	PhotoRobot robot;
	ErrorCtx errLog;

	public SyncWorker(int mode, String connId, String docTable) {
		this.mode = mode;
		uri = "sync.jserv";
		connPriv = connId;
		targetablPriv = docTable;
		
		errLog = new ErrorCtx() {
			@Override
			public void onError(MsgCode code, String msg) {
				Utils.warn(msg);
			}
		};
	}

	@Override
	public void run() {
		try {
			Docsyncer.lock.lock();

	        DocsResp resp = null;

			String[] act = AnsonHeader.usrAct("sync.jserv", "query", "r/tasks", "query tasks");
			AnsonHeader header = client.header().act(act);

			DocsReq req = (DocsReq) new DocsReq().a(A.records);

			AnsonMsg<DocsReq> q = client.<DocsReq>userReq("", AnsonMsg.Port.docsync, req)
									.header(header);

			resp = client.commit(q, errLog);
			
			syncDocs(resp);

		} catch (Exception ex) {
			ex.printStackTrace();
		}
		finally {
			Docsyncer.lock.unlock();
		}
	}

	public SyncWorker login(String workerId, String string) throws SemanticException, AnsonException, SsException, IOException, GeneralSecurityException {
		this.workerId = workerId;
		
		if (client == null) {
			client = Clients.login(workerId, "configured passwd");
			robot = new PhotoRobot(workerId, null, workerId);
			tempDir = String.format("io.oz.sync-%s.%s", mode, workerId); 
		}
		
		return this;
	}

	void syncDocs(DocsResp resp)
			throws SQLException, AnsonException, IOException, TransException {
		AnsonHeader header = null;
		while (resp.rs(0).next()) {
			Photo p = new Photo(resp.rs(0));
			syncDoc(p, robot, header);
		}
	}

	String syncDoc(Photo p, PhotoRobot worker, AnsonHeader header)
			throws AnsonException, IOException, TransException, SQLException {
		if (!verifyDel(p, worker, targetablPriv)) {
			DocsReq req = (DocsReq) new DocsReq(/* p */)
							.syncWith(p.clientpath(), p.device())
							.a(A.download);

			String tempath = tempath(p, worker);
			
			client.download(uri, Port.docsync, req, tempath);
			
			Albums.createFile(connPriv, p, robot);
		}

		DocsReq clsReq = (DocsReq) new DocsReq(null, uri, p)
						.a(A.synclose);

		AnsonMsg<DocsReq> q = client
				.<DocsReq>userReq(uri, AnsonMsg.Port.docsync, clsReq)
				.header(header);

		client.commit(q, errLog);

		return p.clientpath();
	}

	/** 
	 * <h5>Verify the file.</h5>
	 * <p>If it is not expected, delete it.</p>
	 * Two cases need this verification<br>
	 * 1. the file was downloaded but the task closing was failed<br>
	 * 2. the previous downloading resulted in the error message and been saved as a file<br>
	 * 
	 * @param f
	 * @param worker 
	 * @param docTable photo (doc)'s table name, used to resolve target file path if needed
	 * @return true if file exists and mime and size match (file moved to uri);
	 * or false if file size and mime doesn't match (tempath deleted)
	 * @throws IOException 
	 */
	protected boolean verifyDel(Photo f, PhotoRobot worker, String docTable) throws IOException {
		String pth = tempath(f, worker);
		File file = new File(pth);
		if (!file.exists())
			return false;
	
		String mime = f.mime;
		long size = f.size;
		f = Exif.parseExif(f, pth);

		if ( mime != null && mime.equals(f.mime) && size == f.size ) {
			// move temporary file
			String targetPath = resolvePrivRoot(f.uri);
			if (Docsyncer.debug)
				Utils.logi("   %s\n-> %s", pth, targetPath);
			try {
				Files.move(Paths.get(pth), Paths.get(targetPath), StandardCopyOption.ATOMIC_MOVE);
			} catch (Throwable t) {
				Utils.warn("Moving temporary file failed: %s\n->%s\n  %s\n  %s", pth, targetPath, f.device(), f.clientpath);
			}
			return true;
		}
		else {
			try { FileUtils.delete(new File(pth)); }
			catch (Exception ex) {}
			return false;
		}
	}

	public String tempath(ISyncFile f, PhotoRobot worker) {
		String tempath = f.clientpath().replaceAll(":", "");
		return EnvPath.decodeUri(tempDir, tempath);
	}

	public String resolvePrivRoot(String uri) {
		String extroot = ((ShExtFile) DATranscxt
				.getHandler(connPriv, targetablPriv, smtype.extFile))
				.getFileRoot();
		return EnvPath.decodeUri(extroot, uri);
	}

	public SyncWorker push() {
		return this;
	}

	public DocsResp queryTasks() {
		return null;
	}


}
