package io.oz.jserv.sync;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.AESHelper;
import io.odysz.common.EnvPath;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.ShExtFile;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.JProtocol.OnProcess;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.SessionInf;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.IFileDescriptor;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docsync.SyncDoc;

public class SyncWorker implements Runnable {
	static int blocksize = 12 * 1024 * 1024;

	/** jserv node mode: cloud hub, equivalent of {@link Docsyncer#cloudHub} */
	public static final int hub = 0;
	/** jserv node mode: private main, equivalent of {@link Docsyncer#mainStorage} */
	public static final int main = 1;
	/** jserv node mode: private , equivalent of {@link Docsyncer#privateStorage}*/
	public static final int priv = 2;
	
	int mode;
	DATranscxt localSt;
	SessionClient client;
	
	String uri;
	String workerId;
	/** connection for update task records at private storage node */
	String connPriv;
	/** document's table name - table for saving records of local files */
	String targetablPriv;
	String tempDir;
	SyncRobot robot;
	ErrorCtx errLog;


	public SyncWorker(int mode, String connId, String worker, String docTable)
			throws SemanticException, SQLException, SAXException, IOException {
		this.mode = mode;
		uri = "sync.jserv";
		connPriv = connId;
		workerId = worker;
		targetablPriv = docTable;
		
		if (mode != main)
			localSt = new DATranscxt(connId);
		
		errLog = new ErrorCtx() {
			@Override
			public void onError(MsgCode code, String msg) {
				Utils.warn(msg);
			}
		};
	}

	public SyncWorker login(String workerId, String pswd) throws SemanticException, AnsonException, SsException, IOException, GeneralSecurityException {
		this.workerId = workerId;
		
		if (workerId != null && client == null) {
			client = Clients.login(workerId, pswd);
			robot = new SyncRobot(workerId, null, workerId);
			tempDir = String.format("io.oz.sync-%s.%s", mode, workerId); 
		}
		
		return this;
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

	void syncDocs(DocsResp resp)
			throws SQLException, AnsonException, IOException, TransException {
		AnsonHeader header = null;
		while (resp.rs(0).next()) {
			SyncDoc p = new SyncDoc(resp.rs(0));
			syncDoc(p, robot, header);
		}
	}

	String syncDoc(SyncDoc p, SyncRobot worker, AnsonHeader header)
			throws AnsonException, IOException, TransException, SQLException {
		if (!verifyDel(p, worker, targetablPriv)) {
			DocsReq req = (DocsReq) new DocsReq(/* p */)
							.syncWith(p.fullpath(), p.device())
							.a(A.download);

			String tempath = tempath(p, worker);
			
			client.download(uri, Port.docsync, req, tempath);
			
			createFile(connPriv, p, robot);
		}

		DocsReq clsReq = (DocsReq) new DocsReq(null, uri, p)
						.a(A.synclose);

		AnsonMsg<DocsReq> q = client
				.<DocsReq>userReq(uri, AnsonMsg.Port.docsync, clsReq)
				.header(header);

		client.commit(q, errLog);

		return p.fullpath();
	}

	public static String createFile(String connPriv2, SyncDoc p, SyncRobot robot2) {
		
		return null;
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
	protected boolean verifyDel(SyncDoc f, SyncRobot worker, String docTable) throws IOException {
		String pth = tempath(f, worker);
		File file = new File(pth);
		if (!file.exists())
			return false;
	
		long size = f.size;
		long length = file.length();

		if ( size == length ) {
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

	public String tempath(IFileDescriptor f, SyncRobot worker) {
		String tempath = f.fullpath().replaceAll(":", "");
		return EnvPath.decodeUri(tempDir, tempath);
	}

	public String resolvePrivRoot(String uri) {
		String extroot = ((ShExtFile) DATranscxt
				.getHandler(connPriv, targetablPriv, smtype.extFile))
				.getFileRoot();
		return EnvPath.decodeUri(extroot, uri);
	}

	/**
	 * Local node push docs to the cloud hub. 
	 * 
	 * @return
	 * @throws TransException
	 * @throws SQLException
	 */
	public SyncWorker push() throws TransException, SQLException {
		if (mode == main || mode == priv) {
			// find local records with shareflag = pub
			AnResultset rs = (AnResultset) localSt
				.select(targetablPriv, "f")
				.cols("device", "clientpath", "syncflag")
				.whereEq("syncflag", DocsReq.sharePrvHub)
				.rs(localSt.instancontxt(connPriv, robot))
				.rs(0);

			// upload
			String clientpath = rs.getString("clientpath");
			sync(rs, robot.sessionInf(), new OnProcess() {

				@Override
				public void proc(int listIndx, int totalBlocks, DocsResp blockResp)
						throws IOException, AnsonException, SemanticException {
					Utils.logi("%s: %s / %s, %s", clientpath, listIndx, totalBlocks, blockResp.msg());
				}});
			
			// set shareflag = hub
		}
		return this;
	}

	private List<DocsResp> sync(AnResultset rs, SessionInf sessionInf, OnProcess onProcess)
			throws SQLException {
		return sync(uri, client, errLog, rs, sessionInf, onProcess);
	}

	public static List<DocsResp> sync(String uri, SessionClient client, ErrorCtx onErr,
			AnResultset files, SessionInf user, OnProcess proc) throws SQLException {

		DocsResp resp = null;
		try {
			String[] act = AnsonHeader.usrAct("sync", "sync", "sync/docs", "multi sync");
			AnsonHeader header = client.header().act(act);

			List<DocsResp> reslts = new ArrayList<DocsResp>(files.getRowCount());

			int px = 0;
			while(files.next()) {
				px++;
				IFileDescriptor p = new SyncDoc(files);
				DocsReq req = new DocsReq()
						.blockStart(p, user);

				AnsonMsg<DocsReq> q = client
						.<DocsReq>userReq(uri, Port.docsync, req)
						.header(header);

				resp = client.commit(q, onErr);

				String pth = p.fullpath();
				if (!pth.equals(resp.fullpath()))
					Utils.warn("resp is not replied with exactly the same path: %s", resp.fullpath());

				int totalBlocks = (int) ((Files.size(Paths.get(pth)) + 1) / blocksize);
				if (proc != null) proc.proc(px, totalBlocks, resp);

				int seq = 0;
				FileInputStream ifs = new FileInputStream(new File(p.fullpath()));
				try {
					String b64 = AESHelper.encode64(ifs, blocksize);
					while (b64 != null) {
						req = new DocsReq().blockUp(seq, resp, b64, user);
						seq++;

						q = client.<DocsReq>userReq(uri, Port.docsync, req)
									.header(header);

						resp = client.commit(q, onErr);
						if (proc != null) proc.proc(px, totalBlocks, resp);

						b64 = AESHelper.encode64(ifs, blocksize);
					}
					req = new DocsReq().blockEnd(resp, user);

					q = client.<DocsReq>userReq(uri, Port.docsync, req)
								.header(header);
					resp = client.commit(q, onErr);
					if (proc != null) proc.proc(px, totalBlocks, resp);
				}
				catch (Exception ex) {
					Utils.warn(ex.getMessage());

					req = new DocsReq().blockAbort(resp, user);
					req.a(DocsReq.A.blockAbort);
					q = client.<DocsReq>userReq(uri, Port.docsync, req)
								.header(header);
					resp = client.commit(q, onErr);
					if (proc != null) proc.proc(px, totalBlocks, resp);

					throw ex;
				}
				finally { ifs.close(); }

				reslts.add(resp);
			}

			return reslts;
		} catch (IOException e) {
			onErr.onError(MsgCode.exIo, e.getClass().getName() + " " + e.getMessage());
		} catch (AnsonException | SemanticException e) { 
			onErr.onError(MsgCode.exGeneral, e.getClass().getName() + " " + e.getMessage());
		}
		return null;
	}

	public DocsResp queryTasks() {
		return null;
	}


}
