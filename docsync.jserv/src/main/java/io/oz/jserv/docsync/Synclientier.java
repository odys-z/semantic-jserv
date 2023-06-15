package io.oz.jserv.docsync;

import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;

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
import io.odysz.common.DocLocks;
import io.odysz.common.EnvPath;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.jclient.tier.Semantier;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
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
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.IFileDescriptor;
import io.odysz.semantic.tier.docs.PathsPage;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.SessionInf;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.x.TransException;

/**
 * Doc synchronizing API for both jserv node and device client.
 * 
 * @author odys-z@github.com
 *
 */
public class Synclientier extends Semantier {
	public boolean verbose = false;

	protected SessionClient client;
	protected ErrorCtx errCtx;

	protected SyncRobot robot;

	protected String tempath;

	int blocksize = 3 * 1024 * 1024;

	/**
	 * Change default block size for performance. Default is 3 Mib.
	 * 
	 * @param s must be multiple of 12
	 * @throws SemanticException
	 */
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
		this.errCtx = errCtx;
		this.uri = clientUri;
		
		tempath = ".";
	}
	
	/**
	 * Temporary root will be changed after login.
	 * 
	 * @param root
	 * @return this
	 */
	public Synclientier tempRoot(String root) {
		tempath = root; 
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
	 * @throws AnsonException
	 * @throws SsException
	 * @throws IOException
	 * @throws TransException 
	 */
	public Synclientier login(String workerId, String device, String pswd)
			throws AnsonException, SsException, IOException, TransException {

		client = Clients.login(workerId, pswd, device);

		return onLogin(client);
	}
	
	/**
	 * Start heart beat, create robot for synchronizing,
	 * clean and create local temporary directory for downloading,
	 * load user information.
	 * 
	 * @param client
	 * @return this
	 * @throws TransException 
	 */
	public Synclientier onLogin(SessionClient client) {
		SessionInf ssinf = client.ssInfo();
		try {
			robot = new SyncRobot(ssinf.uid())
					.device(ssinf.device);
			tempath = FilenameUtils.concat(tempath,
					String.format("io.oz.sync.%s.%s", ssinf.device, ssinf.uid()));
			
			new File(tempath).mkdirs(); 
			
			JUserMeta um = null;
			if (Connects.getAllConnIds() == null)
				um = (JUserMeta) robot.meta();
			else // a temporary solution for client without DB connections
				um = new JUserMeta();

			AnsonMsg<AnQueryReq> q = client.query(uri, um.tbl, "u", 0, -1);
			q.body(0)
			 .j(um.orgTbl, "o", String.format("o.%1$s = u.%1$s", um.org))
			 .whereEq("=", "u." + um.pk, robot.userId);

			AnsonResp resp = client.commit(q, errCtx);
			AnResultset rs = resp.rs(0).beforeFirst();
			if (rs.next())
				robot.orgId(rs.getString(um.org))
					.orgName(rs.getString(um.orgName));
			else throw new SemanticException("Synode haven't been reqistered: %s", robot.userId);
		} catch (TransException | AnsonException | SQLException | IOException e) {
			e.printStackTrace();
		}

		return this;
	}
	
	/**
	 * Synchronizing files to hub using block chain, accessing port {@link Port#docsync}.
	 * This method will use meta to create entity object of doc.
	 * @param meta for creating {@link SyncDoc} object 
	 * @param rs tasks, rows should be limited
	 * @param workerId 
	 * @param onProcess
	 * @return Sync response list
	 * @throws TransException 
	 * @throws AnsonException 
	 * @throws IOException 
	 */
	List<DocsResp> syncUp(DocTableMeta meta, AnResultset rs, String workerId, OnProcess onProc)
			throws TransException, AnsonException, IOException {
		List<SyncDoc> videos = new ArrayList<SyncDoc>();
		try {
			while (rs.next())
				videos.add(new SyncDoc(rs, meta));

			return syncUp(meta.tbl, videos, workerId, onProc);
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}

	public List<DocsResp> syncUp(String tabl, List<? extends SyncDoc> videos, String workerId,
			OnProcess onProc, OnDocOk... docOk)
			throws TransException, AnsonException, IOException {
		SessionInf photoUser = client.ssInfo();
		photoUser.device = workerId;

		return pushBlocks(
				tabl, videos, onProc,
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
			DocsReq req = (DocsReq) new DocsReq()
							.docTabl(meta.tbl)
							.org(robot.orgId)
							.queryPath(p.device(), p.fullpath())
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
			if (Docsyncer.verbose)
				Utils.logi("   %s\n-> %s", pth, targetPath);
			try {
				Files.move(Paths.get(pth), Paths.get(targetPath), StandardCopyOption.ATOMIC_MOVE);
			} catch (Throwable t) {
				Utils.warn("Moving temporary file failed: %s\n->%s\n  %s\n  %s",
							pth, targetPath, f.device(), f.fullpath());
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
	 * @param tbl doc table name
	 * @param videos any doc-table managed records, of which uri shouldn't be loaded,
	 * e.g. use {@link io.odysz.transact.sql.parts.condition.Funcall#extFile(String) extFile()} as sql select expression.
	 * - the method is working in stream mode
	 * @param user
	 * @param proc
	 * @param docOk
	 * @param onErr
	 * @return list of response
	 */
	public List<DocsResp> pushBlocks(String tbl, List<? extends SyncDoc> videos,
				OnProcess proc, OnDocOk docOk, ErrorCtx ... onErr)
				throws TransException, IOException {
		ErrorCtx err = onErr == null || onErr.length == 0 ? errCtx : onErr[0];
		return pushBlocks(client, uri, tbl, videos, blocksize, proc, docOk, err);
	}

	public static List<DocsResp> pushBlocks(SessionClient client, String uri, String tbl,
			List<? extends SyncDoc> videos, int blocksize,
			OnProcess proc, OnDocOk docOk, ErrorCtx errHandler)
			throws TransException, IOException {

		SessionInf user = client.ssInfo();

        DocsResp resp0 = null;
        DocsResp respi = null;

		String[] act = AnsonHeader.usrAct("synclient.java", "sync", "c/sync", "push blocks");
		AnsonHeader header = client.header().act(act);

		List<DocsResp> reslts = new ArrayList<DocsResp>(videos.size());

		for ( int px = 0; px < videos.size(); px++ ) {

			FileInputStream ifs = null;
			int seq = 0;
			int totalBlocks = 0;

			SyncDoc p = videos.get(px);
			DocsReq req = new DocsReq(tbl)
					.folder(p.folder())
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

				DocLocks.reading(p.fullpath());
				ifs = new FileInputStream(new File(p.fullpath()));

				String b64 = AESHelper.encode64(ifs, blocksize);
				while (b64 != null) {
					req = new DocsReq(tbl).blockUp(seq, p, b64, user);
					seq++;

					q = client.<DocsReq>userReq(uri, Port.docsync, req)
								.header(header);

					respi = client.commit(q, errHandler);
					if (proc != null) proc.proc(px, videos.size(), seq, totalBlocks, respi);

					b64 = AESHelper.encode64(ifs, blocksize);
				}
				req = new DocsReq(tbl).blockEnd(respi, user);

				q = client.<DocsReq>userReq(uri, Port.docsync, req)
							.header(header);
				respi = client.commit(q, errHandler);
				if (proc != null) proc.proc(px, videos.size(), seq, totalBlocks, respi);

				if (docOk != null) docOk.ok(respi.doc, respi);
				reslts.add(respi);
			}
			catch (IOException | TransException | AnsonException ex) { 
				Utils.warn(ex.getMessage());

				if (resp0 != null) {
					req = new DocsReq(tbl).blockAbort(resp0, user);
					req.a(DocsReq.A.blockAbort);
					q = client.<DocsReq>userReq(uri, Port.docsync, req)
								.header(header);
					respi = client.commit(q, errHandler);
				}

				if (ex instanceof IOException)
					continue;
				else errHandler.err(MsgCode.exGeneral, ex.getMessage(), ex.getClass().getName(), isblank(ex.getCause()) ? null : ex.getCause().getMessage());
			}
			finally {
				if (ifs != null)
					ifs.close();
				DocLocks.readed(p.fullpath());
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
	 * @param docTabl 
	 * @param docId
	 * @param onErr
	 * @return response
	 */
	public DocsResp selectDoc(String docTabl, String docId, ErrorCtx ... onErr) {
		ErrorCtx errHandler = onErr == null || onErr.length == 0 ? errCtx : onErr[0];
		String[] act = AnsonHeader.usrAct("synclient.java", "synch", "c/photo", "multi synch");
		AnsonHeader header = client.header().act(act);

		DocsReq req = new DocsReq(docTabl);
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
	
	public DocsResp listNodes(String docTabl, String org, ErrorCtx ... onErr) {
			ErrorCtx errHandler = onErr == null || onErr.length == 0 ? errCtx : onErr[0];
		String[] act = AnsonHeader.usrAct("synclient.java", "synch", "c/photo", "multi synch");
		AnsonHeader header = client.header().act(act);

		DocsReq req = new DocsReq(docTabl);
		req.a(A.orgNodes);
		req.org = org;

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
	
	public DocsResp synDel(String tabl, String device, String clientpath) {
		DocsReq req = (DocsReq) new DocsReq(tabl)
				.device(device)
				.clientpath(clientpath)
				.a(A.del);

		DocsResp resp = null;
		try {
			String[] act = AnsonHeader.usrAct("synclient.java", "del", "d/photo", "");
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

	DocsResp synClosePush(SyncDoc p, String docTabl)
			throws AnsonException, IOException, TransException, SQLException {

		DocsReq clsReq = (DocsReq) new DocsReq()
						.docTabl(docTabl)
						.org(robot.orgId)
						.queryPath(p.device(), p.fullpath())
						.a(A.synclosePush);

		AnsonMsg<DocsReq> q = client
				.<DocsReq>userReq(uri, AnsonMsg.Port.docsync, clsReq);

		DocsResp r = client.commit(q, errCtx);
		return r;
	}
	
	/**
	 * Tell upper synode to close the doc downloading.
	 * @param p
	 * @param docTabl
	 * @return
	 * @throws SemanticException
	 * @throws AnsonException
	 * @throws IOException
	 */
	DocsResp synClosePull(SyncDoc p, String docTabl)
			throws SemanticException, AnsonException, IOException {
		DocsReq clsReq = (DocsReq) new DocsReq()
						.docTabl(docTabl)
						.org(robot.orgId)
						.queryPath(p.device(), p.fullpath())
						.a(A.synclosePull);

		AnsonMsg<DocsReq> q = client
				.<DocsReq>userReq(uri, AnsonMsg.Port.docsync, clsReq);

		DocsResp r = client.commit(q, errCtx);
		return r;
	}
	
	/**
	 * Insert the locally ready doc (localpath) into table.
	 * Also update meta.syncflag.
	 * 
	 * @param st
	 * @param conn
	 * @param localPath
	 * @param doc
	 * @param usr
	 * @param meta
	 * @return new doc id
	 * @throws TransException
	 * @throws SQLException
	 */
	static String insertLocalFile(DATranscxt st, String conn, String localPath,
			SyncDoc doc, SyncRobot usr, DocTableMeta meta)
			throws TransException, SQLException {

		if (isblank(localPath))
			throw new SemanticException("Client path can't be null/empty.");
		
		long size = new File(localPath).length();

		Insert ins = st.insert(meta.tbl, usr)
				.nv(meta.org(), usr.orgId())
				.nv(meta.uri, doc.uri)
				.nv(meta.resname, doc.pname)
				.nv(meta.synoder, usr.deviceId())
				.nv(meta.fullpath, doc.fullpath())
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
	 * @param tabl
	 * @param doc
	 * @param ok
	 * @param errorCtx
	 * @return 
	 * @throws TransException
	 * @throws IOException
	 * @throws SQLException
	 */
	public DocsResp synInsertDoc(String tabl, SyncDoc doc, OnDocOk ok, ErrorCtx ... errorCtx)
			throws TransException, IOException, SQLException {
		List<SyncDoc> videos = new ArrayList<SyncDoc>();
		videos.add(doc);

		// SessionInf ssInf = client.ssInfo(); // simulating pushing from app

		List<DocsResp> resps = pushBlocks(tabl, videos, 
				new OnProcess() {
					@Override
					public void proc(int rows, int rx, int seqBlock, int totalBlocks, AnsonResp resp)
							throws IOException, AnsonException, SemanticException {
					}},
				ok, isNull(errorCtx) ? errCtx : errorCtx[0]);
		return isNull(resps) ? null : resps.get(0);
	}
	
	/**
	 * @deprecated now clients only match paths with local DB.
	 * 
	 * @param page
	 * @param tabl
	 * @return
	 * @throws TransException
	 * @throws IOException
	 */
	public DocsResp synQueryPathsPage(PathsPage page, String tabl)
			throws TransException, IOException {
		String[] act = AnsonHeader.usrAct("synclient.java", "query", "r/states", "query sync");
		AnsonHeader header = client.header().act(act);

		DocsReq req = (DocsReq) new DocsReq()
				.syncing(page)
				.docTabl(tabl)
				.device(page.device)
				.a(A.records);

//		for (long i = page.start; i < page.end & i < files.size(); i++) {
//			if (i < 0 || i > Integer.MAX_VALUE)
//				throw new SemanticException("Synclientier.queryDocs(): page's range is out of bounds: H%x", i);
//
//			IFileDescriptor p = files.get((int)i);
//			if (isblank(p.fullpath()))
//				continue;
//			req.querySync(p);
//		}

		AnsonMsg<DocsReq> q = client.<DocsReq>userReq(uri, Port.docsync, req)
								.header(header);

		DocsResp resp = client.commit(q, errCtx);

		return resp;
	}

	public String tempath(IFileDescriptor f) {
		String clientpath = f.fullpath().replaceAll(":", "");
		return EnvPath.decodeUri(tempath, f.device(), FilenameUtils.getName(clientpath));
	}


}
