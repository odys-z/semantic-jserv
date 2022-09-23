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
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.ShExtFile;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.ext.DocTableMeta;
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
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.x.TransException;

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
	// String targetablPriv;
	String tempDir;
	SyncRobot robot;
	ErrorCtx errLog;

	/**
	 * Local table meta of which records been synchronized as private jserv node.
	 */
	DocTableMeta localMeta;

	public SyncWorker(int mode, String connId, String worker, DocTableMeta tablMeta)
			throws SemanticException, SQLException, SAXException, IOException {
		this.mode = mode;
		uri = "sync.jserv";
		connPriv = connId;
		workerId = worker;
		// targetablPriv = tablMeta.tbl;

		localMeta = tablMeta;
		
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
			client = Clients.login(workerId, pswd, "java.test");
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
			
			pullDocs(resp);

		} catch (Exception ex) {
			ex.printStackTrace();
		}
		finally {
			Docsyncer.lock.unlock();
		}
	}

	/**
	 * @param meta 
	 * @param usr 
	 * @param device 
	 * @return response
	 * @throws IOException 
	 * @throws AnsonException 
	 * @throws SemanticException 
	 */
	public DocsResp queryTasks(DocTableMeta meta, IUser usr, String device) throws SemanticException, AnsonException, IOException {
		DocsyncReq req = new DocsyncReq(usr.orgId())
							.query(meta);

		String[] act = AnsonHeader.usrAct("sync", "list", meta.tbl, device);
		AnsonHeader header = client.header().act(act);

		AnsonMsg<DocsyncReq> q = client
				.<DocsyncReq>userReq(uri, Port.docsync, req)
				.header(header);

		return client.<DocsyncReq, DocsResp>commit(q, errLog);
	}

	void pullDocs(DocsResp tasks)
			throws SQLException, AnsonException, IOException, TransException {
		AnsonHeader header = null;
		while (tasks.rs(0).next()) {
			SyncDoc p = new SyncDoc(tasks.rs(0), localMeta);
			syncPull(p, robot, header);
		}
	}

	String syncPull(SyncDoc p, SyncRobot worker, AnsonHeader header)
			throws AnsonException, IOException, TransException, SQLException {
		if (!verifyDel(p, worker, localMeta.tbl)) {
			DocsyncReq req = (DocsyncReq) new DocsyncReq(/* p */)
							.with(p.fullpath(), p.device())
							.a(A.download);

			String tempath = tempath(p, worker);
			
			String path = client.download(uri, Port.docsync, req, tempath);
			
			p.uri(path);
			insertLocalFile(localSt, connPriv, path, p, robot, localMeta);
		}

		DocsReq clsReq = (DocsReq) new DocsReq(null, uri, p)
						.a(A.synclose);

		AnsonMsg<DocsReq> q = client
				.<DocsReq>userReq(uri, AnsonMsg.Port.docsync, clsReq)
				.header(header);

		client.commit(q, errLog);

		return p.fullpath();
	}

	static String insertLocalFile(DATranscxt st, String conn, String path, SyncDoc doc, SyncRobot usr, DocTableMeta meta)
			throws TransException, SQLException {
		if (LangExt.isblank(path))
			throw new SemanticException("Client path can't be null/empty.");
		
		Insert ins = st.insert(meta.tbl, usr)
				.nv("family", usr.orgId())
				.nv("uri", doc.uri)
				.nv("pname", doc.pname)
				.nv("device", usr.deviceId())
				.nv("clientpath", doc.clientpath)
				.nv("shareflag", doc.isPublic() ? DocsReq.Share.pub : DocsReq.Share.priv)
				.nv("syncflag", doc.isPublic() ? DocsyncReq.SyncFlag.pushing : DocsyncReq.SyncFlag.priv)
				;
		
		if (!LangExt.isblank(doc.mime))
			ins.nv("mime", doc.mime);
		
		Docsyncer.onDocreate(doc, meta, usr);

		SemanticObject res = (SemanticObject) ins.ins(st.instancontxt(conn, usr));
		String pid = ((SemanticObject) ((SemanticObject) res.get("resulved"))
				.get("h_photos"))
				.getString("pid");
		
		return pid;
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
				.getHandler(connPriv, localMeta.tbl, smtype.extFile))
				.getFileRoot();
		return EnvPath.decodeUri(extroot, uri);
	}

	/**
	 * Private nodes push docs to the cloud hub. 
	 * 
	 * @return
	 * @throws TransException
	 * @throws SQLException
	 */
	public SyncWorker push() throws TransException, SQLException {
		if (mode == main || mode == priv) {
			// find local records with shareflag = pub
			AnResultset rs = (AnResultset) localSt
				.select(localMeta.tbl, "f")
				.cols(localMeta.device, localMeta.fullpath, localMeta.syncflag)
				.whereEq(localMeta.syncflag, DocsyncReq.SyncFlag.pushing)
				.rs(localSt.instancontxt(connPriv, robot))
				.rs(0);

			// upload
			String clientpath = rs.getString(localMeta.fullpath);
			sync(localMeta, rs, robot.sessionInf(), new OnProcess() {

				@Override
				public void proc(int listIndx, int totalBlocks, DocsResp blockResp)
						throws IOException, AnsonException, SemanticException {
					Utils.logi("%s: %s / %s, %s", clientpath, listIndx, totalBlocks, blockResp.msg());
				}});
			
			// set shareflag = hub
		}
		return this;
	}

	/** Synchronizing files to hub using block chain, accessing port {@link Port#docsync}.
	 * @param localMeta 
	 * @param rs
	 * @param sessionInf
	 * @param onProcess
	 * @return Sync response list
	 * @throws SQLException
	 */
	List<DocsResp> sync(DocTableMeta localMeta, AnResultset rs, SessionInf sessionInf, OnProcess onProcess)
			throws SQLException {
		return sync(uri, client, errLog, localMeta, rs, sessionInf, onProcess);
	}

	public List<DocsResp> sync(String uri, SessionClient client, ErrorCtx onErr, DocTableMeta meta,
			AnResultset files, SessionInf user, OnProcess proc) throws SQLException {

		DocsResp resp = null;
		try {
			String[] act = AnsonHeader.usrAct("sync", "push", meta.tbl, user.device);
			AnsonHeader header = client.header().act(act);

			List<DocsResp> reslts = new ArrayList<DocsResp>(files.getRowCount());

			int px = 0;
			while(files.next()) {
				px++;
				IFileDescriptor p = new SyncDoc(files, localMeta);
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

					
					// TODO
					// FIXME change local sync flag

					if (proc != null) proc.proc(totalBlocks, totalBlocks, resp);
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
}
