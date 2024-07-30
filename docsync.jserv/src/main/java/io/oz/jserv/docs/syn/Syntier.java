package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.transact.sql.parts.condition.Funcall.count;
import static io.odysz.transact.sql.parts.condition.Funcall.ifElse;
import static io.odysz.transact.sql.parts.condition.Funcall.now;
import static io.odysz.transact.sql.parts.condition.Funcall.sum;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DATranscxt.SemanticsMap;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantic.syn.DBSyntableBuilder;
import io.odysz.semantic.syn.SynodeMode;
import io.odysz.semantic.tier.docs.Device;
import io.odysz.semantic.tier.docs.DocUtils;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Query;
import io.odysz.transact.x.TransException;
import io.oz.jserv.docs.meta.DeviceTableMeta;
import io.oz.jserv.docs.x.DocsException;

@WebServlet(description = "Synode Tier: docs-sync", urlPatterns = { "/docs.syn" })
public class Syntier extends ServPort<DocsReq> {
	/** {domain: {jserv: exession-persist}} */
	HashMap<String, Synoder> domains;

	DBSyntableBuilder doctrb;
	public DBSyntableBuilder doctrb() {
		return doctrb;
	}

	final String synode;
	final String myconn;

	public Syntier() throws SemanticException, SQLException, SAXException, IOException {
		this("test", "test");
	}

	public Syntier(String synoderId, String loconn)
			throws SemanticException, SQLException, SAXException, IOException {
		super(Port.dbsyncer);
		synode = synoderId;
		myconn = loconn;
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
		Utils.logi("== %s", jmsg.toBlock());
		
		AnsonBody jreq = jmsg.body(0); // SyncReq | DocsReq
		String a = jreq.a();
		DocsResp rsp = null;
		DocUser usr;
		try {
			usr = (DocUser) JSingleton.getSessionVerifier().verify(jmsg.header());

			DocsReq docreq = jmsg.body(0) instanceof DocsReq ? (DocsReq)jmsg.body(0) : null;
			// ExpDocTableMeta docm = (ExpDocTableMeta) doctrb().getSyntityMeta(docreq.docTabl);

			if (A.upload.equals(a))
				rsp = createPhoto(docreq, usr);
			else if (A.del.equals(a))
				rsp = delDoc(docreq, usr);
			else if (A.selectSyncs.equals(a))
				rsp = querySyncs((DocsReq)jmsg.body(0), usr);

			//
	//		else if (DocsReq.A.blockStart.equals(a))
	//			rsp = startBlocks(jmsg.body(0), usr);
	//		else if (DocsReq.A.blockUp.equals(a))
	//			rsp = uploadBlock(jmsg.body(0), usr);
	//		else if (DocsReq.A.blockEnd.equals(a))
	//			rsp = endBlock(jmsg.body(0), usr);
	//		else if (DocsReq.A.blockAbort.equals(a))
	//			rsp = abortBlock(jmsg.body(0), usr);
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

			if (rsp != null) {
				write(resp, ok(rsp));
			}
		} catch (SsException | SAXException e) {
			e.printStackTrace();
		} catch (TransException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public Synoder start(String org, String domain, String conn, SynodeMode mod)
			throws SQLException, TransException, SAXException, IOException {
		if (domains == null)
			domains = new HashMap<String, Synoder>();
		if (!domains.containsKey(domain))
			domains.put(domain, new Synoder(org, domain, synode, conn, mod));

		SemanticsMap ss = DATranscxt.initConfigs(conn, DATranscxt.loadSemantics(conn),
			(c) -> new DBSyntableBuilder.SynmanticsMap(synode, c));
		
		Synoder synoder = domains
				.get(domain)
				.born(ss.get(smtype.synChange), 0, 0);
		
		doctrb =  new DBSyntableBuilder(
				domain, // FIXME this is not correct. 
						// FIXME See {@link DBSyntableBuilder}'s issue ee153bcb30c3f3b868413beace8cc1f3cb5c3f7c. 
				myconn, synode, mod)
				.loadNyquvect(conn);
		return synoder;
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
	DocsResp querySyncs(DocsReq docsReq, IUser usr)
			throws SemanticException, TransException, SQLException {

		/*
		if (syncReq.syncQueries() == null)
			throw new SemanticException("Null Query - invalide request.");

		ArrayList<String> paths = new ArrayList<String>(syncReq.syncQueries().size());
		for (String s : syncReq.syncQueries()) {
			paths.add(s);
		}

		String conn = Connects.uri2conn(syncReq.uri());
		PhotoMeta meta = new PhotoMeta(conn);

		Object[] kpaths = syncReq.syncing().paths() == null ? new Object[0]
				: syncReq.syncing().paths().keySet().toArray();

		AnResultset rs = ((AnResultset) st
				.select(syncReq.docTabl, "t")
				.cols((Object[])SyncDoc.synPageCols(meta))
				// .whereEq(meta.domain, req.org == null ? usr.orgId() : req.org)
				.whereEq(meta.synoder, usr.deviceId())
				.whereIn(meta.fullpath, Arrays.asList(kpaths).toArray(new String[kpaths.length]))
				// TODO add file type for performance
				// FIXME issue: what if paths length > limit ?
				.limit(syncReq.limit())
				.rs(st.instancontxt(conn, usr))
				.rs(0))
				.beforeFirst();

		DocsResp album = new DocsResp().syncing(syncReq).pathsPage(rs, meta);

		return album;
		*/
		return null;
	}

	DocsResp createPhoto(DocsReq docreq, IUser usr)
			throws TransException, SQLException, IOException, SAXException {
		String conn = Connects.uri2conn(docreq.uri());
		ExpDocTableMeta docm = (ExpDocTableMeta) doctrb().getSyntityMeta(docreq.docTabl);

		checkDuplication(docreq, (DocUser) usr, docm);

		String pid = createFile(conn, docreq, usr, docm);
	
		SyncDoc doc = onPhotoCreated(pid, conn, docm, usr);
		return new DocsResp().doc(doc);
	}

	private SyncDoc onPhotoCreated(String pid, String conn, ExpDocTableMeta docm, IUser usr) {
		// TODO Auto-generated method stub
		return null;
	}

	void checkDuplication(DocsReq docreq, DocUser usr, ExpDocTableMeta docm)
			throws SemanticException, TransException, SQLException, SAXException, IOException {
		String conn = Connects.uri2conn(docreq.uri());
		checkDuplicate(conn, usr.deviceId(), docreq.clientpath(), usr, docm);
	}

	private void checkDuplicate(String conn, String device, String clientpath,
			IUser usr, ExpDocTableMeta meta)
			throws SemanticException, TransException, SQLException, SAXException, IOException {
		DBSyntableBuilder st = (DBSyntableBuilder) doctrb();
		AnResultset rs = ((AnResultset) st
				.select(meta.tbl, "p")
				.col(count(meta.pk), "cnt")
				.whereEq(meta.synoder, device)
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
		// ExpDocTableMeta meta = null; // new ExpDocTableMeta(conn);
		ExpDocTableMeta docm = (ExpDocTableMeta) doctrb().getSyntityMeta(docsReq.docTabl);

		DBSyntableBuilder st = (DBSyntableBuilder) doctrb();
		SemanticObject res = (SemanticObject) st
				.delete(docm.tbl, usr)
				.whereEq("device", docsReq.device().id)
				.whereEq("clientpath", docsReq.clientpath())
				// .post(Docsyncer.onDel(req.clientpath, req.device()))
				.d(st.instancontxt(conn, usr));

		return (DocsResp) new DocsResp().data(res.props());
	}

	/**
	 * <p>Create photo - call this after duplication is checked.</p>
	 * <p>Photo is created as in the folder of user/month/.</p>
	 *
	 * @param conn
	 * @param exblock
	 * @param usr
	 * @return pid
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException
	 * @throws SAXException 
	 */
	public String createFile(String conn, DocsReq docreq, IUser usr, ExpDocTableMeta meta)
			throws TransException, SQLException, IOException, SAXException {

		// ExpDocTableMeta meta = null; // new ExpDocTableMeta();

		// exblock.share(usr.uid(), Share.priv);

		String pid = DocUtils.createFileB64(doctrb(), conn, docreq, usr, meta);

		return pid;
	}

	/**
	 * Read a document (id, uri).
	 *
	 * @param req
	 * @param usr
	 * @param meta 
	 * @return loaded media record
	 * @throws SQLException
	 * @throws TransException
	 * @throws SemanticException
	 * @throws IOException
	 * @throws SAXException 
	 */
	protected DocsResp doc(DocsReq req, IUser usr, ExpDocTableMeta meta)
			throws SemanticException, TransException, SQLException, IOException, SAXException {

		String conn = Connects.uri2conn(req.uri());
		// ExpDocTableMeta meta = new ExpDocTableMeta(conn);
		// Photo_OrgMeta mp_o = new Photo_OrgMeta(conn);

		DATranscxt st = doctrb();
		Query q = st
				.select(meta.tbl, "p")
				.j("a_users", "u", "u.userId = p.shareby")
				.l("a_orgs" , "po", "po.pid = p.pid");

		AnResultset rs = (AnResultset) SyncDoc.cols(q, meta)
				.col(meta.shareby, "shareby").col(count("po.oid"), "orgs")
				.whereEq("p." + meta.pk, req.pageInf.mergeArgs().getArg("pid"))
				.rs(st.instancontxt(conn, usr)).rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find file for id: '%s' (with permission of %s)",
					!isblank(req.docId)
					? req.docId
					: !isblank(req.pageInf)
					? !isblank(req.pageInf.mapCondts)
					  ? req.pageInf.mapCondts.get("pid")
					  : isNull(req.pageInf.arrCondts) ? null : req.pageInf.arrCondts.get(0)[1]
					: null,
					usr.uid());

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

		return new DocsResp().doc(new SyncDoc().folder(rs.nxt(), mph));
	}
	
	static String chainId(IUser usr, String clientpathRaw) {
		return usr.sessionId() + " " + clientpathRaw;
	}
}
