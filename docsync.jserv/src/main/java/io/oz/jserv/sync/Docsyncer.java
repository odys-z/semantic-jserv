package io.oz.jserv.sync;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Configs;
import io.odysz.common.EnvPath;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.ShExtFilev2;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.FileStream;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Delete;
import io.odysz.transact.sql.Update;
import io.odysz.transact.sql.parts.Resulving;
import io.odysz.transact.x.TransException;
import io.oz.jserv.sync.Dochain.OnChainOk;
import io.oz.jserv.sync.SyncWorker.SyncMode;

@WebServlet(description = "Document uploading tier", urlPatterns = { "/docs.sync" })
public class Docsyncer extends ServPort<DocsReq> {
	private static final long serialVersionUID = 1L;
	public static boolean debug = true;

	static HashMap<String, DocTableMeta> metas;
	static HashMap<String, OnChainOk> endChainHandlers;

	OnChainOk onCreateHandler;

	public static final String keyMode = "sync-mode";
	public static final String keyInterval = "sync-interval-min";
	public static final String keySynconn = "sync-conn-id";

	/** TODO replace with SyncMode.pub */
	public static final String cloudHub = "hub";
	public static final String mainStorage = "main";
	public static final String privateStorage = "private";

	@SuppressWarnings("unused")
	private static ScheduledFuture<?> schedualed;
	private static SyncMode mode;
	static ReentrantLock lock;
	protected static DATranscxt st;
	/** connection for update sync flages &amp; task records. */
	private static String synconn;

	private static ScheduledExecutorService scheduler;

	static SyncRobot robot;

	static {
		try {
			st = new DATranscxt(null);
			metas = new HashMap<String, DocTableMeta>();

			robot = new SyncRobot("Robot Syncer", "");
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void addSyncTable(DocTableMeta m) {
		metas.put(m.tbl, m);
	}

	public static Delete onDel(String clientpath, String device) {
		return null;
	}

	public static DocsResp delDocRec(DocsReq req, IUser usr, boolean ...isAdmin)
			throws TransException, SQLException {
		String conn = Connects.uri2conn(req.uri());
		DocTableMeta meta = metas.get(req.docTabl); 

		String device = req.device();

		if (!LangExt.is(isAdmin)
				&& !LangExt.isblank(device) && !device.equals(usr.deviceId()))
			throw new SemanticException("User (id %s, device %s) is trying to delete a file from another device? (req.device = %s)",
					usr.uid(), usr.deviceId(), req.device());
		
		Delete d = st
				.delete(meta.tbl, usr)
				.whereEq(meta.org, usr.orgId())
				.whereEq(meta.device, device)
				.whereEq(meta.fullpath, req.clientpath)
				.post(Docsyncer.onDel(req.clientpath, req.device()))
				;

		SemanticObject res = (SemanticObject) d.d(st.instancontxt(conn, usr));
		
		return (DocsResp) new DocsResp().data(res.props()); 
	}

	/**
	 * <p>Setup sync-flag after doc been synchronized</p>
	 * @see SyncFlag
	 * 
	 * @param doc
	 * @param meta
	 * @param usr
	 * @return post update
	 * @throws TransException
	 */
	public static Update onDocreate(SyncDoc doc, DocTableMeta meta, IUser usr)
			throws TransException {

		String syn = SyncFlag.start(mode, doc.shareflag());
		return st.update(meta.tbl, usr)
				.nv(meta.syncflag, syn)
				.whereEq(meta.org, usr.orgId())
				.whereEq(meta.pk, new Resulving(meta.tbl, meta.pk))
				;
	}

	/**
	 * Initialize doc synchronizer.
	 * @param evt
	 * @param nodeId Jserv node id which is used as sync-worker's login id.
	 * It is not required if the node is running in hub mode.
	 * 
	 * @throws SemanticException
	 * @throws SQLException
	 * @throws SAXException
	 * @throws IOException
	 */
	public static void init(String nodeId)
			throws SemanticException, SQLException, SAXException, IOException {

		Utils.logi("Starting file synchronizer ...");

		lock = new ReentrantLock();

		scheduler = Executors.newScheduledThreadPool(1);

		int m = 5;  // sync interval
		try { m = Integer.valueOf(Configs.getCfg(keyInterval)); } catch (Exception e) {}

		synconn = Configs.getCfg(keySynconn);

		String cfg = Configs.getCfg(keyMode);
		if (Docsyncer.cloudHub.equals(cfg)) {
			mode = SyncMode.hub;
			if (ServFlags.file)
				Utils.logi("[ServFlags.file] sync worker disabled for node working in cloud hub mode.");
		}
		else {
			if (Docsyncer.mainStorage.equals(cfg))
				mode = SyncMode.main;
			else mode = SyncMode.priv;
		
			schedualed = scheduler.scheduleAtFixedRate(
					new SyncWorker(mode, synconn, nodeId, nodeId, new DocTableMeta("h_photos", "pid", synconn)),
					0, m, TimeUnit.MINUTES);

			if (ServFlags.file)
				Utils.warn("[ServFlags.file] sync worker scheduled for private node (mode %s, interval %s minute).",
						cfg, m);
		}
	}

	public Docsyncer() {
		super(Port.docsync);
	}

	public static void addDochainHandler (String tabl, OnChainOk onCreateHandler) {
		if (endChainHandlers != null)
			endChainHandlers = new HashMap<String, OnChainOk>();
		endChainHandlers.put(tabl, onCreateHandler);
	}

	@Override
	protected void onGet(AnsonMsg<DocsReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		//
		if (debug)
			Utils.logi("[Docsyncer.debug/album.less GET]");

		try {
			DocsReq jreq = msg.body(0);
			String a = jreq.a();
			if (A.download.equals(a))
				download(resp, msg.body(0), robot);
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			if (debug) {
				Utils.warn("[Docsyncer.debug]");
				e.printStackTrace();
			}
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
		}	
	}

	@Override
	protected void onPost(AnsonMsg<DocsReq> jmsg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		// 
		resp.setCharacterEncoding("UTF-8");
		try {
			IUser usr = JSingleton.getSessionVerifier().verify(jmsg.header());

			DocsReq jreq = jmsg.body(0);

			AnsonResp rsp = null;
			String a = jreq.a();
			if (A.rec.equals(a))
				rsp = selectDoc(jreq, usr);
			else if (A.download.equals(a)) {
				// non-session access allowed?
				download(resp, jreq, usr);
				return;
			}

			// requires meta for operations
			else {
				if (LangExt.isblank(jreq.docTabl))
					throw new SemanticException("To push/update a doc via Docsyncer, docTable name can not be null.");

				if (A.records.equals(a))
					rsp = queryTasks(jreq, usr);
				else if (A.del.equals(a))
					rsp = delDocRec(jmsg.body(0), usr);
				else if (A.synclose.equals(a))
					rsp = synclose(jreq, usr);
				else {
					Dochain chain = new Dochain(metas.get(jreq.docTabl), st);
					if (DocsReq.A.blockStart.equals(a)) {
						if (LangExt.isblank(jreq.subFolder, " - - "))
							throw new SemanticException("Folder of managed doc can not be empty - which is important for saving file. It's required for creating media file.");
						rsp = chain.startBlocks(jmsg.body(0), usr);
					}
					else if (DocsReq.A.blockUp.equals(a))
						rsp = chain.uploadBlock(jmsg.body(0), usr);
					else if (DocsReq.A.blockEnd.equals(a))
						// synchronization are supposed to be required by a SyncRobot
						rsp = chain.endBlock(jmsg.body(0), (SyncRobot)usr);
					else if (DocsReq.A.blockAbort.equals(a))
						rsp = chain.abortBlock(jmsg.body(0), usr);

					else throw new SemanticException(String.format(
						"request.body.a can not handled: %s",
						a));
				}
			}

			if (resp != null)
				write(resp, ok(rsp));
		} catch (SQLException | TransException e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} catch (InterruptedException e) {
			// e.printStackTrace();
			write(resp, err(MsgCode.exIo, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	/**
	 * <p>Write file stream to response.</p>
	 * 
	 * File path: resolved uri in sync task table.
	 * If the file is not found, write an error response with error code {@link MsgCode#exDA}. 
	 * 
	 * <h6>Issue:</h6>
	 * Actually, if needing continuing at break point, the function should be changed to block chain mode. 
	 * 
	 * @param resp 
	 * @param jreq
	 * @param usr
	 * @throws IOException
	 * @throws SemanticException
	 * @throws TransException
	 * @throws SQLException
	 */
	void download(HttpServletResponse resp, DocsReq req, IUser usr)
			throws IOException, SemanticException, TransException, SQLException {
		
		DocTableMeta meta = metas.get(req.docTabl);
		if (meta == null)
			throw new SemanticException("Can't find table meta for %s", req.docTabl);
		
		AnResultset rs = (AnResultset) st
				.select(meta.tbl, "p")
				.col(meta.device).col(meta.fullpath)
				.col(meta.uri)
				.col(meta.mime)
				.whereEq(meta.device, req.device())
				.whereEq(meta.fullpath, req.clientpath)
				.rs(st.instancontxt(synconn, usr)).rs(0);

		if (!rs.next()) {
			write(resp, err(MsgCode.exDA, "File missing: %s", ""));
		}
		else {
			String mime = rs.getString(meta.mime);
			resp.setContentType(mime);

			String path = resolveHubRoot(req.docTabl, rs.getString(meta.uri));

			FileStream.sendFile(resp.getOutputStream(), path);
		}
	}
	
	public static String resolveHubRoot(String tabl, String uri) {
		String extroot = ((ShExtFilev2) DATranscxt
				.getHandler(synconn, tabl, smtype.extFilev2))
				.getFileRoot();
		return EnvPath.decodeUri(extroot, uri);
	}

	/**
	 * Remove sync task, either by close of by delete.
	 * 
	 * @param jreq
	 * @param usr
	 * @return response
	 * @throws SQLException 
	 * @throws TransException 
	 */
	protected DocsResp synclose(DocsReq jreq, IUser usr) throws TransException, SQLException {
		DocTableMeta meta = metas.get(jreq.docTabl);
		SemanticObject r = (SemanticObject) st
				.update(meta.tbl, usr)
				.nv(meta.shareflag, SyncFlag.publish)
				.whereEq(meta.org, jreq.org == null ? usr.orgId() : jreq.org)
				.whereEq(meta.device, usr.deviceId())
				.whereEq(meta.fullpath, jreq.clientpath)
				.u(st.instancontxt(synconn, usr));
		return (DocsResp) new DocsResp().data(r.props()); 
	}

	/**
	 * Query tasks for synchronizing.
	 * This method accept tasks querying for different family - request.org not null.  
	 * Otherwise using session's org-id.
	 * @param jreq
	 * @param usr
	 * @return
	 * @throws TransException
	 * @throws SQLException
	 */
	protected DocsResp queryTasks(DocsReq jreq, IUser usr) throws TransException, SQLException {
		DocTableMeta meta = metas.get(jreq.docTabl);
		AnResultset rs = ((AnResultset) st
				.select(jreq.docTabl, "t")
				// .cols(meta.org, meta.device, meta.fullpath, meta.shareflag, meta.syncflag)
				.cols(SyncDoc.nvCols(meta))
				.whereEq(meta.org, jreq.org == null ? usr.orgId() : jreq.org)
				// .whereEq(meta.syncflag, SyncFlag.hub)
				.rs(st.instancontxt(synconn, usr))
				.rs(0))
				.beforeFirst();

		return (DocsResp) new DocsResp().rs(rs);
	}

	protected DocsResp selectDoc(DocsReq jreq, IUser usr) throws TransException, SQLException {
		DocTableMeta meta = metas.get(jreq.docTabl);
		AnResultset rs = (AnResultset) st
				.select(jreq.docTabl, "t")
				.cols(SyncDoc.nvCols(meta))
				.whereEq(meta.org, jreq.org == null ? usr.orgId() : jreq.org)
				.whereEq(meta.pk, jreq.docId)
				.rs(st.instancontxt(synconn, usr))
				.rs(0);
		
		return (DocsResp) new DocsResp().doc(rs, meta);
	}
}