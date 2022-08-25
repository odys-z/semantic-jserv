package io.odysz.semantic.tier.docs.sync;

import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Configs;
import io.odysz.common.EnvPath;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.ShExtFile;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.file.ISyncFile;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.FileStream;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Delete;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;
import io.oz.album.PhotoRobot;

@WebServlet(description = "Document uploading tier", urlPatterns = { "/docs.sync" })
public class Docsyncer extends ServPort<DocsReq> {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public Docsyncer() {
		super(Port.docsync);
	}

	public static final String keyMode = "sync-mode";
	public static final String keyInterval = "sync-interval-min";
	public static final String keySynconn = "sync-conn-id";
	public static final String keySyntablMeta = "sync-table-meta";

	public static final String cloudHub = "cloud-hub";
	public static final String mainStorage = "main-storage";
	public static final String privateStorage = "private-storage";

	public static final String taskHubBuffered = "hub-buf";
	public static final String taskPushByMain = "main-push";

	public static final String tablSyncTasks = "sync_tasks";

	static ReentrantLock lock;

	protected static DATranscxt st;

	private static ScheduledExecutorService scheduler;

	@SuppressWarnings("unused")
	private static ScheduledFuture<?> schedualed;
	private static int mode;
	/** connection for update task records at cloud hub */
	private static String connHub;
	private static String targetablHub;

	public static boolean debug = true;

	static {
		try {
			st = new DATranscxt(null);
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	public static Delete onDel(String clientpath, String device) {
		return null;
	}

	public static Insert onDocreate(ISyncFile doc, String targetabl, IUser usr)
			throws TransException {

		if (SyncWorker.hub == mode && !doc.isPublic())
			return st
				.insert(tablSyncTasks)
				.nv("task", taskHubBuffered)
				.nv("shareby", usr.uid())
				.nv("docId", doc.recId())
				.nv("uri", doc.uri())
				.nv("mime", doc.mime())
				.nv("device", doc.device())
				.nv("clientpath", doc.clientpath())
				.nv("targetabl", targetabl)
				;
		else if (SyncWorker.main == mode && doc.isPublic())
			return st
				.insert(Docsyncer.tablSyncTasks)
				.nv("task", Docsyncer.taskPushByMain)
				.nv("shareby", usr.uid())
				.nv("docId", doc.recId())
				.nv("targetabl", targetabl)
				;
		else if (SyncWorker.priv == mode)
			throw new TransException("TODO");
		else
			Utils.warn("Unknow album serv mode: %s", mode);
		return null;
	}

	public static void init(ServletContextEvent evt) {

		Utils.logi("Starting file synchronizer ...");

		ServletContext ctx = evt.getServletContext();
		String webINF = ctx.getRealPath("/WEB-INF");
		String root = ctx.getRealPath(".");
		// initJserv(root, webINF, ctx.getInitParameter("io.oz.root-key"));
		
		lock = new ReentrantLock();

		scheduler = Executors.newScheduledThreadPool(1);

		int m = 5;  // sync interval
		try { m = Integer.valueOf(Configs.getCfg(keyInterval)); } catch (Exception e) {}

		String conn = Configs.getCfg(keySynconn);
		String targetabl = Configs.getCfg(keySyntablMeta);

		String cfg = Configs.getCfg(keyMode);
		if (Docsyncer.cloudHub.equals(cfg)) {
			mode = SyncWorker.hub;
			connHub = conn;
			targetablHub = targetabl;
		}
		else if (Docsyncer.mainStorage.equals(cfg))
			mode = SyncWorker.main;
		else mode = SyncWorker.priv;
	
        schedualed = scheduler.scheduleAtFixedRate(
        		new SyncWorker(mode, conn, targetabl),
        		0, m, TimeUnit.MINUTES);

		if (ServFlags.file)
			Utils.warn("[ServFlags.file] sync worker scheduled (interval %s minute).", m);
	}

	@Override
	protected void onGet(AnsonMsg<DocsReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		//
		
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
			if (A.records.equals(jreq.a()))
				rsp = query(jreq, usr);
			else if (A.download.equals(jreq.a()))
				download(resp, jreq, usr);
			else if (A.synclose.equals(jreq.a()))
				rsp = synclose(jreq, usr);
			else throw new SemanticException(String.format(
						"request.body.a can not handled: %s\\n",
						jreq.a()));

			if (resp != null)
				write(resp, ok(rsp));
		} catch (SQLException | TransException e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
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
		
		AnResultset rs = (AnResultset) st
				.select(tablSyncTasks, "p")
				.col("device").col("clientpath")
				.col("uri")
				.col("mime")
				.whereEq("device", req.device())
				.whereEq("clientpath", req.clientpath)
				.rs(st.instancontxt(connHub, usr)).rs(0);

		if (!rs.next()) {
			write(resp, err(MsgCode.exDA, "File missing: %s", ""));
		}
		else {
			String mime = rs.getString("mime");
			resp.setContentType(mime);

			String path = resolveHubRoot(rs.getString("uri"));

			FileStream.sendFile(resp.getOutputStream(), path);
		}
	}
	
	public static String resolveHubRoot(String uri) {
		String extroot = ((ShExtFile) DATranscxt
				.getHandler(connHub, targetablHub, smtype.extFile))
				.getFileRoot();
		return EnvPath.decodeUri(extroot, uri);
	}



	/**
	 * Remove sync task.
	 * 
	 * @param jreq
	 * @param usr
	 * @return response
	 * @throws SQLException 
	 * @throws TransException 
	 */
	protected DocsResp synclose(DocsReq jreq, IUser usr) throws TransException, SQLException {
		SemanticObject r = (SemanticObject) st.delete(tablSyncTasks)
		  .whereEq("device", jreq.device())
		  .whereEq("clientpath", jreq.clientpath)
		  .d(st.instancontxt(connHub, usr));
		return (DocsResp) new DocsResp().data(r.props()); 
	}

	protected DocsResp query(DocsReq jreq, IUser usr) throws TransException, SQLException {
		AnResultset rs = (AnResultset) st.select(tablSyncTasks, "t")
				// .whereEq("device", jreq.device())
				// .whereEq("clientpath", jreq.clientpath)
				.whereEq("home", jreq.org)
				.rs(st.instancontxt(connHub, usr))
				.rs(0);

		return (DocsResp) new DocsResp().rs(rs);
	}

}
