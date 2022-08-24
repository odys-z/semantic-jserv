package io.odysz.semantic.tier.docs.sync;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.EnvPath;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.file.ISyncFile;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.transact.x.TransException;
import io.oz.album.PhotoRobot;
import io.oz.album.tier.Albums;
import io.oz.album.tier.Photo;

public class SyncWorker implements Runnable {

	public static final int hub = 0;
	public static final int main = 1;
	public static final int priv = 2;
	
	int mode;

	SessionClient client;
	
	String uri;
	String conn;
	private String workerId;
	private ErrorCtx errLog;
	private String tempDir;
	private PhotoRobot robot;

	public SyncWorker(int mode, String connId) {
		this.mode = mode;
		uri = "sync.jserv";
		conn = connId;
		
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
			if (workerId == null)
				workerId = registerWorker(uri);
			if (client == null) {
				client = Clients.login(workerId, "configured passwd");
				robot = new PhotoRobot(workerId, null, workerId);
				tempDir = String.format("io.oz.sync-%s.%s", mode, workerId); 
			}

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
	}

	private String registerWorker(String uri) {
		return workerId;
	}

	private void syncDocs(DocsResp resp)
			throws SQLException, AnsonException, IOException, TransException {
		AnsonHeader header = null;
		while (resp.rs(0).next()) {
			Photo p = new Photo(resp.rs(0));
			syncDoc(p, robot, header);
		}
	}

	String syncDoc(Photo p, PhotoRobot worker, AnsonHeader header)
			throws AnsonException, IOException, TransException, SQLException {
		if (!verifyDel(p)) {
			DocsReq req = (DocsReq) new DocsReq(/* p */)
							.syncWith(p.fullpath(), p.device())
							.a(A.download);

			String tempath = p.fullpath().replaceAll(":", "");
			tempath = EnvPath.decodeUri(tempDir, tempath);
			
			File f = new File(tempath);
			if (f.exists()) f.delete();
				
			client.download(uri, Port.docsync, req, tempath);
			
			Albums.createFile(tempath, p, robot);
		}

		DocsReq clsReq = (DocsReq) new DocsReq(null, uri, p)
						.a(A.synclose);

		AnsonMsg<DocsReq> q = client
				.<DocsReq>userReq(uri, AnsonMsg.Port.docsync, clsReq)
				.header(header);

		client.commit(q, errLog);

		return p.fullpath();
	}

	/** 
	 * <h5>Verify the file.</h5>
	 * <p>If it is not expected, delete it.</p>
	 * Two cases need this verification<br>
	 * 1. the file was downloaded but the task closing was failed<br>
	 * 2. the previous downloading resulted in the error message and been saved as a file<br>
	 * 
	 * @param f
	 * @return true if file exists and mime and size match
	 */
	private boolean verifyDel(ISyncFile f) {
		return false;
	}

}
