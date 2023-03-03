package io.oz.jserv.docsync;

import static io.odysz.common.LangExt.isblank;

import java.io.IOException;
import java.lang.reflect.Constructor;
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
import io.odysz.common.Utils;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantic.tier.docs.IProfileResolver;
import io.odysz.semantics.IUser;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docsync.Dochain.OnChainOk;

@WebServlet(description = "Document uploading tier", urlPatterns = { "/docs.sync" })
public class Synode extends ServPort<DocsReq> {
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

	private static SynodeMode mode;

	protected static SynodeMeta synodesMeta; 

	protected static DATranscxt st;
	/** connection for update sync flages &amp; task records. */
	private static String synconn;

	public static IProfileResolver profilesolver;

	/**
	 * FIXME shouldn't be a map of [tabl, ScheduledFeature]?
	 */
	@SuppressWarnings("unused")
	private static ScheduledFuture<?> schedualed;
	private static ScheduledExecutorService scheduler;
	private static ReentrantLock lock;

	static SyncRobot anonymous;

	static {
		try {
			st = new DATranscxt(null);
			metas = new HashMap<String, TableMeta>();
			synodesMeta = new SynodeMeta();

			anonymous = new SyncRobot("Robot Synode");
			
			verbose = Configs.getBoolean("docsync.debug");
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	OnChainOk onBlocksFinish;
	
	public Synode() {
		super(Port.docsync);
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
	 * @throws SsException 
	 * @throws AnsonException 
	 */
	public static void init(String nodeId)
			throws SemanticException, SQLException, SAXException, IOException, AnsonException, SsException {

		Utils.logi("Starting file synchronizer ...");

		lock = new ReentrantLock();

		scheduler = Executors.newScheduledThreadPool(1);

		int m = 5;  // sync interval
		try { m = Integer.valueOf(Configs.getCfg(keyInterval)); } catch (Exception e) {}

		synconn = Configs.getCfg(keySynconn);

		String cfg = Configs.getCfg(keyMode);
		if (cloudHub.equals(cfg)) {
			mode = SynodeMode.hub;
			if (ServFlags.file)
				Utils.logi("[ServFlags.file] sync worker disabled for this node is working in cloud hub mode.");
		}
		else {
			if (mainode.equals(cfg))
				mode = SynodeMode.main;
			else if (privnode.equals(cfg))
				mode = SynodeMode.bridge;
			else mode = SynodeMode.device;
		
			// FIXME oo design error TODO
			// TODO can be find out from semantics
			scheduler.scheduleAtFixedRate(new SyncWorker(
					mode, nodeId, synconn, nodeId,
					new DocTableMeta("h_photos", "pid", synconn))
					.login("what's the pswd?"),
					0, m, TimeUnit.MINUTES);

			if (ServFlags.file)
				Utils.warn("[ServFlags.file] sync worker scheduled for private node (mode %s, interval %s minute).",
						cfg, m);
		}

		try {
			Class<?> reslass = Class.forName(Configs.getCfg("docsync.folder-resolver"));
			Constructor<?> c = reslass.getConstructor(SynodeMode.class);
			profilesolver = (IProfileResolver) c.newInstance(mode);
			
			Utils.logi("[Syndoe] Working in '%s' mode, folder resolver: %s", mode, reslass.getName());
		} catch (NoSuchMethodException e) {
			throw new SemanticException("Fatal error: can't create folder resolver [k=docsync.flolder-resolver]: %s. No such method found (constructor with parameter of type SynodeMode).",
					Configs.getCfg("docsync.resolver"));
		} catch (ReflectiveOperationException e) {
			throw new SemanticException("Fatal error: can't create folder resolver [k=docsync.flolder-resolver]: %s.",
					Configs.getCfg("docsync.resolver"));
		}
	}

	public static void addSyncTable(TableMeta m) {
		metas.put(m.tbl, m);
	}

	@Override
	protected void onGet(AnsonMsg<DocsReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		
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
				;
			else if (A.download.equals(a)) {
				;
				return;
			}

			// requires meta for operations
			else {
				if (isblank(jreq.docTabl))
					throw new SemanticException("To push/update a doc via Docsyncer, docTable name can not be null.");

				if (A.records.equals(a))
					; // rsp = queryPathsPage(jreq, usr);
				else if (A.getstamp.equals(a))
					; // rsp = getstamp(jreq, usr);
				else if (A.setstamp.equals(a))
					; // rsp = setstamp(jreq, usr);
				else if (A.orgNodes.equals(a))
					; // rsp = queryNodes(jreq, usr);
				else if (A.syncdocs.equals(a))
					; // rsp = querySynodeTasks(jreq, usr);
				else if (A.del.equals(a))
					; // rsp = delDocRec(jmsg.body(0), usr, verbose);
				else if (A.synclosePush.equals(a))
					; // rsp = synclosePush(jreq, usr);
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

}
