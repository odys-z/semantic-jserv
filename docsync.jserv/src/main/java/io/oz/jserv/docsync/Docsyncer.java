package io.oz.jserv.docsync;

import static io.odysz.common.LangExt.is;
import static io.odysz.common.LangExt.isblank;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Configs;
import io.odysz.common.EnvPath;
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
import io.odysz.semantic.tier.docs.IProfileResolver;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Delete;
import io.odysz.transact.sql.Statement;
import io.odysz.transact.sql.Update;
import io.odysz.transact.sql.parts.Logic.op;
import io.odysz.transact.sql.parts.Resulving;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docsync.Dochain.OnChainOk;

/**
 * @deprecated replaced by {@link Synode}
 * @author odys-z@github.com
 *
 */
@WebServlet(description = "Document uploading tier", urlPatterns = { "/docs.del" })
public class Docsyncer extends ServPort<DocsReq> {
	private static final long serialVersionUID = 1L;
	
	/** Flag of verbose and doc-writing privilege.
	 * 
	 *  <p>configuration</p>
	 *  config.xml/t[id=default]/k=docsync.debug
	 *  */
	public static boolean verbose = true;

	static HashMap<String, TableMeta> metas;
	static HashMap<String, OnChainOk> endChainHandlers;

	/** xml configure key: sync-mode */
	public static final String keyMode = "sync-mode";
	/** xml configure key: sync-pooling interval */
	public static final String keyInterval = "sync-interval-min";
	/** xml configure key: sync-db connection id */
	public static final String keySynconn = "sync-conn-id";

	public static final String cloudHub = "hub";
	public static final String mainode = "main";
	public static final String privnode = "private";

	/**
	@SuppressWarnings("unused")
	private static ScheduledFuture<?> schedualed;
	private static ScheduledExecutorService scheduler;
	 */
	private static SynodeMode mode;

	protected static SynodeMeta synodesMeta; 

	// static ReentrantLock lock;

	protected static DATranscxt st;
	/** connection for update sync flages &amp; task records. */
	private static String synconn;

	public static IProfileResolver profilesolver;

	static SyncRobot anonymous;

	static {
		try {
			st = new DATranscxt(null);
			metas = new HashMap<String, TableMeta>();
			synodesMeta = new SynodeMeta();

			anonymous = new SyncRobot("Robot Syncer");
			
			verbose = Configs.getBoolean("docsync.debug");
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void addSyncTable(TableMeta m) {
		metas.put(m.tbl, m);
	}

	public static DocsResp delDocRec(DocsReq req, IUser usr, boolean ...isAdmin)
			throws TransException, SQLException {
		String conn = Connects.uri2conn(req.uri());
		DocTableMeta meta = (DocTableMeta) metas.get(req.docTabl); 

		String device = req.device();

		if (!is(isAdmin)
				&& !isblank(device) && !device.equals(usr.deviceId()))
			throw new SemanticException("User (id %s, device %s) is trying to delete a file from another device? (req.device = %s)",
					usr.uid(), usr.deviceId(), req.device());
		
		String docId = resolveDoc(usr.orgId(), req.clientpath(), device, meta, usr, conn);
		
		if (isblank(docId))
			throw new SemanticException("No record found for doc %s : %s", device, req.clientpath());

		Delete d = st
				.delete(meta.tbl, usr)
				// .whereEq(meta.org, usr.orgId())
				// .whereEq(meta.device, device)
				.whereEq(meta.pk, docId)
				.post(Docsyncer.onDel(docId, meta, usr))
				;
		
		if (req.clientpath() == null && !is(isAdmin))
			throw new SemanticException("This user are not permeted to delete multiple files at on request: %s, device %s",
					usr.uid(), usr.deviceId());
		else if (req.clientpath() != null)
			d.whereEq(meta.fullpath, req.clientpath());

		SemanticObject res = (SemanticObject) d.d(st.instancontxt(conn, usr));
		
		return (DocsResp) new DocsResp().data(res.props()); 
	}

	/**
	 * Find doc id from synode's DB.
	 * 
	 * @param orgId
	 * @param clientpath
	 * @param device
	 * @param meta
	 * @param usr
	 * @param conn
	 * @return doc id
	 * @throws SQLException
	 * @throws TransException
	 */
	static String resolveDoc(String orgId, String clientpath, String device, DocTableMeta meta, IUser usr, String conn)
			throws SQLException, TransException {
		AnResultset rs = ((AnResultset) st.select(meta.tbl, "d")
			.col(meta.pk)
			.whereEq(meta.synoder, device)
			.whereEq(meta.fullpath, clientpath)
			.whereEq(meta.org(), usr.orgId())
			.rs(st.instancontxt(conn, usr))
			.rs(0))
			.beforeFirst();
		if (rs.next())
			return rs.getString(meta.pk);
		else return null;
	}

	/**
	 * <p>Setup sync-flag after doc been synchronized.</p>
	 * <p>If this node works in mode other than {@link SynodeMode#device},
	 * this method will create insert statement into the share-log tasks table, meta.sharelog.</p>
	 * <p>The update statement should committed with insert statement.</p> 
	 * <p>NOTE: syncstamp is automatically set on insert.
	 * see {@link io.odysz.semantic.tier.docs.DocUtils#createFileB64(String, SyncDoc, IUser, DocTableMeta, DATranscxt, Update) createFile64()}
	 * 
	 * @see SyncFlag
	 * 
	 * @param doc
	 * @param meta
	 * @param usr
	 * @return post update with post of inserting share logs
	 * @throws TransException
	 */
	public static Update onDocreate(SyncDoc doc, DocTableMeta meta, IUser usr)
			throws TransException {

		String syn = SyncFlag.start(mode, doc.shareflag());
		return st.update(meta.tbl, usr)
				.nv(meta.syncflag, syn)
				.whereEq(meta.org(), usr.orgId())
				.whereEq(meta.pk, new Resulving(meta.tbl, meta.pk))
				/*
				.post(mode == SynodeMode.device ? null :
					st.insert(meta.sharelog.tbl, usr)
					  .cols(meta.sharelog.insertShorelogCols())
					  .select(st
							.select("a_synodes", "n")
							.cols(meta.sharelog.synid, meta.sharelog.org, meta.sharelog.clientpath, meta.tbl)
							.col(new Resulving(meta.tbl, meta.pk))
							.whereEq("org", usr.orgId())))
				*/
				;
	}

	/**
	 * Initialize doc synchronizer.
	 * @param evt
	 * @param nodeId Jserv node id which is used as sync-worker's login id.
	 * It is not required if the node is running in hub mode.
	 * 
	 * @throws SQLException
	 * @throws SAXException
	 * @throws IOException
	 * @throws SsException 
	 * @throws AnsonException 
	 * @throws TransException 
	 */
	public static void init(String nodeId)
			throws SQLException, SAXException, IOException, AnsonException, SsException, TransException {

		Utils.logi("Starting file synchronizer ...");

		// lock = new ReentrantLock();

		// scheduler = Executors.newScheduledThreadPool(1);

		int m = 5;  // sync interval
		try { m = Integer.valueOf(Configs.getCfg(keyInterval)); } catch (Exception e) {}

		synconn = Configs.getCfg(keySynconn);

		// logmetas = new HashMap<String, SharelogMeta>();

		String cfg = Configs.getCfg(keyMode);
		if (Docsyncer.cloudHub.equals(cfg)) {
			mode = SynodeMode.hub;

			// FIXME oo design error TODO
			// metas.put("a_synclog", new SharelogMeta("h_phots", "pid", synconn));
			// SharelogMeta sharemeta = new SharelogMeta("h_phots", "pid", synconn);
			// metas.put(sharemeta.tbl, sharemeta);

			if (ServFlags.file)
				Utils.logi("[ServFlags.file] sync worker disabled for this node is working in cloud hub mode.");
		}
		else {
			if (Docsyncer.mainode.equals(cfg))
				mode = SynodeMode.main;
			else if (Docsyncer.privnode.equals(cfg))
				mode = SynodeMode.bridge;
			else mode = SynodeMode.device;
		
			/*
			schedualed = scheduler.scheduleAtFixedRate(new SyncWorker(
					mode, nodeId, synconn, nodeId,
					new DocTableMeta("h_photos", "pid", synconn)) // FIXME oo design error TODO
					.login("what's the pswd?"),
					0, m, TimeUnit.MINUTES);
			*/

			if (ServFlags.file)
				Utils.warn("[ServFlags.file] sync worker scheduled for private node (mode %s, interval %s minute).",
						cfg, m);
		}

		try {
			Class<?> reslass = Class.forName(Configs.getCfg("docsync.folder-resolver"));
			Constructor<?> c = reslass.getConstructor(SynodeMode.class);
			profilesolver = (IProfileResolver) c.newInstance(mode);
			
			Utils.logi("[Docsyncer] Working in '%s' mode, folder resolver: %s", mode, reslass.getName());
		} catch (NoSuchMethodException e) {
			throw new SemanticException("Fatal error: can't create folder resolver [k=docsync.flolder-resolver]: %s. No such method found (constructor with parameter of type SynodeMode).",
					Configs.getCfg("docsync.resolver"));
		} catch (ReflectiveOperationException e) {
			throw new SemanticException("Fatal error: can't create folder resolver [k=docsync.flolder-resolver]: %s.",
					Configs.getCfg("docsync.resolver"));
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
		if (verbose)
			Utils.logi("[Docsyncer.debug/album.less GET]");

		try {
			DocsReq jreq = msg.body(0);
			String a = jreq.a();
			if (A.download.equals(a))
				download(resp, msg.body(0), anonymous.orgId(jreq.org));
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			if (verbose) {
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
				if (isblank(jreq.docTabl))
					throw new SemanticException("To push/update a doc via Docsyncer, docTable name can not be null.");

				if (A.records.equals(a))
					rsp = queryPathsPage(jreq, usr);
				else if (A.getstamp.equals(a))
					rsp = getstamp(jreq, usr);
				else if (A.setstamp.equals(a))
					rsp = setstamp(jreq, usr);
				else if (A.orgNodes.equals(a))
					rsp = queryNodes(jreq, usr);
				else if (A.syncdocs.equals(a))
					rsp = querySynodeTasks(jreq, usr);
				else if (A.del.equals(a))
					rsp = delDocRec(jmsg.body(0), usr, verbose);
				else if (A.synclosePush.equals(a))
					rsp = synclosePush(jreq, usr);
				else if (A.synclosePull.equals(a))
					; // for what? rsp = synclosePull(jreq, usr);
				else {
					Dochain chain = new Dochain((DocTableMeta) metas.get(jreq.docTabl), st);
					if (DocsReq.A.blockStart.equals(a)) {
						if (isblank(jreq.subFolder, " - - "))
							throw new SemanticException("Folder of managed doc can not be empty - which is important for saving file. It's required for creating media file.");
						rsp = chain.startBlocks(profilesolver.onStartPush(jmsg.body(0), usr), usr, profilesolver);
					}
					else if (DocsReq.A.blockUp.equals(a))
						rsp = chain.uploadBlock(jmsg.body(0), usr);
					else if (DocsReq.A.blockEnd.equals(a))
						rsp = chain.endBlock(jmsg.body(0), usr, onBlocksFinish);
					else if (DocsReq.A.blockAbort.equals(a))
						rsp = chain.abortBlock(jmsg.body(0), usr);

					else throw new SemanticException(String.format(
						"request.body.a can not be handled: %s",
						a));
				}
			}

			if (resp != null)
				write(resp, ok(rsp));
		} catch (SQLException | SemanticException e) {
			if (verbose) e.printStackTrace();
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (TransException e) {
			if (verbose) e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} catch (InterruptedException e) {
			write(resp, err(MsgCode.exIo, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	private AnsonResp setstamp(DocsReq jreq, IUser usr) {
		return null;
	}

	private AnsonResp getstamp(DocsReq jreq, IUser usr) throws SQLException, TransException {
		DocTableMeta meta = (DocTableMeta) metas.get(jreq.docTabl);

		AnResultset rs = ((AnResultset) st
				.select("a_synodes", "t")
				.col(Funcall.isnull("syncstamp", Funcall.now()), "stamp")
				.whereEq(meta.org(), jreq.org == null ? usr.orgId() : jreq.org)
				.rs(st.instancontxt(synconn, usr))
				.rs(0))
				.beforeFirst();

		return (DocsResp) new DocsResp().stamp(rs.getString("stamp"));
	}

	/**
	 * Query the device's doc page of which the paths can be used for client matching,
	 * e.g. show the files' synchronizing status.
	 * 
	 * @deprecated now paths matching only happens locally.
	 * 
	 * @param jreq's client paths number should be limited
	 * @param usr
	 * @return page response
	 * @throws SQLException
	 * @throws TransException
	 */
	protected AnsonResp queryPathsPage(DocsReq jreq, IUser usr) throws SQLException, TransException {
		DocTableMeta meta = (DocTableMeta) metas.get(jreq.docTabl);

		Object[] kpaths = jreq.syncing().paths() == null ? new Object[0]
				: jreq.syncing().paths().keySet().toArray();

		AnResultset rs = ((AnResultset) st
				.select(jreq.docTabl, "t")
				.cols(SyncDoc.synPageCols(meta))
				.whereEq(meta.org(), jreq.org == null ? usr.orgId() : jreq.org)
				.whereEq(meta.synoder, usr.deviceId())
				// .whereEq(meta.shareby, usr.uid())
				.whereIn(meta.fullpath, Arrays.asList(kpaths).toArray(new String[kpaths.length]))
				.limit(jreq.limit())	// FIXME issue: what if paths length > limit ?
				.rs(st.instancontxt(synconn, usr))
				.rs(0))
				.beforeFirst();

		return (DocsResp) new DocsResp().syncing(jreq).pathsPage(rs, meta);
	}

	/**
	 * List all the family's nodes, both devices &amp; synodes.
	 * 
	 * @param jreq
	 * @param usr
	 * @return
	 * @throws SQLException
	 * @throws TransException
	 */
	protected AnsonResp queryNodes(DocsReq jreq, IUser usr) throws SQLException, TransException {
		// DocTableMeta meta = (DocTableMeta) metas.get(jreq.docTabl);

		AnResultset rs = ((AnResultset) st
				// .select(meta.sharelog.tbl, "t")
				// .cols(meta.sharelog.org, meta.sharelog.synid)
				.select(synodesMeta.tbl, "t")
				.cols(synodesMeta.org, synodesMeta.synid)
				.whereEq(synodesMeta.org, jreq.org == null ? usr.orgId() : jreq.org)
				.orderby(synodesMeta.synid)
				.rs(st.instancontxt(synconn, usr))
				.rs(0))
				.beforeFirst();

		return (DocsResp) new DocsResp().rs(rs);
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
	protected void download(HttpServletResponse resp, DocsReq req, IUser usr)
			throws IOException, SemanticException, TransException, SQLException {
		
		DocTableMeta meta = (DocTableMeta) metas.get(req.docTabl);
		if (meta == null)
			throw new SemanticException("Can't find table meta for %s", req.docTabl);
		
		AnResultset rs = (AnResultset) st
				.select(meta.tbl, "p")
				.col(meta.synoder).col(meta.fullpath)
				.col(meta.uri)
				.col(meta.mime)
				.whereEq(meta.synoder, req.device())
				.whereEq(meta.fullpath, req.clientpath())
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
	 * Mark sync task as finished.
	 * 
	 * @param jreq
	 * @param usr
	 * @return response
	 * @throws SQLException 
	 * @throws TransException 
	 * @throws IOException 
	 */
	protected DocsResp synclosePush(DocsReq jreq, IUser usr) throws TransException, SQLException, IOException {
		DocTableMeta meta = (DocTableMeta) metas.get(jreq.docTabl);
		st.update(meta.tbl, usr)
			.nv(meta.shareflag, SyncFlag.publish)
			.whereEq(meta.org(), jreq.org == null ? usr.orgId() : jreq.org)
			.whereEq(meta.synoder, usr.deviceId())
			.whereEq(meta.fullpath, jreq.clientpath())
			.u(st.instancontxt(synconn, usr));
		
		return (DocsResp) new DocsResp()
				.org(usr.orgId())
				.doc((SyncDoc) new SyncDoc()
						.device(usr.deviceId())
						.fullpath(jreq.clientpath())); 
	}

	/**
	 * Query tasks for synchronizing between synodes.
	 * This method accept tasks querying for different family - request.org not null.  
	 * Otherwise using session's org-id.
	 * @param jreq
	 * @param usr
	 * @return
	 * @throws TransException
	 * @throws SQLException
	 */
	protected DocsResp querySynodeTasks(DocsReq jreq, IUser usr) throws TransException, SQLException {
		if (isblank(jreq.stamp()))
			throw new SemanticException("Can't query sync tasks with empty timestamp.");

		DocTableMeta meta = (DocTableMeta) metas.get(jreq.docTabl);
		AnResultset rs = ((AnResultset) st
				.select(jreq.docTabl, "t")
				.cols(SyncDoc.nvCols(meta))
				.whereEq(meta.org(), jreq.org == null ? usr.orgId() : jreq.org)
				.where(op.lt, meta.stamp, jreq.stamp())
				.limit(jreq.limit())
				.rs(st.instancontxt(synconn, usr))
				.rs(0))
				.beforeFirst();

		return (DocsResp) new DocsResp().rs(rs);
	}

	protected DocsResp selectDoc(DocsReq jreq, IUser usr) throws TransException, SQLException {
		DocTableMeta meta = (DocTableMeta) metas.get(jreq.docTabl);
		AnResultset rs = (AnResultset) st
				.select(jreq.docTabl, "t")
				.cols(SyncDoc.nvCols(meta))
				.whereEq(meta.org(), jreq.org == null ? usr.orgId() : jreq.org)
				.whereEq(meta.pk, jreq.docId)
				.rs(st.instancontxt(synconn, usr))
				.rs(0);
		
		return (DocsResp) new DocsResp().doc(rs, meta);
	}

	/**
	 * Setup synchronizing tasks.
	 */
	protected OnChainOk onBlocksFinish = (Update post, SyncDoc f, DocTableMeta meta, IUser robot) -> {
//		if (mode != SynodeMode.hub) {
//			SharelogMeta shmeta = (SharelogMeta) metas.get(meta.sharelog.tbl);
//			SynodeMeta snode = (SynodeMeta) metas.get(meta.tbl);
//			try {
//				post.post(st
//					.insert(shmeta.tbl, robot)
//					.select(st
//						.select(snode.tbl, "n")
//						// .cols(shmeta.selectSynodeCols())
//						.cols(shmeta.synid, shmeta.org)
//						.whereEq(snode.org, robot.orgId())));
//			} catch (TransException e) {
//				e.printStackTrace();
//			}
//		}
		return post;
	};

	/**
	 * 1. clear share-log (only for {@link SynodeMode#hub})<br>
	 * 
	 * @param docId
	 * @return statement for adding as a post statement
	 */
	public static Statement<?> onDel(String docId, DocTableMeta meta, IUser usr) {
//		if (mode == SynodeMode.hub) {
//			SharelogMeta shmeta = (SharelogMeta) metas.get(meta.sharelog.tbl);
//			return st.delete(shmeta.tbl, usr)
//					.whereEq(shmeta.org, usr.orgId())
//					.whereEq(shmeta.docFk, docId);
//		}
		return null;
	}

	public static Statement<?> onClean(String org, DocTableMeta meta, IUser usr) {
//		if (mode == SynodeMode.hub) {
//			SharelogMeta shmeta = (SharelogMeta) metas.get(meta.sharelog.tbl);
//			return st.delete(shmeta.tbl, usr)
//				.whereEq(shmeta.org, usr.orgId());
//		}
		return null;
	}

}
