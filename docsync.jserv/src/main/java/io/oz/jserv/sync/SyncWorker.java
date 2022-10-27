package io.oz.jserv.sync;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.JProtocol.OnProcess;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

public class SyncWorker implements Runnable {
	static int blocksize = 3 * 1024 * 1024;

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
	
	String mac;
	Synclientier synctier;
	
	public final String uri;
	String connPriv;
	String workerId;
	
	// String tempDir;
	// SyncRobot robot;
	ErrorCtx errLog;

	/**
	 * Local table meta of which records been synchronized as private jserv node.
	 */
	DocTableMeta localMeta;

	DATranscxt localSt;

	public boolean verbose = true;


	public SyncWorker(SyncMode mode, String device, String connId, String worker, DocTableMeta tablMeta)
			throws SemanticException, SQLException, SAXException, IOException {
		this.mode = mode;
		uri = "/sync/worker";
		connPriv = connId;
		workerId = worker;
		mac = device;

		localMeta = tablMeta;
		
		localSt = new DATranscxt(connId);
		
		errLog = new ErrorCtx() {
			@Override
			public void onError(MsgCode code, String msg) {
				Utils.warn(msg);
			}
		};
	}

	public SyncWorker login(String pswd) throws SemanticException, AnsonException, SsException, IOException, SQLException, SAXException {
		synctier = new Synclientier(uri, connPriv, errLog)
				.login(workerId, mac, pswd);
		return this;
	}

	@Override
	public void run() {
		try {
			/*
			Docsyncer.lock.lock();

	        DocsResp resp = null;

			String[] act = AnsonHeader.usrAct("sync.jserv", "query", "r/tasks", "query tasks");
			AnsonHeader header = client.header().act(act);

			DocsReq req = (DocsReq) new DocsReq(null).a(A.records);

			AnsonMsg<DocsReq> q = client.<DocsReq>userReq("", AnsonMsg.Port.docsync, req)
									.header(header);

			resp = client.commit(q, errLog);
			
			pullDocs(resp);
			*/
		} catch (Exception ex) {
			// ex.printStackTrace();
		}
		finally {
			Docsyncer.lock.unlock();
		}
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
	ArrayList<DocsResp> pullDocs(DocsResp tasks)
			throws SQLException, AnsonException, IOException, TransException {
		
		ArrayList<DocsResp> res = new ArrayList<DocsResp>(tasks.rs(0).total());
		tasks.rs(0).beforeFirst();
		while (tasks.rs(0).next()) {
			try {
				SyncDoc p = new SyncDoc(tasks.rs(0), localMeta);
					
				res.add(synctier.synClose(
						synctier.synStreamPull(p, localMeta), localMeta));
			}
			catch(Exception e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		}
		
		return res;
	}

	/**
	 * Private nodes push docs to the cloud hub. 
	 * 
	 * @return
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException 
	 * @throws AnsonException 
	 */
	public SyncWorker push() throws TransException, SQLException, AnsonException, IOException {
		if (mode == SyncMode.main || mode == SyncMode.priv) {
			// find local records with shareflag = pub
			AnResultset rs = ((AnResultset) localSt
				.select(localMeta.tbl, "f")
				.cols(SyncDoc.nvCols(localMeta)).col(localMeta.syncflag)
				.whereEq(localMeta.syncflag, SyncFlag.priv)
				.limit(30)
				.rs(localSt.instancontxt(connPriv, synctier.robot))
				.rs(0)).beforeFirst();

			rs.next();

			// upload
			synctier.syncUp(rs, workerId, localMeta,
			  new OnProcess() {
				@Override
				public void proc(int listIndx, int rows, int seq, int totalBlocks, AnsonResp blockResp)
						throws IOException, AnsonException, SemanticException {
					String clientpath;
					try {
						clientpath = rs.getString(listIndx, localMeta.fullpath);
						Utils.logi("[%s/%s] %s: %s / %s, reply: %s",
								listIndx, rows, clientpath, seq, totalBlocks, blockResp.msg());
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}
			  });
		}
		return this;
	}

	public ArrayList<DocsResp> pull() throws AnsonException, IOException, SQLException, TransException {
		DocsResp rsp = synctier.queryTasks(localMeta, synctier.robot.orgId, synctier.robot.deviceId);
		return pullDocs(rsp);
	}

	/**
	 * Verifying each rec-id has a corresponding local file.
	 * 
	 * @param list length should limited. 
	 * @return this
	 * @throws TransException
	 * @throws SQLException
	 */
	public SyncWorker verifyDocs(ArrayList<DocsResp> list) throws TransException, SQLException {

		/*
		AnResultset rs = (AnResultset) localSt
				.select(localMeta.tbl, "t")
				.cols(localMeta.pk, localMeta.fullpath, localMeta.uri, localMeta.mime, localMeta.size)
				.whereIn(localMeta.pk, ids)
				.rs(localSt.instancontxt(connPriv, synctier.robot))
				.rs(0);

		if (rs.total() != ids.size())
			throw new SemanticException("id count (%s) != file records' count (%s)", ids.size(), rs.total());

		while (rs.next()) {
			String p = synctier.resolvePrivRoot(rs.getString(localMeta.uri), localMeta);
			File f = new File(p);
			if (f.exists() && f.length() == rs.getLong(localMeta.size))
				continue;
			throw new SemanticException("Doc doesn't exists. id: %s, uri: %s, expecting path: %s",
					rs.getString(localMeta.pk), uri, p);
		}
		*/
		for (DocsResp r : list) {
			AnResultset rs = (AnResultset) localSt
				.select(localMeta.tbl, "t")
				.cols(localMeta.pk, localMeta.fullpath, localMeta.uri, localMeta.mime, localMeta.size)
				.whereEq(localMeta.org, r.org())
				.whereEq(localMeta.device, mac)
				.whereEq(localMeta.fullpath, r.doc.fullpath())
				.rs(localSt.instancontxt(connPriv, synctier.robot))
				.rs(0);

			rs.beforeFirst().next();
			String p = synctier.resolvePrivRoot(rs.getString(localMeta.uri), localMeta);
			File f = new File(p);

			if (!f.exists())
				throw new SemanticException("Doc doesn't exists.\nid: %s, uri: %s,\nexpecting path: %s",
					rs.getString(localMeta.pk), rs.getString(localMeta.uri), p);
			else if (f.length() != rs.getLong(localMeta.size))
				throw new SemanticException("Saved length (%s) doesn't equal file length (%s).\nid: %s, uri: %s,\nexpecting path: %s",
					rs.getLong(localMeta.size), f.length(), rs.getString(localMeta.pk), rs.getString(localMeta.uri), p);
			else if (!mime(f).equals(rs.getLong(localMeta.mime)))
				throw new SemanticException("Saved mime (%s) doesn't match with file's (%s).\nid: %s, uri: %s,\nexpecting path: %s",
					rs.getLong(localMeta.mime), mime(f), rs.getString(localMeta.pk), rs.getString(localMeta.uri), p);
			else
				continue;
		}

		return this;
	}

	public String org() {
		return synctier.robot.orgId;
	}
	
	public SyncRobot robot() {
		return synctier.robot;
	}

	public DocsResp queryTasks()
			throws SemanticException, AnsonException, IOException {
		return synctier.queryTasks(localMeta, synctier.robot.orgId, workerId);
	}

	public String nodeId() {
		return synctier.robot.deviceId();
	}

	/*
	public SyncWorker login(String workerId, String pswd) throws SemanticException, AnsonException, SsException, IOException, GeneralSecurityException, SQLException {
		this.workerId = workerId;
		
		if (workerId != null && client == null) {
			client = Clients.login(workerId, pswd, "jnode " + workerId);
			robot = new SyncRobot(workerId, null, workerId)
					.device("jnode " + workerId);
			tempDir = String.format("io.oz.sync-%s.%s", mode, workerId); 
			
			new File(tempDir).mkdirs(); 

			AnsonMsg<AnQueryReq> q = client.query("/jnode/worker", "a_users", "u", 0, -1);
			q.body(0).j("a_orgs", "o", "o.orgId = u.orgId")
					.whereEq("=", "u.userId", robot.userId);
			AnsonResp resp = client.commit(q, errLog);
			AnResultset rs = resp.rs(0).beforeFirst();
			if (rs.next())
				robot.orgId(rs.getString("orgId"))
					.orgName(rs.getString("orgName"));
		}
		
		return this;
	}
	*/

//	/**
//	 * @param meta 
//	 * @param family 
//	 * @param deviceId 
//	 * @return response
//	 * @throws IOException 
//	 * @throws AnsonException 
//	 * @throws SemanticException 
//	 */
//	DocsResp queryTasks(DocTableMeta meta, String family, String deviceId) throws SemanticException, AnsonException, IOException {
//		DocsyncReq req = new DocsyncReq(family)
//							.query(meta);
//
//		String[] act = AnsonHeader.usrAct("sync", "list", meta.tbl, deviceId);
//		AnsonHeader header = client.header().act(act);
//
//		AnsonMsg<DocsyncReq> q = client
//				.<DocsyncReq>userReq(uri, Port.docsync, req)
//				.header(header);
//
//		return client.<DocsyncReq, DocsResp>commit(q, errLog);
//	}

//	/**
//	 * Downward synchronizing.
//	 * @param p
//	 * @param worker
//	 * @return doc record (e.g. h_photos)
//	 * @throws AnsonException
//	 * @throws IOException
//	 * @throws TransException
//	 * @throws SQLException
//	 */
//	SyncDoc synStreamPull(SyncDoc p, SyncRobot worker)
//			throws AnsonException, IOException, TransException, SQLException {
//
//		if (!verifyDel(p, worker, localMeta.tbl)) {
//			DocsyncReq req = (DocsyncReq) new DocsyncReq(worker.orgId)
//							.docTabl(localMeta.tbl)
//							.with(p.fullpath(), p.device())
//							.a(A.download);
//
//			String tempath = tempath(p);
//			String path = client.download(uri, Port.docsync, req, tempath);
//			
//			// suppress uri handling, but create a stub file
//			p.uri = "";
//
//			String pid = insertLocalFile(localSt, connPriv, path, p, robot, localMeta);
//			
//			// move
//			String targetPath = DocUtils.resolvExtroot(localSt, connPriv, pid, robot, localMeta);
//			if (verbose)
//				Utils.logi("   [SyncWorker.verbose: end stream download] %s\n-> %s", path, targetPath);
//			Files.move(Paths.get(path), Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);
//		}
//		return p;
//	}

//	String synClose(SyncDoc p, SyncRobot worker, DocTableMeta meta)
//			throws AnsonException, IOException, TransException, SQLException {
//
//		DocsyncReq clsReq = (DocsyncReq) new DocsyncReq(worker.orgId)
//						.with(p.device(), p.fullpath())
//						.docTabl(meta.tbl)
//						.a(A.synclose);
//
//		AnsonMsg<DocsReq> q = client
//				.<DocsReq>userReq(uri, AnsonMsg.Port.docsync, clsReq);
//
//		client.commit(q, errLog);
//
//		return p.recId();
//	}

//	static String insertLocalFile(DATranscxt st, String conn, String path, SyncDoc doc, SyncRobot usr, DocTableMeta meta)
//			throws TransException, SQLException {
//		if (LangExt.isblank(path))
//			throw new SemanticException("Client path can't be null/empty.");
//		
//		Insert ins = st.insert(meta.tbl, usr)
//				.nv(meta.org, usr.orgId())
//				.nv(meta.uri, doc.uri)
//				.nv(meta.filename, doc.pname)
//				.nv(meta.device, usr.deviceId())
//				.nv(meta.fullpath, doc.clientpath)
//				.nv(meta.folder, doc.folder())
//				.nv(meta.shareby, doc.shareby)
//				.nv(meta.shareflag, doc.shareflag)
//				.nv(meta.shareDate, doc.sharedate)
//				;
//		
//		if (!LangExt.isblank(doc.mime))
//			ins.nv(meta.mime, doc.mime);
//		
//		ins.post(Docsyncer.onDocreate(doc, meta, usr));
//
//		SemanticObject res = (SemanticObject) ins.ins(st.instancontxt(conn, usr));
//		String pid = ((SemanticObject) ((SemanticObject) res.get("resulved"))
//				.get(meta.tbl))
//				.getString(meta.pk);
//		
//		return pid;
//	}

//	/** 
//	 * <p>Verify the local file.</p>
//	 * <p>If it is not expected, delete it.</p>
//	 * Two cases need this verification<br>
//	 * 1. the file was downloaded but the task closing was failed<br>
//	 * 2. the previous downloading resulted in the error message and been saved as a file<br>
//	 * 
//	 * @param f
//	 * @param worker 
//	 * @param docTable photo (doc)'s table name, used to resolve target file path if needed
//	 * @return true if file exists and mime and size match (file moved to uri);
//	 * or false if file size and mime doesn't match (tempath deleted)
//	 * @throws IOException 
//	 */
//	protected boolean verifyDel(SyncDoc f, SyncRobot worker, String docTable) throws IOException {
//		String pth = tempath(f);
//		File file = new File(pth);
//		if (!file.exists())
//			return false;
//	
//		long size = f.size;
//		long length = file.length();
//
//		if ( size == length ) {
//			// move temporary file
//			String targetPath = resolvePrivRoot(f.uri);
//			if (Docsyncer.debug)
//				Utils.logi("   %s\n-> %s", pth, targetPath);
//			try {
//				Files.move(Paths.get(pth), Paths.get(targetPath), StandardCopyOption.ATOMIC_MOVE);
//			} catch (Throwable t) {
//				Utils.warn("Moving temporary file failed: %s\n->%s\n  %s\n  %s", pth, targetPath, f.device(), f.clientpath);
//			}
//			return true;
//		}
//		else {
//			try { FileUtils.delete(new File(pth)); }
//			catch (Exception ex) {}
//			return false;
//		}
//	}

//	public String tempath(IFileDescriptor f) {
//		String tempath = f.fullpath().replaceAll(":", "");
//		return EnvPath.decodeUri(tempDir, tempath);
//	}

//	/**
//	 * Synchronizing files to hub using block chain, accessing port {@link Port#docsync}.
//	 * @param localMeta 
//	 * @param rs row count should limited
//	 * @param robot device is required for overriding doc's device field.
//	 * @param onProcess
//	 * @return Sync response list
//	 * @throws SQLException
//	 * @throws TransException 
//	 */
//	List<DocsResp> syncUp(AnResultset rs, OnProcess onProc)
//			throws SQLException, TransException {
//		// return sync(localSt, connPriv, uri, client, errLog, localMeta, rs, robot, onProc);
//
//		// Synclientier synclientier = new Synclientier(uri, client, localMeta, errLog);
//		
//		List<SyncDoc> videos = new ArrayList<SyncDoc>();
//		while (rs.next())
//			videos.add(new SyncDoc(rs, localMeta));
//
//		SessionInf photoUser = client.ssInfo();
//		photoUser.device = workerId;
//
//		return synctier.pushBlocks(videos, photoUser, onProc, errLog);
//	}

	/*
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
	*/

}
