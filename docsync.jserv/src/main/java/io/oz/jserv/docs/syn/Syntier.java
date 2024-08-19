package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.transact.sql.parts.condition.Funcall.count;
import static io.odysz.transact.sql.parts.condition.Funcall.ifElse;
import static io.odysz.transact.sql.parts.condition.Funcall.now;
import static io.odysz.transact.sql.parts.condition.Funcall.sum;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DATranscxt.SemanticsMap;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.syn.DBSyntableBuilder;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.tier.docs.BlockChain;
import io.odysz.semantic.tier.docs.Device;
import io.odysz.semantic.tier.docs.DocUtils;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.ExpSyncDoc;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.meta.DeviceTableMeta;
import io.oz.jserv.docs.x.DocsException;

@WebServlet(description = "Synode Tier: docs-sync", urlPatterns = { "/docs.sync" })
public class Syntier extends ServPort<DocsReq> {
	/** {domain: {jserv: exession-persist}} */
	HashMap<String, Synoder> domains;

	DBSyntableBuilder doctrb;
	public DBSyntableBuilder doctrb() throws SemanticException {
		if (doctrb == null)
			throw new SemanticException("This synode haven't been started.");
		return doctrb;
	}

	final String synode;
	/** DB connection id for this node to synchronize. */
	final String myconn;

	public Syntier() throws SemanticException, SQLException, SAXException, IOException {
		this("test", "test");
	}

	/**
	 * <h5>note</h5>
	 * 
	 * <p>If synoderId is null, will be loaded from
	 * {@link Configs#cfgFile}/table/v [k={@link Configs.keys#synode}], and
	 * {@code null} can not be used before
	 * {@link Syngleton#initSynodetier(String, String, String, String)} or
	 * {@link Configs#init(String, String, String...)} has been called.
	 * 
	 * @param synoderId optional
	 * @param loconn local connection id for this node tier, see {@link #myconn}.
	 * @throws SemanticException
	 * @throws SQLException
	 * @throws SAXException
	 * @throws IOException
	 */
	public Syntier(String synoderId, String loconn)
			throws SemanticException, SQLException, IOException {
		super(Port.dbsyncer);
		synode = isblank(synoderId) ? Configs.getCfg(Configs.keys.synode) : synoderId;
		myconn = loconn;
		
		if (synode == null)
			throw new SemanticException("Synode id must be configured in %s. table %s, k = %s",
					Configs.cfgFullpath, Configs.keys.deftXTableId, Configs.keys.synode);
	}

	private static final long serialVersionUID = 1L;

	public static final int jservx = 0;
	public static final int myconx = 1;

	Synodebot locrobot;

	IUser locrobot() {
		if (locrobot == null)
			locrobot = new Synodebot(synode);
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
		Utils.logi("== %s", jmsg.toString());
		
		AnsonBody jreq = jmsg.body(0); // SyncReq | DocsReq
		String a = jreq.a();
		DocsResp rsp = null;
		try {
			DocsReq docreq = jmsg.body(0);

			if (A.rec.equals(a) || A.download.equals(a)) {
				// Session less
				if (A.rec.equals(a))
					rsp = doc(jmsg.body(0));
//				else if (A.download.equals(a))
//					download(resp, jmsg.body(0), usr);
			} else {
				DocUser usr;
				usr = (DocUser) JSingleton.getSessionVerifier().verify(jmsg.header());

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
				else if (DocsReq.A.registDev.equals(a))
					rsp = registDevice((DocsReq) jmsg.body(0), usr);
		//		else if (AlbumReq.A.updateFolderel.equals(a))
		//			rsp = updateFolderel(jmsg.body(0), usr);

				else
					throw new SemanticException("Request.a, %s, can not be handled.", jreq.a());
			}

			if (rsp != null) {
				write(resp, ok(rsp));
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

	/**
	 * Start this Syntier.
	 * 
	 * <p>This will created an instance of DBSytableBuilder for the domain.</p>
	 * 
	 * @param org
	 * @param domain
	 * @param conn
	 * @param mod
	 * @return
	 * @throws Exception
	 */
	public Syntier start(String org, String domain, String conn, SynodeMode mod)
			throws Exception {
		if (domains == null)
			domains = new HashMap<String, Synoder>();
		if (!domains.containsKey(domain))
			domains.put(domain, new Synoder(org, domain, synode, conn, mod));

		SemanticsMap ss = DATranscxt.initConfigs(conn, DATranscxt.loadSemantics(conn),
			(c) -> new DBSyntableBuilder.SynmanticsMap(synode, c));
		
		// @SuppressWarnings("unused")
		// Synoder synoder =
		domains .get(domain)
				.born(ss.get(smtype.synChange), 0, 0);
		
		doctrb = new DBSyntableBuilder(
				domain, // FIXME this is not correct. 
						// FIXME See {@link DBSyntableBuilder}'s issue ee153bcb30c3f3b868413beace8cc1f3cb5c3f7c. 
				myconn, synode, mod)
				; // .loadNyquvect(conn);
		return this;
	}

	public Synoder synoder(String domain) {
		return domains.get(domain);
	}
	
	DocsResp registDevice(DocsReq body, DocUser usr)
			throws SemanticException, TransException, SQLException, SAXException, IOException {
		String conn = Connects.uri2conn(body.uri());
		DeviceTableMeta devMeta = new DeviceTableMeta(conn);

		if (isblank(body.device().id)) {
			SemanticObject result = (SemanticObject) doctrb()
				.insert(devMeta.tbl, usr)
				.nv(devMeta.synoder, synode)
				.nv(devMeta.devname, body.device().devname)
				.nv(devMeta.owner, usr.uid())
				.nv(devMeta.cdate, now())
				.nv(devMeta.org, usr.orgId())
				.ins(doctrb().instancontxt(Connects.uri2conn(body.uri()), usr));

			String resulved = result.resulve(devMeta.tbl, devMeta.pk, -1);
			return new DocsResp().device(new Device(
				resulved, synode, body.device().devname));
		}
		else {
			if (isblank(body.device().id))
				throw new SemanticException("Error for pdating device name without a device id.");

			doctrb().update(devMeta.tbl, usr)
				.nv(devMeta.cdate, now())
				// .whereEq(devMeta.domain, usr.orgId())
				.whereEq(devMeta.pk, body.device().id)
				.u(doctrb().instancontxt(Connects.uri2conn(body.uri()), usr));

			return new DocsResp().device(new Device(
				body.device().id, synode, body.device().devname));
		}
	}

	private HashMap<String, BlockChain> blockChains;

	private boolean debug;
	/**
	 * Query client paths
	 * @param docsReq
	 * @param usr
	 * @param prf
	 * @param meta
	 * @return album where clientpath in req's fullpath and device also matched
	 * @throws SemanticException
	 * @throws TransException
	 * @throws SQLException
	 */
	DocsResp querySyncs(DocsReq syncReq, IUser usr)
			throws SemanticException, TransException, SQLException {

		if (syncReq.syncQueries() == null)
			throw new SemanticException("Null Query - invalide request.");

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

		AnResultset rs = ((AnResultset) meta
				.selectSynPaths(doctrb(), syncReq.docTabl)
				.col(meta.fullpath)
				.whereEq(meta.device, devid)
				.whereIn(meta.fullpath, kpaths)

				// TODO add file type for performance
				// FIXME issue: what if paths length > limit ?
				.limit(syncReq.limit())
				.rs(doctrb().instancontxt(conn, usr))
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

		checkBlock0(doctrb(), conn, body, (DocUser) usr);

		if (blockChains == null)
			blockChains = new HashMap<String, BlockChain>(2);

		String tempDir = ((DocUser)usr).touchTempDir(conn, body.docTabl);

		BlockChain chain = new BlockChain(body.docTabl, tempDir, body.device().id,
				// body.doc.clientpath, body.doc.createDate, body.doc.folder());
				body.doc);

		// FIXME security breach?
		String id = chainId(usr, chain.doc.clientpath);

		if (blockChains.containsKey(id))
			throw new SemanticException("Why started again?");

		blockChains.put(id, chain);
		return new DocsResp()
				.blockSeq(-1)
				.doc((ExpSyncDoc) new ExpSyncDoc()
					.clientname(chain.doc.clientname())
					.cdate(body.doc.createDate)
					.fullpath(chain.doc.clientpath));
	}

	DocsResp uploadBlock(DocsReq body, IUser usr) throws IOException, TransException {
		String id = chainId(usr, body.doc.clientpath);
		if (!blockChains.containsKey(id))
			throw new SemanticException("Uploading blocks must be accessed after starting chain is confirmed.");

		BlockChain chain = blockChains.get(id);
		chain.appendBlock(body);

		return new DocsResp()
				.blockSeq(body.blockSeq())
				.doc((ExpSyncDoc) new ExpSyncDoc()
					.clientname(chain.doc.clientname())
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
	 * @return response
	 * @throws SQLException
	 * @throws IOException
	 * @throws TransException
	 */
	DocsResp endBlock(DocsReq body, IUser usr)
			throws SQLException, IOException, TransException {
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

		String pid = DocUtils.createFileBy64(doctrb(), conn, photo, usr, meta);

		// move file
		String targetPath = DocUtils.resolvExtroot(doctrb(), conn, pid, usr, meta);

		if (debug)
			Utils.logT(new Object() {}, " %s\n-> %s", chain.outputPath, targetPath);

		Files.move(Paths.get(chain.outputPath), Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);

		onDocreated(pid, conn, meta, usr);

		return new DocsResp()
				.blockSeq(body.blockSeq())
				.doc((ExpSyncDoc) new ExpSyncDoc()
					.recId(pid)
					.device(body.device())
					.folder(photo.folder())
					.clientname(chain.doc.clientname())
					.cdate(body.doc.createDate)
					.fullpath(chain.doc.clientpath));
	}

	DocsResp abortBlock(DocsReq body, IUser usr)
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

	DocsResp createDoc(DocsReq docreq, IUser usr)
			throws TransException, SQLException, IOException, SAXException {
		Utils.warnT(new Object() {}, "Is this really happenning?");

		String conn = Connects.uri2conn(docreq.uri());

		ExpDocTableMeta docm = checkDuplication(doctrb(), docreq, (DocUser) usr);

		ExpSyncDoc photo = docreq.doc;
		String pid = DocUtils.createFileBy64(doctrb(), conn, photo, usr, docm);
	
		onDocreated(pid, conn, docm, usr);
		return new DocsResp().doc(photo);
	}

	private void onDocreated(String pid, String conn, ExpDocTableMeta docm, IUser usr) {
	}

	static void checkBlock0(DBSyntableBuilder st, String conn, DocsReq body, DocUser usr)
			throws TransException, SQLException, IOException {
		if (isblank(body.docTabl))
			throw new DocsException(DocsException.IOError, "DocsReq.docTabl is empty");
	
		if (body.doc == null || isblank(body.doc.folder()))
			throw new DocsException(DocsException.SemanticsError, "Starting a block chain without doc & folder specified?");
	
		if (body.device() == null || isblank(body.device().id))
			throw new DocsException(DocsException.SemanticsError, "Starting a block chain without device specified?");

		if (isblank(body.doc.device()))
			body.doc.device(body.device());

		if (isblank(body.doc.clientpath))
			throw new TransException("Client path is neccessary to start a block chain transaction. Cannot be empty.");

		if (!Connects.getMeta(conn).containsKey(body.docTabl))
			throw new DocsException(DocsException.IOError,
					"DocTabl is unknown to this node: %s, conn: %s",
					body.docTabl, conn);
	
		checkDuplication(st, body, usr);
	}

	static ExpDocTableMeta checkDuplication(DBSyntableBuilder st, DocsReq docreq, DocUser usr)
			throws SemanticException, TransException, SQLException, IOException {
		String conn = Connects.uri2conn(docreq.uri());

		ExpDocTableMeta docm = (ExpDocTableMeta) Connects.getMeta(conn, docreq.docTabl);

		// checkDuplicate(st, conn, docm, usr.deviceId(), docreq.doc.clientpath, usr);
		checkDuplicate(st, conn, docm, docreq.doc.device(), docreq.doc.clientpath, usr);
		return docm;
	}

	static void checkDuplicate(DBSyntableBuilder st, String conn, ExpDocTableMeta meta, String device, String clientpath, IUser usr)
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

	DocsResp delDoc(DocsReq docsReq, IUser usr)
			throws TransException, SQLException, SAXException, IOException {
		String conn = Connects.uri2conn(docsReq.uri());
		ExpDocTableMeta docm = (ExpDocTableMeta) doctrb().getSyntityMeta(docsReq.docTabl);

		DBSyntableBuilder st = (DBSyntableBuilder) doctrb();
		SemanticObject res = (SemanticObject) st
				.delete(docm.tbl, usr)
				.whereEq("device", docsReq.device().id)
				.whereEq("clientpath", docsReq.doc.clientpath)
				.d(st.instancontxt(conn, usr));

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

		DATranscxt st = doctrb();

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
					synode);

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
		// ExpDocTableMeta mph = null; // new ExpDocTableMeta(conn);
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
		DATranscxt st = doctrb();
		AnResultset rs = (AnResultset) st
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
				.rs(st.instancontxt(conn, usr)).rs(0);

		return new DocsResp().doc(new ExpSyncDoc().folder(rs.nxt(), mph));
	}
	
	static String chainId(IUser usr, String clientpathRaw) {
		return usr.sessionId() + " " + clientpathRaw;
	}
}
