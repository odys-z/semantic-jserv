package io.oz.jserv.docs.syn;

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
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jprotocol.JProtocol.OnError;
import io.odysz.semantic.jprotocol.JProtocol.OnOk;
import io.odysz.semantic.jprotocol.JProtocol.OnProcess;
import io.odysz.semantic.jserv.R.AnQueryReq;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.tier.docs.Device;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantic.tier.docs.IFileDescriptor;
import io.odysz.semantic.tier.docs.PathsPage;
import io.odysz.semantics.SessionInf;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.PageInf;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.x.DocsException;

public class Doclientier extends Semantier {
	public boolean verbose = false;

	protected SessionClient client;
	protected OnError errCtx;

	protected DocUser robt;

	/** For download. */
	protected String tempath;

	/** Must be multiple of 12. Default 3 MiB */
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
	
	public Doclientier blockSize(int size) {
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
	public Doclientier(String clientUri, OnError errCtx)
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
	public Doclientier tempRoot(String root) {
		tempath = root; 
		return this;
	}
	
	/**
	 * use {@link #loginWithUri(String, String, String, String)} instead
	 * 
	 * @deprecated 
	 * @param workerId
	 * @param device
	 * @param pswd
	 * @return this
	 * @throws SemanticException 
	 * @throws SQLException
	 * @throws AnsonException
	 * @throws IOException
	 * @throws TransException 
	 * @throws SsException 
	 */
	public Doclientier login(String workerId, String device, String pswd)
			throws SemanticException, AnsonException, SsException, IOException {

		client = Clients.login(workerId, pswd, device);

		return onLogin(client);
	}

	/**
	 * Login to hub, where hub root url is initialized with {@link Clients#init(String, boolean...)}.
	 * 
	 * @param uri
	 * @param workerId
	 * @param device
	 * @param pswd
	 * @return
	 * @throws SemanticException
	 * @throws AnsonException
	 * @throws SsException
	 * @throws IOException
	 */
	public Doclientier loginWithUri(String uri, String workerId, String device, String pswd)
			throws SemanticException, AnsonException, SsException, IOException {

		client = Clients.loginWithUri(uri, workerId, pswd, device);

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
	public Doclientier onLogin(SessionClient client) {
		SessionInf ssinf = client.ssInfo();
		try {
			// robot = new SyncRobot(ssinf.uid(), ssinf.device, tempath, ssinf.device);
			robt = new DocUser(ssinf.uid(), ssinf.userName());
			tempath = FilenameUtils.concat(tempath,
					String.format("io.oz.doc.%s.%s", ssinf.device, ssinf.uid()));
			
			new File(tempath).mkdirs(); 
			
			JUserMeta um = isNull(Connects.getAllConnIds())
					? new JUserMeta() // a temporary solution for client without DB connections
					: (JUserMeta) robt.meta();

			AnsonMsg<AnQueryReq> q = client.query(uri, um.tbl, "u", 0, -1);
			q.body(0)
			 .l(um.om.tbl, "o", String.format("o.%1$s = u.%1$s", um.org))
			 .whereEq("u." + um.pk, robt.uid());

			AnsonResp resp = client.commit(q, errCtx);
			AnResultset rs = resp.rs(0).beforeFirst();
			if (rs.next())
				robt.orgId(rs.getString(um.org))
					.orgName(rs.getString(um.orgName));
			else throw new SemanticException("User identity haven't been reqistered: %s", robt.uid());
		} catch (Exception e) {
			e.printStackTrace();
		}

		return this;
	}
	
	/**
	 * Synchronizing files to a {@link ExpDoctier} using block chain, accessing port {@link Port#docsync}.
	 * This method will use meta to create entity object of doc.
	 * @param meta for creating {@link ExpSyncDoc} object 
	 * @param rs tasks, rows should be limited
	 * @param onProc
	 * @return Sync response list
	 * @throws TransException 
	 * @throws AnsonException 
	 * @throws IOException 
	 */
	List<DocsResp> syncUp(ExpDocTableMeta meta, AnResultset rs, OnProcess onProc)
			throws TransException, AnsonException, IOException {
		List<ExpSyncDoc> videos = new ArrayList<ExpSyncDoc>();
		try {
			while (rs.next())
				videos.add(new ExpSyncDoc(rs, meta));

			return syncUp(meta.tbl, videos, onProc);
		} catch (SQLException e) {
			e.printStackTrace();
			return null;
		}
	}

	public List<DocsResp> syncUp(String tabl, List<? extends ExpSyncDoc> videos,
			OnProcess onProc, OnOk... docOk)
			throws TransException, AnsonException, IOException {
		return pushBlocks(
				tabl, videos, onProc,
				isNull(docOk) ? new OnOk() {
					@Override
					public void ok(AnsonResp resp)
							throws IOException, AnsonException { }
				} : docOk[0],
				errCtx);
	}

	/**
	 * Downward synchronizing.
	 * @param p
	 * @param meta
	 * @return doc record (e.g. h_photos)
	 * @throws AnsonException
	 * @throws IOException
	 * @throws TransException
	 * @throws SQLException
	 */
	ExpSyncDoc synStreamPull(ExpSyncDoc p, ExpDocTableMeta meta)
			throws AnsonException, IOException, TransException, SQLException {

		if (!verifyDel(p, meta)) {
			DocsReq req = (DocsReq) new DocsReq()
							.docTabl(meta.tbl)
							// .org(robot.orgId)
							.queryPath(p.device(), p.fullpath())
							.a(A.download);

			String tempath = tempath(p);
			tempath = client.download(uri, Port.docsync, req, tempath);
		}
		return p;
	}

	protected boolean verifyDel(ExpSyncDoc f, ExpDocTableMeta meta) {
		String pth = tempath(f);
		File file = new File(pth);
		if (!file.exists())
			return false;

		long size = f.size;
		long length = file.length();

		if ( size == length ) {
			// move temporary file
			String targetPath = "";
			if (verbose)
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
	 * [Synchronously]
	 * Upward pushing with BlockChain.
	 * 
	 * @param tbl doc table name
	 * @param videos any doc-table managed records, of which uri shouldn't be loaded,
	 * e.g. use {@link io.odysz.transact.sql.parts.condition.Funcall#extFile(String) extFile()} as sql select expression.
	 * - the method is working in stream mode
	 * @param proc reporting at each block finished
	 * @param docOk callback for implementing asynchronous wrapper
	 * @param onErr
	 * @return list of response
	 */
	public List<DocsResp> pushBlocks(String tbl, List<? extends ExpSyncDoc> videos,
				OnProcess proc, OnOk docOk, OnError ... onErr)
				throws TransException, IOException {
		OnError err = onErr == null || onErr.length == 0 ? errCtx : onErr[0];
		return pushBlocks(client, uri, tbl, videos, blocksize, proc, docOk, err);
	}

	public static List<DocsResp> pushBlocks(SessionClient client, String uri, String tbl,
			List<? extends ExpSyncDoc> videos, int blocksize,
			OnProcess proc, OnOk docOk, OnError errHandler)
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

			ExpSyncDoc p = videos.get(px);
			if (isblank(p.clientpath) || isblank(p.device()))
				throw new DocsException(DocsException.SemanticsError,
						"Docs' pushing requires device id and clientpath.\n" +
						"Doc Id: %s, device id: %s, client-path: %s, resource name: %s",
						p.recId, p.device(), p.clientpath, p.pname);

			DocsReq req  = new DocsReq(tbl, p, uri)
					.device(user.device)
					.resetChain(true)
					.blockStart(p, user);
			
			AnsonMsg<DocsReq> q = client.<DocsReq>userReq(uri, Port.docsync, req)
									.header(header);

			try {
				resp0 = client.commit(q, errHandler);

				String pth = p.fullpath();
				if (!pth.equals(resp0.xdoc.fullpath()))
					Utils.warn("Resp is not replied with exactly the same path: %s",
							resp0.xdoc.fullpath());

				totalBlocks = (int) ((Files.size(Paths.get(pth)) + 1) / blocksize);
				if (proc != null) proc.proc(videos.size(), px, 0, totalBlocks, resp0);

				DocLocks.reading(p.fullpath());
				ifs = new FileInputStream(new File(p.fullpath()));

				String b64 = AESHelper.encode64(ifs, blocksize);
				while (b64 != null) {
					req = new DocsReq(tbl, uri).blockUp(seq, p, b64, user);
					seq++;

					q = client.<DocsReq>userReq(uri, Port.docsync, req)
								.header(header);

					respi = client.commit(q, errHandler);
					if (proc != null) proc.proc(px, videos.size(), seq, totalBlocks, respi);

					b64 = AESHelper.encode64(ifs, blocksize);
				}
				req = new DocsReq(tbl, uri).blockEnd(respi == null ? resp0 : respi, user);

				q = client.<DocsReq>userReq(uri, Port.docsync, req)
							.header(header);
				respi = client.commit(q, errHandler);
				if (proc != null) proc.proc(px, videos.size(), seq, totalBlocks, respi);

				if (docOk != null) docOk.ok(respi);
				reslts.add(respi);
			}
			catch (IOException | TransException | AnsonException ex) { 
				Utils.warn(ex.getMessage());

				if (resp0 != null) {
					req = new DocsReq(tbl, uri).blockAbort(resp0, user);
					req.a(DocsReq.A.blockAbort);
					q = client.<DocsReq>userReq(uri, Port.docsync, req)
								.header(header);
					respi = client.commit(q, errHandler);
				}

				if (ex instanceof IOException)
					continue;
				else errHandler.err(MsgCode.exGeneral, ex.getMessage(),
					ex.getClass().getName(), isblank(ex.getCause()) ? null : ex.getCause().getMessage());
			}
			finally {
				if (ifs != null)
					ifs.close();
				DocLocks.readed(p.fullpath());
			}
		}

		return reslts;
	}

	public String download(String clientUri, String syname, ExpSyncDoc photo, String localpath)
			throws SemanticException, AnsonException, IOException {
		DocsReq req = (DocsReq) new DocsReq(syname, uri);
		req.doc.recId = photo.recId;
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
		OnError errHandler = onErr == null || onErr.length == 0 ? errCtx : onErr[0];
		String[] act = AnsonHeader.usrAct("synclient.java", "synch", "c/photo", "multi synch");
		AnsonHeader header = client.header().act(act);

		DocsReq req = (DocsReq) new DocsReq(docTabl, uri)
					.pageInf(0, -1, "pid", docId)
					.a(A.rec);

		DocsResp resp = null;
		try {
			AnsonMsg<DocsReq> q = client
								.<DocsReq>userReq(uri, Port.docsync, req)
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
		OnError errHandler = onErr == null || onErr.length == 0 ? errCtx : onErr[0];
		String[] act = AnsonHeader.usrAct("synclient.java", "synch", "c/photo", "multi synch");
		AnsonHeader header = client.header().act(act);

		DocsReq req = new DocsReq(docTabl, uri);
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
		DocsReq req = (DocsReq) new DocsReq(tabl, uri)
				.doc(device, clientpath)
				.a(A.del);

		DocsResp resp = null;
		try {
			String[] act = AnsonHeader.usrAct("synclient.java", "del", "d/photo", "");
			AnsonHeader header = client.header().act(act);
			AnsonMsg<DocsReq> q = client.<DocsReq>userReq(uri, Port.docsync, req)
										.header(header);

			resp = client.commit(q, errCtx);
		} catch (AnsonException | SemanticException e) {
			e.printStackTrace();
			errCtx.err(MsgCode.exSemantic, e.getMessage() + " " + (e.getCause() == null ? "" : e.getCause().getMessage()));
		} catch (IOException e) {
			errCtx.err(MsgCode.exIo, e.getMessage() + " " + (e.getCause() == null ? "" : e.getCause().getMessage()));
		}
		return resp;
	}

	/**
	 * [Synchronously]
	 * Create a doc record at server side, then start pushing.
	 * <p>Using block chain for file upload.</p>
	 * 
	 * @param tabl
	 * @param doc
	 * @param follows handling following pushes.
	 * @param errorCtx
	 * @return 
	 * @throws TransException
	 * @throws IOException
	 * @throws SQLException
	 */
	public DocsResp startPush(String tabl, ExpSyncDoc doc, OnOk follows, ErrorCtx ... errorCtx)
			throws TransException, IOException, SQLException {
		List<ExpSyncDoc> videos = new ArrayList<ExpSyncDoc>();
		videos.add(doc);

		List<DocsResp> resps = pushBlocks(tabl, videos, 
				new OnProcess() {
					@Override
					public void proc(int rows, int rx, int seqBlock, int totalBlocks, AnsonResp resp)
							throws IOException, AnsonException, SemanticException {
					}},
				follows, isNull(errorCtx) ? errCtx : errorCtx[0]);
		return isNull(resps) ? null : resps.get(0);
	}
	
	/**
	 * @param page
	 * @param tabl
	 * @return reply
	 * @throws TransException
	 * @throws IOException
	 */
	public <T extends IPort> DocsResp synQueryPathsPage(PathsPage page, String tabl, T port)
			throws TransException, IOException {
		String[] act = AnsonHeader.usrAct("synclient.java", "query", "r/states", "query sync");
		AnsonHeader header = client.header().act(act);

		DocsReq req = (DocsReq) new DocsReq()
				.syncing(page)
				.docTabl(tabl)
				.device(new Device(page.device, null))
				.a(A.selectSyncs); // v 0.1.50

		AnsonMsg<DocsReq> q = client
				.<DocsReq>userReq(uri, port/*MVP 0.2.1 Port.docsync*/, req)
				.header(header);

		DocsResp resp = client.commit(q, errCtx);

		return resp;
	}

	/**
	 * Implementing new device registering together with {@link #queryDevices(String)}.
	 * 
	 * <pre>CREATE TABLE doc_devices (
      synode0 varchar(12)  NOT NULL, -- initial node a device is registered
      device  varchar(12)  NOT NULL, -- ak, generated when registering, but is used together with synode-0 for file identity.
      devname varchar(256) NOT NULL, -- set by user, warn on duplicate, use old device id if user confirmed, otherwise generate a new one.
      mac     varchar(512),          -- an anciliary identity for recognize a device if there are supporting ways to automatically find out a device mac
      orgId   varchar(12)  NOT NULL, -- fk-del, usually won't happen
      owner   varchar(12),           -- or current user, not permenatly bound
      PRIMARY KEY (synode0, device)
      ); -- registered device names. Name is set by user, prompt if he's device names are duplicated
	 * </pre>
	 * @return this
	 * @throws IOException 
	 * @throws AnsonException 
	 * @throws SemanticException 
	 * @since 0.2.0
	 */
	public DocsResp registerDevice(String devname)
			throws SemanticException, AnsonException, IOException {
		String[] act = AnsonHeader.usrAct("synclient.java", "register", A.devices, Port.docsync.name());
		AnsonHeader header = client.header().act(act);

		DocsReq req = (DocsReq) new DocsReq("doc_devices", uri);
		req.pageInf = new PageInf(0, -1, devname);
		req.a(A.registDev);

		AnsonMsg<DocsReq> q = client
			.<DocsReq>userReq(uri, Port.docsync, req)
			.header(header);

		return client.commit(q, errCtx);
	}
	
	public String tempath(IFileDescriptor f) {
		String clientpath = f.fullpath().replaceAll(":", "");
		return EnvPath.decodeUri(tempath, f.device(), FilenameUtils.getName(clientpath));
	}


}
