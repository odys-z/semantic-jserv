package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.transact.sql.parts.condition.Funcall.count;
import static io.odysz.transact.sql.parts.condition.Funcall.ifElse;
import static io.odysz.transact.sql.parts.condition.Funcall.sum;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonException;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.tier.docs.BlockChain;
import io.odysz.semantic.tier.docs.DocUtils;
import io.odysz.semantic.tier.docs.DocsException;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.syn.DBSynTransBuilder;
import io.oz.syn.SyncUser;
import io.oz.syn.registry.SynodeConfig;
import io.oz.syn.registry.SyntityReg;

/**
 * The access point of document client tiers, accepting doc's pushing.
 * 
 * This ServPort requires a Syndomanager to work, but is actually can support only one domain.
 *
 * @since 0.2.0
 * @author ody
 */
@WebServlet(description = "Synode Tier: docs-sync", urlPatterns = { "/docs.tier" })
public class ExpDoctier extends ServPort<DocsReq> {
	@FunctionalInterface
	public interface IOnDocreate {
		/**
		 * <p>Update any information can be figured out according the created file,
		 * then persist into the file's record.</p>
		 * 
		 * The callback is triggered by {@link ExpDoctier#endBlock(DocsReq, IUser)}, and must be
		 * always be called in a background thread.
		 */
		void onCreate(String conn, String docId, DATranscxt st, IUser usr, ExpDocTableMeta docm, String... path);
	}

	private static final long serialVersionUID = 1L;

	DBSynTransBuilder trb0;

	public DATranscxt syntransBuilder() throws SQLException, TransException {
		if (st == null)
			throw new SemanticException("This synode haven't been started.");
		return st;
	}

	public ExpDoctier() throws Exception {
		super(Port.docstier);
		notifies = new HashMap<String, SynDomanager>();
	}

	/**
	 * 
	 * @since 0.2.0
	 * @param syndomanager
	 * @param docreateHandler 
	 * @throws Exception
	 */
	public ExpDoctier(SynDomanager syndomanager, IOnDocreate docreateHandler) throws Exception {
		super(Port.docstier);
		
		domx = syndomanager;
		
		st   = new DATranscxt(syndomanager.synconn);
		trb0 = new DBSynTransBuilder(domx);

		onCreate = docreateHandler;
		
		try {debug = Connects.getDebug(syndomanager.synconn); }
		catch (Exception e) {debug = false;}

		notifies = new HashMap<String, SynDomanager>();
	}

	SyncUser locrobot;

	private SynDomanager domx;

	IOnDocreate onCreate;
	public ExpDoctier onCreate(IOnDocreate callback) {
		onCreate = callback;
		return this;
	}

	IUser locrobot() {
		if (locrobot == null)
			locrobot = new SyncUser(domx.synode, null, "robot@" + domx.synconn)
							.deviceId(domx.synode);
		return locrobot;
	}

	@Override
	protected void onGet(AnsonMsg<DocsReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		Utils.logi("-- %s", msg.toBlock());
	}

	@Override
	protected void onPost(AnsonMsg<DocsReq> jmsg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		
		DocsResp rsp = null;
		try {
			DocsReq docreq = jmsg.body(0);
			String a = docreq.a();

			if (A.rec.equals(a) || A.download.equals(a)) {
				// Session less
				if (A.rec.equals(a))
					rsp = doc(jmsg.body(0));
			} else {
				DocUser usr = (DocUser) JSingleton
						.getSessionVerifier()
						.verify(jmsg.header());

				if (A.upload.equals(a))
					rsp = createDoc(docreq, usr);
				else if (A.del.equals(a))
					rsp = delDoc(docreq, usr);
				else if (A.selectSyncs.equals(a))
					rsp = querySyncs((DocsReq)jmsg.body(0), usr);

				//
				else if (DocsReq.A.blockStart.equals(a))
					rsp = startBlocks(jmsg.body(0), usr);
				else if (DocsReq.A.blockUp.equals(a))
					rsp = uploadBlock(jmsg.body(0), usr);
				else if (DocsReq.A.blockEnd.equals(a))
					rsp = endBlock(jmsg.body(0), usr);
				else if (DocsReq.A.blockAbort.equals(a))
					rsp = abortBlock(jmsg.body(0), usr);
		//		else if (DocsReq.A.devices.equals(a))
		//			rsp = devices(jmsg.body(0), usr);
		//		else if (DocsReq.A.checkDev.equals(a))
		//			rsp = chkDevname(jmsg.body(0), usr);
//				else if (DocsReq.A.registDev.equals(a))
//					rsp = registDevice((DocsReq) jmsg.body(0), usr);
		//		else if (AlbumReq.A.updateFolderel.equals(a))
		//			rsp = updateFolderel(jmsg.body(0), usr);

				else if (DocsReq.A.requestSyn.equals(a))
					rsp = notifySyndom(jmsg.body(0));

				else
					throw new SemanticException("Request.a, %s, can not be handled.", docreq.a());
			}

			if (rsp != null) {
				write(resp, ok(rsp.syndomain(domx.synode)));
			}
		} catch (DocsException e) {
			write(resp, err(MsgCode.ext, e.ex().toBlock()));
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			if (debug)
				e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} catch (InterruptedException e) {
			if (debug)
				e.printStackTrace();
		} catch (Throwable e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exGeneral, "%s\n%s", e.getClass().getName(), e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	DocsResp notifySyndom(DocsReq body)
			throws SemanticException, AnsonException, SsException, IOException {
		// FIXME instead of re-schedule the syn-worker?
		return new DocsResp().device(body.device());
	}

//	DocsResp registDevice(DocsReq body, DocUser usr)
//			throws SemanticException, TransException, SQLException, SAXException, IOException {
//		String conn = Connects.uri2conn(body.uri());
//		DeviceTableMeta devMeta = new DeviceTableMeta(conn);
//
//		DATranscxt b = syntransBuilder();
//
//		if (isblank(body.device().id)) {
//			SemanticObject result = (SemanticObject) b
//				.insert(devMeta.tbl, usr)
//				.nv(devMeta.synoder, body.device().id)
//				.nv(devMeta.devname, body.device().devname)
//				.nv(devMeta.owner, usr.uid())
//				.nv(devMeta.cdate, now())
//				.nv(devMeta.org, usr.orgId())
//				.ins(b.instancontxt(Connects.uri2conn(body.uri()), usr));
//
//			String resulved = result.resulve(devMeta.tbl, devMeta.pk, -1);
//			return new DocsResp().device(new Device(
//				resulved, body.device().id, body.device().devname));
//		}
//		else {
//			if (isblank(body.device().id))
//				throw new SemanticException("Error for pdating device name without a device id.");
//
//			b.update(devMeta.tbl, usr)
//				.nv(devMeta.cdate, now())
//				// .whereEq(devMeta.domain, usr.orgId())
//				.whereEq(devMeta.pk, body.device().id)
//				.u(b.instancontxt(Connects.uri2conn(body.uri()), usr));
//
////			return new DocsResp().device(new Device(
////				body.device().id, body.device().id, body.device().devname));
//			return new DocsResp().device(body.device());
//		}
//	}

	private HashMap<String, BlockChain> blockChains;

	private boolean debug;

	/** DB updating event targets */
	private final HashMap<String, SynDomanager> notifies;

	/**
	 * Query client paths
	 * @param docsReq
	 * @param usr
	 * @param prf
	 * @param docm
	 * @return album where clientpath in req's fullpath and device also matched
	 * @throws SemanticException
	 * @throws TransException
	 * @throws SQLException
	 */
	DocsResp querySyncs(DocsReq syncReq, IUser usr)
			throws SemanticException, TransException, SQLException {

		if (syncReq.syncQueries() == null)
			throw new SemanticException("Null Query - invalid request.");

		ArrayList<String> paths = new ArrayList<String>(syncReq.syncQueries().size());
		for (String s : syncReq.syncQueries()) {
			paths.add(s);
		}

		String conn = Connects.uri2conn(syncReq.uri());
		ExpDocTableMeta meta = (ExpDocTableMeta) Connects
							.getMeta(conn, syncReq.docTabl);

		String[] kpaths = syncReq.syncingPage().paths() == null ? new String[0]
				: syncReq.syncingPage().paths().keySet().toArray(new String[0]);

		String devid = isblank(syncReq.syncingPage().device) ? 
				syncReq.device() == null ? usr.deviceId()
				: syncReq.device().id
				: syncReq.syncingPage().device;

		DATranscxt b = syntransBuilder();
		AnResultset rs = ((AnResultset) meta
				.selectSynPaths(b, syncReq.docTabl)
				.col(meta.fullpath)
				.whereEq(meta.device, devid)
				.whereIn(meta.fullpath, kpaths)

				// TODO add file type for performance
				// FIXME issue: what if paths length > limit ?
				.limit(syncReq.limit())
				.rs(b.instancontxt(conn, usr))
				.rs(0))
				.beforeFirst();

		return new DocsResp()
				.device(devid)
				.syncingPage(syncReq)
				.pathsPage(rs, meta);
	}

	DocsResp startBlocks(DocsReq body, IUser usr)
			throws IOException, TransException, SQLException, SAXException {
		String conn = Connects.uri2conn(body.uri());

		checkBlock0(st, conn, body, (DocUser) usr);

		if (blockChains == null)
			blockChains = new HashMap<String, BlockChain>(2);

		String tempDir = ((DocUser)usr).touchTempDir(conn, body.docTabl);

		BlockChain chain = new BlockChain(body.docTabl, tempDir, body.device().id, body.doc);

		String id = chainId(usr, body.doc.clientpath);

		if (blockChains.containsKey(id))
			throw new SemanticException("Why started again?");

		blockChains.put(id, chain);
		return new DocsResp()
				.blockSeq(-1)
				.doc((ExpSyncDoc) new ExpSyncDoc()
					.clientname(body.doc.clientname())
					.cdate(body.doc.createDate)
					.fullpath(body.doc.clientpath));
	}

	DocsResp uploadBlock(DocsReq body, IUser usr) throws IOException, TransException {

		if (isblank(body.doc.clientpath))
			throw new SemanticException("Doc's client-path must presenting in each pushing blocks.");

		String id = chainId(usr, body.doc.clientpath);
		if (!blockChains.containsKey(id))
			throw new SemanticException("Uploading blocks must be accessed after starting chain is confirmed.");

		BlockChain chain = blockChains.get(id);
		chain.appendBlock(body);

		return new DocsResp()
				.blockSeq(body.blockSeq())
				.doc((ExpSyncDoc) new ExpSyncDoc()
					.clientname(body.doc.clientname())
					.cdate(body.doc.createDate)
					.fullpath(body.doc.clientpath));
	}

	/**
	 * Finishing doc (block chain) uploading.
	 * 
	 * <p>This method will trigger ext-file handling by which the uri is set to file path starting at
	 * volume environment variable.</p>
	 * 
	 * @param body
	 * @param usr
	 * @param oncreate 
	 * @return response
	 * @throws Exception 
	 * @throws SAXException 
	 */
	DocsResp endBlock(DocsReq body, IUser usr)
			throws SAXException, Exception {
		String chaid = chainId(usr, body.doc.clientpath); // shouldn't reply chain-id to the client?
		BlockChain chain = null;
		if (blockChains.containsKey(chaid)) {
			blockChains.get(chaid).closeChain();
			chain = blockChains.remove(chaid);
		} else
			throw new SemanticException("Ending a block chain which is not exists.");

		String conn = Connects.uri2conn(body.uri());
		ExpDocTableMeta meta = (ExpDocTableMeta) Connects.getMeta(conn, chain.docTabl);

		ExpSyncDoc photo = chain.doc;

		DBSynTransBuilder b = new DBSynTransBuilder(domx);
		String pid = DocUtils.createFileBy64(b, conn, photo, usr, meta);

		if (Anson.startEnvelope(photo.uri64))
			Utils.warnT(new Object() {}, "Must be verfified: Ignoring file moving since envelope is saved into the uri field. TODO wrap this into somewhere, not here.");
		else {
			// move file
			String targetPath = DocUtils.resolvExtroot(b, conn, pid, usr, meta);

			if (debug) {
				Utils.logT(new Object() {}, " %s\n-> %s", chain.outputPath, targetPath);
				Utils.logT(new Object() {}, " %s\n-> %s", Path.of(chain.outputPath).toAbsolutePath(),
														  Path.of(targetPath).toAbsolutePath());
			}

			// Target dir always exists since the semantics handler, by calling ExtFileInsertv2.sql(), has touched it.
			Files.move(Paths.get(chain.outputPath), Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);
			///////////////////////////////////////////////////////

			if (onCreate != null)
				new Thread(() ->
					onCreate.onCreate(conn, pid, b, usr, meta, targetPath),
					f("On doc %s.%s [%s] create", meta.tbl, pid, conn))
				.start();
		}

		return new DocsResp()
				.blockSeq(body.blockSeq())
				.doc((ExpSyncDoc) new ExpSyncDoc()
					.recId(pid)
					.device(body.device())
					.folder(photo.folder())
					.share(photo.shareby, photo.shareflag(), photo.sharedate)
					.clientname(chain.doc.clientname())
					.cdate(body.doc.createDate)
					.fullpath(chain.doc.clientpath));
	}

	DocsResp abortBlock(DocsReq body, IUser usr)
			throws SQLException, IOException, InterruptedException, TransException {
		return abortBlock(blockChains, body, usr);
	}

	static DocsResp abortBlock(HashMap<String, BlockChain> blockChains, DocsReq body, IUser usr)
			throws SQLException, IOException, InterruptedException, TransException {
		String id = chainId(usr, body.doc.clientpath);
		DocsResp ack = new DocsResp();
		if (blockChains.containsKey(id)) {
			blockChains.get(id).abortChain();
			blockChains.remove(id);
			ack.blockSeqReply = body.blockSeq();
		} else
			ack.blockSeqReply = -1;

		return ack;
	}

	/**
	 * Upload a doc, with an Anson block.
	 * @param docreq
	 * @param usr
	 * @return response
	 * @throws Exception
	 */
	DocsResp createDoc(DocsReq docreq, IUser usr) throws Exception {
		Utils.warnT(new Object() {}, "Is this really happenning?");

		String conn = Connects.uri2conn(docreq.uri());

		DBSynTransBuilder b = new DBSynTransBuilder(domx);
		ExpDocTableMeta docm = checkDuplication(b, docreq, (DocUser) usr);

		ExpSyncDoc photo = docreq.doc;
		String pid = DocUtils.createFileBy64(b, conn, photo, usr, docm);
	
		onCreate.onCreate(conn, pid, b, usr, docm);
		return new DocsResp().doc(photo);
	}

	static void checkBlock0(DATranscxt st, String conn, DocsReq body, DocUser usr)
			throws TransException, SQLException, IOException {
		if (isblank(body.docTabl))
			throw new DocsException(DocsException.IOError, "DocsReq.docTabl is empty");
	
		if (body.doc == null || isblank(body.doc.folder()))
			throw new DocsException(DocsException.SemanticsError, "Starting a block chain without doc & folder specified?");
	
		if (body.device() == null || isblank(body.device().id))
			throw new DocsException(DocsException.SemanticsError, "Starting a block chain without device specified?");

		if (isblank(body.doc.shareflag()))
			Utils.warn("[Error 0.7.6 (%s)] Document's sharing flag is not specified. Doc: [%s] %s",
					DocsException.SemanticsError, 
					body.doc.recId, body.doc.pname);

		if (isblank(body.doc.device()))
			body.doc.device(body.device());

		if (isblank(body.doc.clientpath))
			throw new TransException("Client path is neccessary to start a block chain transaction. Cannot be empty.");

		if (!Connects.getMeta(conn).containsKey(body.docTabl))
			throw new DocsException(DocsException.IOError, f(
					"DocTabl is unknown to this node: %s, uri %s -> conn %s",
					body.docTabl, body.uri(), conn));
	
		checkDuplication(st, body, usr);
	}

	static ExpDocTableMeta checkDuplication(DATranscxt st, DocsReq docreq, DocUser usr)
			throws SemanticException, TransException, SQLException, IOException {
		String conn = Connects.uri2conn(docreq.uri());

		ExpDocTableMeta docm = (ExpDocTableMeta) Connects.getMeta(conn, docreq.docTabl);

		// checkDuplicate(st, conn, docm, usr.deviceId(), docreq.doc.clientpath, usr);
		checkDuplicate(st, conn, docm, docreq.doc.device(), docreq.doc.clientpath, usr);
		return docm;
	}

	static void checkDuplicate(DATranscxt st, String conn, ExpDocTableMeta meta,
			String device, String clientpath, IUser usr)
			throws TransException, SQLException, IOException {

		AnResultset rs = ((AnResultset) st
				.select(meta.tbl, "p")
				.col(count(meta.pk), "cnt")
				.whereEq(meta.device, device)
				.whereEq(meta.fullpath, clientpath)
				.rs(st.instancontxt(conn, usr))
				.rs(0)).nxt();

		if (rs.getInt("cnt") > 0)
			throw new DocsException(DocsException.Duplicate,
					"Found existing file for device & client path.",
					device, clientpath);
	}

	DocsResp delDoc(DocsReq docsReq, IUser usr) throws Exception {
		String conn = Connects.uri2conn(docsReq.uri());
		DBSynTransBuilder b = new DBSynTransBuilder(domx);
		ExpDocTableMeta docm = (ExpDocTableMeta) DBSynTransBuilder.getEntityMeta(conn, docsReq.docTabl);

		SemanticObject res = (SemanticObject) b
				.delete(docm.tbl, usr)
				.whereEq("device", docsReq.device().id)
				.whereEq("clientpath", docsReq.doc.clientpath)
				.d(b.instancontxt(conn, usr));

		return (DocsResp) new DocsResp().data(res.props());
	}

	/**
	 * Read a document (id, uri).
	 *
	 * @param req
	 * @return loaded media record
	 * @throws SQLException
	 * @throws TransException
	 * @throws SemanticException
	 * @throws IOException
	 * @throws SAXException 
	 */
	protected DocsResp doc(DocsReq req)
			throws SemanticException, TransException, SQLException, IOException, SAXException {

		String conn = Connects.uri2conn(req.uri());
		ExpDocTableMeta meta = (ExpDocTableMeta) Connects.getMeta(conn, req.docTabl);

		DATranscxt st = syntransBuilder();

		AnResultset rs = (AnResultset) st.select(meta.tbl, "p")
				.cols_byAlias("p", meta.pk, meta.org,
					meta.resname, meta.createDate, meta.folder,
					meta.fullpath, meta.device, meta.uri,
					meta.shareflag, meta.shareDate, meta.mime, meta.shareby)
				.whereEq("p." + meta.pk, req.pageInf.mergeArgs().getArg(meta.pk))
				.rs(st.instancontxt(conn, locrobot())).rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find file for id: '%s' (with permission of %s)",
					req.doc != null && !isblank(req.doc.recId)
					? req.doc.recId
					: !isblank(req.pageInf)
					? !isblank(req.pageInf.mapCondts)
					  ? req.pageInf.mapCondts.get("pid")
					  : isNull(req.pageInf.arrCondts) ? null : req.pageInf.arrCondts.get(0)[1]
					: null,
					domx.synode);

		return new DocsResp().doc(rs, meta);
	}

	/**
	 * Load a folder record.
	 * @param req
	 * @param usr
	 * @return response
	 * @throws SemanticException
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException
	 * @throws SAXException 
	 */
	protected DocsResp folder(DocsReq req, IUser usr, ExpDocTableMeta mph)
			throws SemanticException, TransException, SQLException, IOException, SAXException {

		String conn = Connects.uri2conn(req.uri());
		JUserMeta musr = new JUserMeta(conn);

		/* folder's tree node
		 select * from (
		 select '%1$s' || '.' || folder, folder, max(tags) tags, max(shareby) shareby, folder pname,
		 folder sort, family || '.' || folder fullpath, 'gallery' nodetype, css,
		 sum(case when substring(mime, 0, 6) = 'image' then 1 else 0 end) img,
		 sum(case when substring(mime, 0, 6) = 'video' then 1 else 0 end) mov,
		 sum (case when substring(mime, 0, 6) = 'audio' then 1 else 0 end) wav,
		 sum(CASE WHEN geox != 0 THEN 1 ELSE 0 END) geo , 0 fav, mime
		 from h_photos f where f.family = '%1$s' group by f.folder
		 ) where img > 0 or mov > 0 or wav > 0
		 */
		DATranscxt b = syntransBuilder();
		AnResultset rs = (AnResultset) b
				.select(mph.tbl, "p")
				.j(musr.tbl, "u", String.format("u.%s = p.%s", musr.pk, mph.shareby))
				.col(mph.pk) // .col(mph.css)
				.col(sum(ifElse("substr(mime, 0, 6) = 'image'", 1, 0)), "img")
				.col(sum(ifElse("substr(mime, 0, 6) = 'video'", 1, 0)), "mov")
				.col(sum(ifElse("substr(mime, 0, 6) = 'audio'", 1, 0)), "wav")
				.col(sum(ifElse("geox != 0", 1, 0)), "geo")
				.col(mph.mime).col(mph.createDate)
				.col(mph.folder, mph.resname)
				.col(musr.uname, mph.shareby)
				.whereEq("p." + mph.folder, req.pageInf.mergeArgs().getArg("pid"))
				.rs(b.instancontxt(conn, usr)).rs(0);

		return new DocsResp().doc(new ExpSyncDoc().folder(rs.nxt(), mph));
	}
	
	String missingFile = "";

	public ExpDoctier missingFile(String onlyPng) {
		missingFile = onlyPng;
		return this;
	}
	
	static String chainId(IUser usr, String clientpathRaw) {
		return usr.sessionId() + " " + clientpathRaw;
	}

	/**
	 * Register notifying of syn-worker.
	 * 
	 * @param cfg
	 * @param onSyntities
	 * @return this
	 */
	public ServPort<?> registSynEvent(SynodeConfig cfg, List<SyntityReg> onSyntities) {
		if (cfg.syncIns > 0 && !isNull(onSyntities))
		for (SyntityReg syntity : onSyntities)
			// one domainx for multiple Syntities
			notifies.put(syntity.table, domx);
		return this;
	}
}
