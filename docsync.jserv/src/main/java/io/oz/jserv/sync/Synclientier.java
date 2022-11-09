package io.oz.jserv.sync;

import static io.odysz.common.LangExt.*;

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
import org.apache.commons.io_odysz.FilenameUtils;
import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.AESHelper;
import io.odysz.common.EnvPath;
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
import io.odysz.semantic.tier.docs.DocsPage;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.IFileDescriptor;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.x.TransException;
import io.oz.jserv.sync.SyncWorker.SyncMode;

/**
 * Doc synchronizing API for both jserv node and java client.
 * 
 * @author odys-z@github.com
 *
 */
public class Synclientier extends Semantier {
	public boolean verbose = false;

	protected SessionClient client;
	protected ErrorCtx errCtx;

	protected SyncRobot robot;

	// protected DATranscxt localSt;

	/** connection for update task records at private storage node */
	// String connPriv;

	String tempDir;

	int blocksize = 3 * 1024 * 1024;
	public void bloksize(int s) throws SemanticException {
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
	 * @param errCtx
	 * @throws IOException 
	 * @throws SAXException 
	 * @throws SQLException 
	 * @throws SemanticException 
	 */
	public Synclientier(String clientUri, ErrorCtx errCtx)
			throws SemanticException, IOException {
//		this.client = client;
		this.errCtx = errCtx;
		this.uri = clientUri;
		
		tempDir = ".";
		
//		try {
//			localSt = new DATranscxt(connId);
//		} catch (SQLException | SAXException e) {
//			throw new SemanticException(
//					"Accessing local DB failed with conn %s. Only jnode should throw this."
//					+ "\nex: %s,\nmessage: %s",
//					connId, e.getClass().getName(), e.getMessage());
//		}
//		connPriv = connId;
	}
	
	/**
	 * Temporary root will be changed after login.
	 * 
	 * @param root
	 * @return this
	 */
	public Synclientier tempRoot(String root) {
		tempDir = root; 
		return this;
	}
	
	/**
	 * Login to hub, where hub root url is initialized with {@link Clients#init(String, boolean...)}.
	 * 
	 * @param workerId
	 * @param device
	 * @param pswd
	 * @return this
	 * @throws SQLException
	 * @throws SemanticException
	 * @throws AnsonException
	 * @throws SsException
	 * @throws IOException
	 */
	public Synclientier login(String workerId, String device, String pswd)
			throws SemanticException, AnsonException, SsException, IOException {

		client = Clients.login(workerId, pswd, device);

		robot = new SyncRobot(workerId, workerId)
				.device(device);
		tempDir = FilenameUtils.concat(tempDir,
				String.format("io.oz.sync.%s.%s", tempDir, SyncMode.priv, workerId));
		
		new File(tempDir).mkdirs(); 
		
		JUserMeta um = (JUserMeta) robot.meta();

		AnsonMsg<AnQueryReq> q = client.query(uri, um.tbl, "u", 0, -1);
		q.body(0).j(um.orgTbl, "o", String.format("o.%1$s = u.%1$s", um.org))
				.whereEq("=", "u." + um.pk, robot.userId);
		AnsonResp resp = client.commit(q, errCtx);
		try {
			AnResultset rs = resp.rs(0).beforeFirst();
			if (rs.next())
				robot.orgId(rs.getString(um.org))
					.orgName(rs.getString(um.orgName));
			else throw new SemanticException("Jnode haven't been reqistered: %s", robot.userId);
		} catch (SQLException e) {
			throw new SemanticException("Return of rs is not understandable: %s", e.getMessage());
		}

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
	 * @param rs tasks, rows should be limited
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

		return syncUp(videos, workerId, meta, onProc);
	}

	public List<DocsResp> syncUp(List<? extends SyncDoc> videos, String workerId,
			DocTableMeta meta, OnProcess onProc, OnDocOk... docOk)
			throws SQLException, TransException, AnsonException, IOException {
		SessionInf photoUser = client.ssInfo();
		photoUser.device = workerId;

		return pushBlocks(
				meta, videos, photoUser, onProc,
				isNull(docOk) ? new OnDocOk() {
					@Override
					public void ok(SyncDoc doc, AnsonResp resp)
							throws IOException, AnsonException, TransException { }
				} : docOk[0],
				errCtx);
	}

	public static void setLocalSync(DATranscxt localSt, String conn,
			DocTableMeta meta, SyncDoc doc, String syncflag, SyncRobot robot)
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

		if (!verifyDel(p, meta)) {
			DocsyncReq req = (DocsyncReq) new DocsyncReq(robot.orgId)
							.docTabl(meta.tbl)
							.with(p.device(), p.fullpath())
							.a(A.download);

			String tempath = tempath(p);
			tempath = client.download(uri, Port.docsync, req, tempath);
		}
		return p;
	}


	protected boolean verifyDel(SyncDoc f, DocTableMeta meta) throws IOException {
		String pth = tempath(f);
		File file = new File(pth);
		if (!file.exists())
			return false;

		long size = f.size;
		long length = file.length();

		if ( size == length ) {
			// move temporary file
			String targetPath = ""; //resolvePrivRoot(f.uri, meta);
			if (Docsyncer.debug)
				Utils.logi("   %s\n-> %s", pth, targetPath);
			try {
				Files.move(Paths.get(pth), Paths.get(targetPath), StandardCopyOption.ATOMIC_MOVE);
			} catch (Throwable t) {
				Utils.warn("Moving temporary file failed: %s\n->%s\n  %s\n  %s",
							pth, targetPath, f.device(), f.clientpath);
			}
			return true;
		}
		else {
			try { FileUtils.delete(new File(pth)); }
			catch (Exception ex) {}
			return false;
		}
	}
	

	/**
	 * Upward pushing with BlockChain
	 * 
	 * @param meta
	 * @param videos any doc-table managed records, of which uri shouldn't be loaded,
	 * e.g. use {@link io.odysz.transact.sql.parts.condition.Funcall#extFile(String) extFile()} as sql select expression.
	 * - the method is working in stream mode
	 * @param user
	 * @param proc
	 * @param docOk
	 * @param onErr
	 * @return result list (AnsonResp)
	 */
	public List<DocsResp> pushBlocks(DocTableMeta meta, List<? extends SyncDoc> videos,
				SessionInf user, OnProcess proc, OnDocOk docOk, ErrorCtx ... onErr) throws TransException, IOException {

		ErrorCtx errHandler = onErr == null || onErr.length == 0 ? errCtx : onErr[0];

        DocsResp resp0 = null;
        DocsResp respi = null;

		String[] act = AnsonHeader.usrAct("album.java", "synch", "c/photo", "multi synch");
		AnsonHeader header = client.header().act(act);

		List<DocsResp> reslts = new ArrayList<DocsResp>(videos.size());

		for ( int px = 0; px < videos.size(); px++ ) {

			FileInputStream ifs = null;
			int seq = 0;
			int totalBlocks = 0;

			SyncDoc p = videos.get(px);
			DocsReq req = new DocsReq(meta.tbl)
					.folder("synctest")
					.share(p)
					.device(user.device)
					.resetChain(true)
					.blockStart(p, user);

			AnsonMsg<DocsReq> q = client.<DocsReq>userReq(uri, Port.docsync, req)
									.header(header);

			try {
				resp0 = client.commit(q, errHandler);

				String pth = p.fullpath();
				if (!pth.equals(resp0.doc.fullpath()))
					Utils.warn("Resp is not replied with exactly the same path: %s", resp0.doc.fullpath());

				totalBlocks = (int) ((Files.size(Paths.get(pth)) + 1) / blocksize);
				if (proc != null) proc.proc(videos.size(), px, 0, totalBlocks, resp0);

				ifs = new FileInputStream(new File(p.fullpath()));

				String b64 = AESHelper.encode64(ifs, blocksize);
				while (b64 != null) {
					req = new DocsReq(meta.tbl).blockUp(seq, p, b64, user);
					seq++;

					q = client.<DocsReq>userReq(uri, Port.docsync, req)
								.header(header);

					respi = client.commit(q, errHandler);
					if (proc != null) proc.proc(px, videos.size(), seq, totalBlocks, respi);

					b64 = AESHelper.encode64(ifs, blocksize);
				}
				req = new DocsReq(meta.tbl).blockEnd(respi, user);

				q = client.<DocsReq>userReq(uri, Port.docsync, req)
							.header(header);
				respi = client.commit(q, errHandler);
				if (proc != null) proc.proc(px, videos.size(), seq, totalBlocks, respi);

				if (docOk != null) docOk.ok(p, respi);
				reslts.add(respi);
			}
			catch (IOException | TransException | AnsonException ex) { 
				Utils.warn(ex.getMessage());

				if (resp0 != null) {
					req = new DocsReq(meta.tbl).blockAbort(resp0, user);
					req.a(DocsReq.A.blockAbort);
					q = client.<DocsReq>userReq(uri, Port.docsync, req)
								.header(header);
					respi = client.commit(q, errHandler);
				}

				if (ex instanceof IOException)
					continue;
				else if (onErr == null || onErr.length < 1 || onErr[0] == null)
					throw ex;
				else onErr[0].err(MsgCode.exGeneral, ex.getMessage(), ex.getClass().getName(), isblank(ex.getCause()) ? null : ex.getCause().getMessage());
			}
			finally {
				if (ifs != null)
					ifs.close();
			}
		}

		return reslts;
	}

	public String download(String clientUri, String syname, SyncDoc photo, String localpath)
			throws SemanticException, AnsonException, IOException {
		DocsReq req = (DocsReq) new DocsReq(syname).uri(clientUri);
		req.docId = photo.recId;
		req.a(A.download);
		return client.download(clientUri, Port.docsync, req, localpath);
	}

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
			errHandler.err(MsgCode.exSemantic, e.getMessage() + " " + (e.getCause() == null ? "" : e.getCause().getMessage()));
		} catch (IOException e) {
			errHandler.err(MsgCode.exIo, e.getMessage() + " " + (e.getCause() == null ? "" : e.getCause().getMessage()));
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
			errCtx.err(MsgCode.exSemantic, e.getMessage() + " " + (e.getCause() == null ? "" : e.getCause().getMessage()));
		} catch (IOException e) {
			errCtx.err(MsgCode.exIo, e.getMessage() + " " + (e.getCause() == null ? "" : e.getCause().getMessage()));
		}
		return resp;
	}

	DocsResp synClose(SyncDoc p, DocTableMeta meta)
			throws AnsonException, IOException, TransException, SQLException {

		DocsyncReq clsReq = (DocsyncReq) new DocsyncReq(robot.orgId)
						.with(p.device(), p.fullpath())
						.docTabl(meta.tbl)
						.a(A.synclose);

		AnsonMsg<DocsReq> q = client
				.<DocsReq>userReq(uri, AnsonMsg.Port.docsync, clsReq);

		DocsResp r = client.commit(q, errCtx);
		return r;
		// return p.recId();
	}
	
	static String insertLocalFile(DATranscxt st, String conn, String path, SyncDoc doc, SyncRobot usr, DocTableMeta meta)
			throws TransException, SQLException {
		if (isblank(path))
			throw new SemanticException("Client path can't be null/empty.");
		
		long size = new File(path).length();

		Insert ins = st.insert(meta.tbl, usr)
				.nv(meta.org, usr.orgId())
				.nv(meta.uri, doc.uri)
				.nv(meta.filename, doc.pname)
				.nv(meta.device, usr.deviceId())
				.nv(meta.fullpath, doc.clientpath)
				.nv(meta.folder, doc.folder())
				.nv(meta.size, size)
				.nv(meta.shareby, doc.shareby)
				.nv(meta.shareflag, doc.shareflag)
				.nv(meta.shareDate, doc.sharedate)
				;
		
		if (!isblank(doc.mime))
			ins.nv(meta.mime, doc.mime);
		
		ins.post(Docsyncer.onDocreate(doc, meta, usr));

		SemanticObject res = (SemanticObject) ins.ins(st.instancontxt(conn, usr));
		String pid = ((SemanticObject) ((SemanticObject) res.get("resulved"))
				.get(meta.tbl))
				.getString(meta.pk);
		
		return pid;
	}

	/**
	 * Create a doc record at server side.
	 * <p>Using block chain for file upload.</p>
	 * 
	 * @param meta
	 * @param doc
	 * @param ok
	 * @param errorCtx
	 * @return 
	 * @throws TransException
	 * @throws IOException
	 * @throws SQLException
	 */
	public DocsResp insertSyncDoc(DocTableMeta meta, SyncDoc doc, OnDocOk ok, ErrorCtx ... errorCtx)
			throws TransException, IOException, SQLException {
		List<SyncDoc> videos = new ArrayList<SyncDoc>();
		videos.add(doc);

		SessionInf ssInf = client.ssInfo(); // simulating pushing from app

		List<DocsResp> resps = pushBlocks(meta, videos, ssInf, 
				new OnProcess() {
					@Override
					public void proc(int rows, int rx, int seqBlock, int totalBlocks, AnsonResp resp)
							throws IOException, AnsonException, SemanticException {
					}},
				ok, isNull(errorCtx) ? errCtx : errorCtx[0]);
		return isNull(resps) ? null : resps.get(0);
	}
	
	public DocsResp queryDocs(List<? extends IFileDescriptor> files, DocsPage page)
			throws TransException, IOException, SQLException {
		String[] act = AnsonHeader.usrAct("album.java", "query", "r/states", "query sync");
		AnsonHeader header = client.header().act(act);

		DocsReq req = (DocsReq) new DocsReq().syncing(page).a(A.selectDocs);

		for (int i = page.start; i < page.end & i < files.size(); i++) {
			IFileDescriptor p = files.get(i);
			req.querySync(p);
		}

		AnsonMsg<DocsReq> q = client.<DocsReq>userReq(uri, Port.docsync, req)
								.header(header);

		DocsResp resp = client.commit(q, errCtx);

		return resp;
	}

	public String tempath(IFileDescriptor f) {
		String tempath = f.fullpath().replaceAll(":", "");
		return EnvPath.decodeUri(tempDir, tempath);
	}

}
