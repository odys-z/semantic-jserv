package io.oz.jserv.sync;

import static org.junit.jupiter.api.Assertions.fail;

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
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.JProtocol.OnProcess;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.SessionInf;
import io.odysz.semantic.tier.docs.DocUtils;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.IFileDescriptor;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.x.TransException;

public class SyncWorker implements Runnable {
	static int blocksize = 12 * 1024 * 1024;

	/**
	 * jserv-node states
	 */
	public enum SyncMode {
		/** jserv node mode: cloud hub, equivalent of {@link Docsyncer#cloudHub} */
		hub,
		/** jserv node mode: private main, equivalent of {@link Docsyncer#mainStorage} */
		main,
		/** jserv node mode: private , equivalent of {@link Docsyncer#privateStorage}*/
		priv
	};
	
	SyncMode mode;
	
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

	public SyncWorker(SyncMode mode, String connId, String worker, DocTableMeta tablMeta)
			throws SemanticException, SQLException, SAXException, IOException {
		this.mode = mode;
		uri = "sync.jserv";
		connPriv = connId;
		workerId = worker;
		// targetablPriv = tablMeta.tbl;

		localMeta = tablMeta;
		
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
			robot = new SyncRobot(workerId, null, workerId).device("junit test device");
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

			DocsReq req = (DocsReq) new DocsReq(null).a(A.records);

			AnsonMsg<DocsReq> q = client.<DocsReq>userReq("", AnsonMsg.Port.docsync, req)
									.header(header);

			resp = client.commit(q, errLog);
			
			pullDocs(resp);

		} catch (Exception ex) {
			// ex.printStackTrace();
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
	DocsResp queryTasks(DocTableMeta meta, IUser usr, String device) throws SemanticException, AnsonException, IOException {
		DocsyncReq req = new DocsyncReq(usr.orgId())
							.query(meta);

		String[] act = AnsonHeader.usrAct("sync", "list", meta.tbl, device);
		AnsonHeader header = client.header().act(act);

		AnsonMsg<DocsyncReq> q = client
				.<DocsyncReq>userReq(uri, Port.docsync, req)
				.header(header);

		return client.<DocsyncReq, DocsResp>commit(q, errLog);
	}

	/**
	 * Pull files if any files at hub needing to be downward synchronized.
	 * @param tasks response of task querying, of which the result set (index 0) is the task list. 
	 * @return list of local file ids
	 * @throws SQLException
	 * @throws AnsonException
	 * @throws IOException
	 * @throws TransException
	 */
	ArrayList<String> pullDocs(DocsResp tasks)
			throws SQLException, AnsonException, IOException, TransException {
		
		ArrayList<String> res = new ArrayList<String>();
		AnsonHeader header = null;
		tasks.rs(0).beforeFirst();
		while (tasks.rs(0).next()) {
			try {
				SyncDoc p = new SyncDoc(tasks.rs(0), localMeta);
					
				res.add(synClose(synPull(p, robot, header), robot, header));
			}
			catch(Exception e) {
				fail(e.getMessage());
			}
		}
		
		return res;
	}

	/**
	 * Downward synchronizing.
	 * @param p
	 * @param worker
	 * @param header
	 * @return doc record (e.g. h_photos)
	 * @throws AnsonException
	 * @throws IOException
	 * @throws TransException
	 * @throws SQLException
	 */
	SyncDoc synPull(SyncDoc p, SyncRobot worker, AnsonHeader header)
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
		return p;
	}

	String synClose(SyncDoc p, SyncRobot worker, AnsonHeader header)
			throws AnsonException, IOException, TransException, SQLException {

		DocsReq clsReq = (DocsReq) new DocsReq(null, uri, p)
						.a(A.synclose);

		AnsonMsg<DocsReq> q = client
				.<DocsReq>userReq(uri, AnsonMsg.Port.docsync, clsReq)
				.header(header);

		client.commit(q, errLog);

		return p.recId();
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
				.nv("shareflag", doc.shareflag)
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
	 * <p>Verify the local file.</p>
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
		return DocUtils.resolvePrivRoot(uri, localMeta, connPriv);
	}

	/**
	 * Private nodes push docs to the cloud hub. 
	 * 
	 * @return
	 * @throws TransException
	 * @throws SQLException
	 */
	public SyncWorker push() throws TransException, SQLException {
		if (mode == SyncMode.main || mode == SyncMode.priv) {
			// find local records with shareflag = pub
			AnResultset rs = ((AnResultset) localSt
				.select(localMeta.tbl, "f")
				.cols(SyncDoc.nvCols(localMeta))
				.whereEq(localMeta.syncflag, SyncFlag.pushing)
				.rs(localSt.instancontxt(connPriv, robot))
				.rs(0)).beforeFirst();

			rs.next();

			// upload
			sync(rs, new OnProcess() {

				@Override
				public void proc(int listIndx, int rows, int seq, int totalBlocks, AnsonResp blockResp)
						throws IOException, AnsonException, SemanticException {
					String clientpath;
					try {
						clientpath = rs.getString(localMeta.fullpath);
						Utils.logi("[%s/%s] %s: %s / %s, reply: %s", listIndx, rows, clientpath, seq, totalBlocks, blockResp.msg());
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}
			});
			
			// set shareflag = hub
		}
		return this;
	}

	/**
	 * Synchronizing files to hub using block chain, accessing port {@link Port#docsync}.
	 * @param localMeta 
	 * @param rs
	 * @param robot device is required for overriding doc's device field.
	 * @param onProcess
	 * @return Sync response list
	 * @throws SQLException
	 * @throws TransException 
	 */
	List<DocsResp> sync(AnResultset rs, OnProcess onProcess)
			throws SQLException, TransException {
		return sync(localSt, connPriv, uri, client, errLog, localMeta, rs, robot, onProcess);
	}

	public static List<DocsResp> sync(DATranscxt st, String loconn, String uri, SessionClient client, ErrorCtx onErr, DocTableMeta meta,
			AnResultset files, SyncRobot robot, OnProcess proc) throws TransException, SQLException {

		SessionInf user = robot.sessionInf();
		DocsResp resp = null;
		try {
			String[] act = AnsonHeader.usrAct("sync", "push", meta.tbl, user.device);
			AnsonHeader header = client.header().act(act);

			List<DocsResp> reslts = new ArrayList<DocsResp>(files.getRowCount());

			int px = 0;
			while(files.next()) {
				px++;
				IFileDescriptor p = new SyncDoc(files, meta);
				DocsReq req = new DocsReq(meta.tbl)
						.folder(files.getString(meta.folder))
						.share((SyncDoc)p)
						.blockStart(p, user);

				AnsonMsg<DocsReq> q = client
						.<DocsReq>userReq(uri, Port.docsync, req)
						.header(header);

				resp = client.commit(q, onErr);

				String pth = p.fullpath();
				if (!pth.equals(resp.doc.fullpath()))
					Utils.warn("resp is not replied with exactly the same path: %s", resp.doc.fullpath());

				int totalBlocks = (int) ((Files.size(Paths.get(pth)) + 1) / blocksize);
				if (proc != null) proc.proc(files.total(), px, 0, totalBlocks, resp);

				int seq = 0;
				FileInputStream ifs = new FileInputStream(new File(p.fullpath()));
				try {
					String b64 = AESHelper.encode64(ifs, blocksize);
					while (b64 != null) {
						req = new DocsReq(meta.tbl).blockUp(seq, resp, b64, user);
						seq++;

						q = client.<DocsReq>userReq(uri, Port.docsync, req)
									.header(header);

						resp = client.commit(q, onErr);
						if (proc != null) proc.proc(files.total(), px, seq, totalBlocks, resp);

						b64 = AESHelper.encode64(ifs, blocksize);
					}
					req = new DocsReq(meta.tbl).blockEnd(resp, user);
					q = client.<DocsReq>userReq(uri, Port.docsync, req)
								.header(header);
					resp = client.commit(q, onErr);

					
					st.update(meta.tbl, robot)
						.nv(meta.syncflag, SyncFlag.publish)
						.whereEq(meta.pk, p.recId())
						.u(st.instancontxt(loconn, robot));

					if (proc != null) proc.proc(files.total(), px, seq, totalBlocks, resp);
				}
				catch (TransException | IOException ex) {
					Utils.warn(ex.getMessage());

					req = new DocsReq(meta.tbl).blockAbort(resp, user);
					req.a(DocsReq.A.blockAbort);
					q = client.<DocsReq>userReq(uri, Port.docsync, req)
								.header(header);
					resp = client.commit(q, onErr);
					if (proc != null) proc.proc(files.total(), px, seq, totalBlocks, resp);

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

	public ArrayList<String> pull() throws AnsonException, IOException, SQLException, TransException {
		DocsResp rsp = queryTasks(localMeta, robot, connPriv);
		return pullDocs(rsp);
	}

	/**
	 * Verifying each rec-id has a corresponding local file.
	 * 
	 * @param ids
	 * @return this
	 * @throws TransException
	 * @throws SQLException
	 */
	public SyncWorker verifyDocs(ArrayList<String> ids) throws TransException, SQLException {
		AnResultset rs = (AnResultset) localSt
				.select(localMeta.tbl, "t")
				.cols(localMeta.pk, localMeta.fullpath, localMeta.uri, localMeta.mime, localMeta.size)
				.whereIn(localMeta.pk, ids)
				.rs(localSt.instancontxt(connPriv, robot))
				.rs(0);

		if (rs.total() != ids.size())
			throw new SemanticException("id count (%s) != file records' count (%s)", ids.size(), rs.total());

		while (rs.next()) {
			String p = resolvePrivRoot(rs.getString(localMeta.uri));
			File f = new File(p);
			if (f.exists() && f.length() == rs.getLong(localMeta.size))
				continue;
			throw new SemanticException("Doc doesn't exists. id: %s, uri: %s, expecting path: %s",
					rs.getString(localMeta.pk), uri, p);
		}
		return this;
	}
}
