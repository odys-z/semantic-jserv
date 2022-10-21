package io.oz.jserv.sync;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
import io.odysz.jclient.tier.Semantier;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.JProtocol.OnDocOk;
import io.odysz.semantic.jprotocol.JProtocol.OnProcess;
import io.odysz.semantic.jserv.R.AnQueryReq;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantic.jsession.SessionInf;
import io.odysz.semantic.tier.docs.DocUtils;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.IFileDescriptor;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.x.TransException;
import io.oz.jserv.sync.SyncFlag.SyncEvent;
import io.oz.jserv.sync.SyncWorker.SyncMode;

public class Synclientier extends Semantier {
	public boolean verbose = false;

	SessionClient client;
	ErrorCtx errCtx;

	SyncRobot robot;

	DATranscxt localSt;
	/** connection for update task records at private storage node */
	String connPriv;

	String tempDir;

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
	 * @param errCtx
	 * @param connId 
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws SQLException 
	 * @throws SemanticException 
	 */
	public Synclientier(String clientUri, SessionClient client, String connId, ErrorCtx errCtx)
			throws SemanticException, SQLException, SAXException, IOException {
		this.client = client;
		this.errCtx = errCtx;
		this.uri = clientUri;
		
		tempDir = "";
		
		localSt = new DATranscxt(connId);
	}
	
	public Synclientier(String clientUri, String connId, ErrorCtx errCtx)
			throws SemanticException, SQLException, SAXException, IOException {
		this(clientUri, null, connId, errCtx);
	}
	
	public Synclientier login(String workerId, String pswd)
			throws SQLException, SemanticException, AnsonException, SsException, IOException {

		client = Clients.login(workerId, pswd, "jnode " + workerId);

		robot = new SyncRobot(workerId, null, workerId)
				.device("jnode " + workerId);
		tempDir = String.format("io.oz.sync.%s.%s", SyncMode.priv, workerId); 
		
		new File(tempDir).mkdirs(); 
		
		JUserMeta um = (JUserMeta) robot.meta();

		AnsonMsg<AnQueryReq> q = client.query(uri, um.tbl, "u", 0, -1);
		q.body(0).j(um.orgTbl, "o", String.format("o.%1$s = u.%1$s", um.org))
				.whereEq("=", "u." + um.pk, robot.userId);
		AnsonResp resp = client.commit(q, errCtx);
		AnResultset rs = resp.rs(0).beforeFirst();
		if (rs.next())
			robot.orgId(rs.getString(um.org))
				.orgName(rs.getString(um.orgName));
		else throw new SemanticException("Jnode haven't been reqistered: %s", robot.userId);

		return this;
	}
	
	/**
	 * @param meta 
	 * @param family 
	 * @param deviceId 
	 * @return response
	 * @throws IOException 
	 * @throws AnsonException 
	 * @throws SemanticException 
	 */
	DocsResp queryTasks(DocTableMeta meta, String family, String deviceId)
			throws SemanticException, AnsonException, IOException {

		DocsyncReq req = new DocsyncReq(family)
							.query(meta);

		String[] act = AnsonHeader.usrAct("sync", "list", meta.tbl, deviceId);
		AnsonHeader header = client.header().act(act);

		AnsonMsg<DocsyncReq> q = client
				.<DocsyncReq>userReq(uri, Port.docsync, req)
				.header(header);

		return client.<DocsyncReq, DocsResp>commit(q, errCtx);
	}
	
	/**
	 * Synchronizing files to hub using block chain, accessing port {@link Port#docsync}.
	 * @param rs row count should limited
	 * @param workerId 
	 * @param meta 
	 * @param onProcess
	 * @return Sync response list
	 * @throws SQLException
	 * @throws TransException 
	 * @throws AnsonException 
	 * @throws IOException 
	 */
	List<DocsResp> syncUp(AnResultset rs, String workerId, DocTableMeta meta, OnProcess onProc)
			throws SQLException, TransException, AnsonException, IOException {
		List<SyncDoc> videos = new ArrayList<SyncDoc>();
		while (rs.next())
			videos.add(new SyncDoc(rs, meta));

		SessionInf photoUser = client.ssInfo();
		photoUser.device = workerId;

		OnDocOk onDocOk = new OnDocOk() {
			@Override
			public void ok(SyncDoc doc, AnsonResp resp) throws IOException, AnsonException, TransException, SQLException {
				String sync0 = rs.getString(meta.syncflag);
				String share = rs.getString(meta.shareflag);
				String f = SyncFlag.to(sync0, SyncEvent.pushEnd, share);
				setLocalSync(localSt, connPriv, meta, doc, f, robot);
			}
		};

		return pushBlocks(meta, videos, photoUser, onProc, onDocOk, errCtx);
	}

	public static void setLocalSync(DATranscxt localSt, String conn, DocTableMeta meta, SyncDoc doc, String syncflag, SyncRobot robot)
			throws TransException, SQLException {
		localSt.update(meta.tbl, robot)
			.nv(meta.syncflag, SyncFlag.hub)
			.whereEq(meta.pk, doc.recId)
			.u(localSt.instancontxt(conn, robot));
	}


	/**
	 * Downward synchronizing.
	 * @param p
	 * @param worker
	 * @param meta 
	 * @return doc record (e.g. h_photos)
	 * @throws AnsonException
	 * @throws IOException
	 * @throws TransException
	 * @throws SQLException
	 */
	SyncDoc synStreamPull(SyncDoc p, DocTableMeta meta)
			throws AnsonException, IOException, TransException, SQLException {

		if (!verifyDel(p, robot, meta)) {
			DocsyncReq req = (DocsyncReq) new DocsyncReq(robot.orgId)
							.docTabl(robot.meta().tbl)
							.with(p.fullpath(), p.device())
							.a(A.download);

			String tempath = tempath(p);
			String path = client.download(uri, Port.docsync, req, tempath);
			
			// suppress uri handling, but create a stub file
			p.uri = "";

			String pid = insertLocalFile(localSt, connPriv, path, p, robot, meta);
			
			// move
			String targetPath = DocUtils.resolvExtroot(localSt, connPriv, pid, robot, meta);
			if (verbose)
				Utils.logi("   [SyncWorker.verbose: end stream download] %s\n-> %s", path, targetPath);
			Files.move(Paths.get(path), Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);
		}
		return p;
	}

	/**
	 * Upward pushing with BlockChain
	 * 
	 * @param meta
	 * @param videos any doc-table managed record, of which uri shouldn't be loaded
	 * - working in stream mode
	 * @param user
	 * @param proc
	 * @param docOk
	 * @param onErr
	 * @return
	 */
	public List<DocsResp> pushBlocks(DocTableMeta meta, List<? extends SyncDoc> videos,
				SessionInf user, OnProcess proc, OnDocOk docOk, ErrorCtx ... onErr) throws TransException, IOException, SQLException {

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
						.device(user.device)
						.resetChain(true)
						.blockStart(p, user);

				AnsonMsg<DocsReq> q = client.<DocsReq>userReq(uri, Port.docsync, req)
										.header(header);

				resp = client.commit(q, errHandler);

				String pth = p.fullpath();
				if (!pth.equals(resp.doc.fullpath()))
					Utils.warn("Resp is not replied with exactly the same path: %s", resp.doc.fullpath());

				int totalBlocks = (int) ((Files.size(Paths.get(pth)) + 1) / blocksize);
				if (proc != null) proc.proc(videos.size(), px, 0, totalBlocks, resp);

				int seq = 0;
				FileInputStream ifs = new FileInputStream(new File(p.fullpath()));
				try {
					String b64 = AESHelper.encode64(ifs, blocksize);
					while (b64 != null) {
						req = new DocsReq(meta.tbl).blockUp(seq, resp, b64, user);
						seq++;

						q = client.<DocsReq>userReq(uri, Port.docsync, req)
									.header(header);

						resp = client.commit(q, errHandler);
						if (proc != null) proc.proc(px, videos.size(), seq, totalBlocks, resp);

						b64 = AESHelper.encode64(ifs, blocksize);
					}
					req = new DocsReq(meta.tbl).blockEnd(resp, user);

					q = client.<DocsReq>userReq(uri, Port.docsync, req)
								.header(header);
					resp = client.commit(q, errHandler);
					if (proc != null) proc.proc(px, videos.size(), seq, totalBlocks, resp);

					if (docOk != null) docOk.ok(p, resp);
					reslts.add(resp);
				}
				catch (IOException | TransException | SQLException ex) {
					Utils.warn(ex.getMessage());

					req = new DocsReq(meta.tbl).blockAbort(resp, user);
					req.a(DocsReq.A.blockAbort);
					q = client.<DocsReq>userReq(uri, Port.docsync, req)
								.header(header);
					resp = client.commit(q, errHandler);
					if (proc != null) proc.proc(videos.size(), px, seq, totalBlocks, resp);

					throw ex;
				}
				finally { ifs.close(); }
			}

			return reslts;
		} catch (IOException e) {
			errHandler.onError(MsgCode.exIo, e.getClass().getName() + " " + e.getMessage());
		} catch (AnsonException | SemanticException e) { 
			errHandler.onError(MsgCode.exGeneral, e.getClass().getName() + " " + e.getMessage());
		}
		return null;
	}

//	public String download(Photo photo, String localpath)
//			throws SemanticException, AnsonException, IOException {
//		DocsReq req = (DocsReq) new DocsReq(meta.tbl).uri(clientUri);
//		req.docId = photo.recId;
//		req.a(A.download);
//		return client.download(clientUri, Port.docsync, req, localpath);
//	}

	/**
	 * Get a doc record from jserv.
	 * 
	 * @param meta 
	 * @param docId
	 * @param onErr
	 * @return response
	 */
	public DocsResp selectDoc(DocTableMeta meta, String docId, ErrorCtx ... onErr) {
		ErrorCtx errHandler = onErr == null || onErr.length == 0 ? errCtx : onErr[0];
		String[] act = AnsonHeader.usrAct("album.java", "synch", "c/photo", "multi synch");
		AnsonHeader header = client.header().act(act);

		DocsReq req = new DocsReq(meta.tbl);
		req.a(A.rec);
		req.docId = docId;

		DocsResp resp = null;
		try {
			AnsonMsg<DocsReq> q = client.<DocsReq>userReq(uri, Port.docsync, req)
										.header(header);

			resp = client.commit(q, errCtx);
		} catch (AnsonException | SemanticException e) {
			errHandler.onError(MsgCode.exSemantic, e.getMessage() + " " + (e.getCause() == null ? "" : e.getCause().getMessage()));
		} catch (IOException e) {
			errHandler.onError(MsgCode.exIo, e.getMessage() + " " + (e.getCause() == null ? "" : e.getCause().getMessage()));
		}
		return resp;
	}
	
	public DocsResp del(DocTableMeta meta, String device, String clientpath) {
		DocsReq req = (DocsReq) new DocsReq(meta.tbl)
				.device(device)
				.clientpath(clientpath)
				.a(A.del);

		DocsResp resp = null;
		try {
			String[] act = AnsonHeader.usrAct("album.java", "del", "d/photo", "");
			AnsonHeader header = client.header().act(act);
			AnsonMsg<DocsReq> q = client.<DocsReq>userReq(uri, Port.docsync, req)
										.header(header);

			resp = client.commit(q, errCtx);
		} catch (AnsonException | SemanticException e) {
			errCtx.onError(MsgCode.exSemantic, e.getMessage() + " " + (e.getCause() == null ? "" : e.getCause().getMessage()));
		} catch (IOException e) {
			errCtx.onError(MsgCode.exIo, e.getMessage() + " " + (e.getCause() == null ? "" : e.getCause().getMessage()));
		}
		return resp;
	}
	
	String synClose(SyncDoc p, DocTableMeta meta)
			throws AnsonException, IOException, TransException, SQLException {

		DocsyncReq clsReq = (DocsyncReq) new DocsyncReq(robot.orgId)
						.with(p.device(), p.fullpath())
						.docTabl(meta.tbl)
						.a(A.synclose);

		AnsonMsg<DocsReq> q = client
				.<DocsReq>userReq(uri, AnsonMsg.Port.docsync, clsReq);

		client.commit(q, errCtx);

		return p.recId();
	}
	
	static String insertLocalFile(DATranscxt st, String conn, String path, SyncDoc doc, SyncRobot usr, DocTableMeta meta)
			throws TransException, SQLException {
		if (LangExt.isblank(path))
			throw new SemanticException("Client path can't be null/empty.");
		
		Insert ins = st.insert(meta.tbl, usr)
				.nv(meta.org, usr.orgId())
				.nv(meta.uri, doc.uri)
				.nv(meta.filename, doc.pname)
				.nv(meta.device, usr.deviceId())
				.nv(meta.fullpath, doc.clientpath)
				.nv(meta.folder, doc.folder())
				.nv(meta.shareby, doc.shareby)
				.nv(meta.shareflag, doc.shareflag)
				.nv(meta.shareDate, doc.sharedate)
				;
		
		if (!LangExt.isblank(doc.mime))
			ins.nv(meta.mime, doc.mime);
		
		ins.post(Docsyncer.onDocreate(doc, meta, usr));

		SemanticObject res = (SemanticObject) ins.ins(st.instancontxt(conn, usr));
		String pid = ((SemanticObject) ((SemanticObject) res.get("resulved"))
				.get(meta.tbl))
				.getString(meta.pk);
		
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
	 * @param meta doc's table name, e.g. h_photos, used to resolve target file path if needed
	 * @return true if file exists and mime and size match (file moved to uri);
	 * or false if file size and mime doesn't match (tempath deleted)
	 * @throws IOException 
	 */
	protected boolean verifyDel(SyncDoc f, SyncRobot worker, DocTableMeta meta) throws IOException {
		String pth = tempath(f);
		File file = new File(pth);
		if (!file.exists())
			return false;
	
		long size = f.size;
		long length = file.length();

		if ( size == length ) {
			// move temporary file
			String targetPath = resolvePrivRoot(f.uri, meta);
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

	public String tempath(IFileDescriptor f) {
		String tempath = f.fullpath().replaceAll(":", "");
		return EnvPath.decodeUri(tempDir, tempath);
	}

	public String resolvePrivRoot(String uri, DocTableMeta localMeta) {
		return DocUtils.resolvePrivRoot(uri, localMeta, connPriv);
	}

}
