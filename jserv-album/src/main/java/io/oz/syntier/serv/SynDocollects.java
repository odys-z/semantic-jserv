package io.oz.syntier.serv;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.transact.sql.parts.condition.Funcall.count;
import static io.odysz.transact.sql.parts.condition.Funcall.ifElse;
import static io.odysz.transact.sql.parts.condition.Funcall.now;
import static io.odysz.transact.sql.parts.condition.Funcall.sum;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.jclient.syn.ExpDocRobot;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetHelper;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantic.syn.DBSynTransBuilder;
import io.odysz.semantic.tier.docs.Device;
import io.odysz.semantic.tier.docs.DocUtils;
import io.odysz.semantic.tier.docs.DocsException;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.FileStream;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Delete;
import io.odysz.transact.sql.PageInf;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.parts.Logic;
import io.odysz.transact.sql.parts.Sql;
import io.odysz.transact.sql.parts.condition.ExprPart;
import io.odysz.transact.x.TransException;
import io.oz.album.AlbumFlags;
import io.oz.album.AlbumSingleton;
import io.oz.album.peer.AlbumPort;
import io.oz.album.peer.AlbumReq;
import io.oz.album.peer.AlbumResp;
import io.oz.album.peer.PhotoMeta;
import io.oz.album.peer.Profiles;
import io.oz.album.peer.SynDocollPort;
import io.oz.album.peer.AlbumReq.A;
import io.oz.jserv.docs.meta.DeviceTableMeta;
import io.oz.jserv.docs.meta.DocOrgMeta;
import io.oz.jserv.docs.syn.DocUser;

/**
 * <h5>The album tier 0.6.50 (MVP)</h5>
 *
 * Although this tie is using the pattern of <i>less</i>, it's also verifying user when uploading
 * - for subfolder name of user.
 *
 * <h5>Design note for version 0.1</h5>
 * <p>
 * A photo always have a default collection Id.
 * </p>
 * The clients collect image files etc., create photo records and upload
 * ({@link AlbumReq.A#insertPhoto A.insertPhoto}) - without collection Id; <br>
 * the browsing clients are supported with a default collection: home/usr/month.
 *
 * @author ody
 *
 */
@WebServlet(description = "Syn-doc collection tier, jserv of ExpDoctier", urlPatterns = { "/docoll.syn" })
public class SynDocollects extends ServPort<AlbumReq> {
	private static final long serialVersionUID = 1L;

	/** tringger exif parsing when new photo inserted */
	public static int POST_ParseExif = 1;

	/** db photo table */
	static final String tablAlbums = "h_albums";
	/** db collection table */
	static final String tablCollects = "h_collects";

	static final String tablAlbumCollect = "h_album_coll";

	static final String tablCollectPhoto = "h_coll_phot";

	/** uri db field */
	static final String uri = "uri";
	/** file state db field */
	static final String state = "state";

	// private HashMap<String, BlockChain> blockChains;

	public static DATranscxt st;

	final IUser robot;

	// PUserMeta userMeta;
	JUserMeta userMeta;

	final String sysconn;
	final String synode;
	final PhotoMeta phm;

	static {
		try {
			st = new DATranscxt(null);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public SynDocollects(String synode, String sysconn, String synconn) throws TransException {
		super(SynDocollPort.docoll);
		this.synode = synode;
		this.phm    = new PhotoMeta(synconn);
		this.sysconn= sysconn;
		this.robot = new ExpDocRobot("Rob.Album@" + synode);
		
		missingFile = "";
	}

	String missingFile = "";

	private DBSynTransBuilder synt;

	public SynDocollects missingFile(String onlyPng) {
		missingFile = onlyPng;
		return this;
	}
	
	@Override
	protected void onGet(AnsonMsg<AlbumReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {

		if (AlbumFlags.album)
			Utils.logi("[AlbumFlags.album/album.less GET]");

		DocsReq jreq = msg.body(0);
		try {
			String a = jreq.a();
			if (A.download.equals(a))
				download(resp, msg.body(0), robot);
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			if (AlbumFlags.album)
				e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (IOException e) {
			write(resp, err(MsgCode.exIo, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void onPost(AnsonMsg<AlbumReq> jmsg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {

		if (AlbumFlags.album)
			Utils.logi("[AlbumFlags.album/album.less POST]");

		try {
			DocsReq jreq = jmsg.body(0);
			String a = jreq.a();
			DocsResp rsp = null;

			if (A.collect.equals(a) || A.rec.equals(a)  || A.download.equals(a)) {
				// Session less
				IUser usr = robot;
				if (A.collect.equals(a))
					rsp = collect(jmsg.body(0), usr);
				else if (A.rec.equals(a))
					rsp = doc(jmsg.body(0), usr);
				else if (A.download.equals(a))
					download(resp, jmsg.body(0), usr);
			} else {
				// session required
				DocUser usr = (DocUser) JSingleton.getSessionVerifier().verify(jmsg.header());
				
				if (userMeta == null)
					userMeta = (JUserMeta) usr.meta();

				Profiles prf = verifyProfiles(jmsg.body(0), usr, a);

				if (A.folder.equals(a))
					rsp = folder(jmsg.body(0), usr);

//				else if (A.insertPhoto.equals(a))
//					rsp = createPhoto(jmsg.body(0), usr, prf);
//				else if (A.del.equals(a))
//					rsp = delPhoto(jmsg.body(0), usr, prf);
//				else if (A.selectSyncs.equals(a))
//					rsp = querySyncs(jmsg.body(0), usr, prf);
				else if (A.getPrefs.equals(a))
					rsp = profile(jmsg.body(0), usr, prf);
				else if (A.album.equals(a)) // FXIME what's the equivalent of Portfolio?
					rsp = album(jmsg.body(0), usr, prf);

				else if (A.stree.equals(a))
					rsp = galleryTree(jmsg.body(0), usr, prf);

				//
//				else if (DocsReq.A.blockStart.equals(a))
//					rsp = startBlocks(jmsg.body(0), usr, prf);
//				else if (DocsReq.A.blockUp.equals(a))
//					rsp = uploadBlock(jmsg.body(0), usr);
//				else if (DocsReq.A.blockEnd.equals(a))
//					rsp = endBlock(jmsg.body(0), usr);
//				else if (DocsReq.A.blockAbort.equals(a))
//					rsp = abortBlock(jmsg.body(0), usr);
				else if (DocsReq.A.devices.equals(a))
					rsp = devices(jmsg.body(0), usr);
				else if (DocsReq.A.checkDev.equals(a))
					rsp = chkDevname(jmsg.body(0), usr);
				else if (DocsReq.A.registDev.equals(a))
					rsp = registDevice(jmsg.body(0), usr);
				else if (AlbumReq.A.updateFolderel.equals(a))
					rsp = updateFolderel(jmsg.body(0), usr);

				else
					throw new SemanticException("Request.a, %s, can not be handled.", jreq.a());
			}

			if (rsp != null) { // no rsp for a == download
				rsp.syncing(jreq.syncingPage());
				write(resp, ok(rsp).port(AlbumPort.album));
			}
		} catch (DocsException e) {
			write(resp, err(MsgCode.ext, e.ex().toBlock()));
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			if (AlbumFlags.album)
				e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
//		} catch (InterruptedException e) {
//			if (Anson.verbose)
//				e.printStackTrace();
		} catch (Throwable e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exGeneral, "%s\n%s", e.getClass().getName(), e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	/**
	 * Update photo-org relationships for all pid in the folder. No depth recursive for photos (docs)
	 * sharing are managed by users only in the view of the original folder's structure, the saving
	 * file system tree structure.
	 * 
	 * @param req
	 * @param usr
	 * @return resp
	 * @throws TransException
	 * @throws SQLException
	 */
	DocsResp updateFolderel(AlbumReq req, DocUser usr)
			throws TransException, SQLException {
		String conn = Connects.uri2conn(req.uri());
		PhotoMeta phm = new PhotoMeta(conn);
		DocOrgMeta pom = new DocOrgMeta(conn);

		Delete d = st
				.delete(pom.tbl, usr)
				.whereIn(phm.pk, st.select(phm.tbl).col(phm.pk).whereEq(phm.folder, req.doc.folder()))
				.whereIn(pom.pk, req.getChecks("oid"));
		if (!req.clearels) {
			d.post(st.insert(pom.tbl)
				.cols(phm.pk, pom.pk)
				.select(st
					.select(phm.tbl, "ph")
					.distinct()
					.col("ph." + phm.pk).col("po." + pom.pk)
					.j(pom.tbl, "po", Sql.condt(Logic.op.in, "po." + pom.pk, new ExprPart(req.getChecks("oid")))
								 .and(Sql.condt(Logic.op.eq, "ph." + phm.folder, ExprPart.constr(req.doc.folder())))
								 .and(Sql.condt(Logic.op.eq, phm.shareby, usr.uid())))
					.whereEq(phm.folder, req.doc.folder())));
		}
		
		d.post(st.update(phm.tbl).nv(phm.shareflag, req.photo.shareflag()));

		SemanticObject res = (SemanticObject)d
				.d(st.instancontxt(conn, usr));

		return (DocsResp) new DocsResp().data(res.props());
	}

	/**
	 * Generate user's profile - used at server side,
	 * yet {@link IUser#profile()} is used for loading profile for client side.
	 *
	 * Connection used to verifiy the user id is overriden by server side sys-conn.
	 * 
	 * @param body
	 * @param usr
	 * @param a
	 * @return profiles
	 * @throws SemanticException
	 * @throws SQLException
	 * @throws TransException
	 */
	Profiles verifyProfiles(DocsReq body, IUser usr, String a)
			throws SemanticException, SQLException, TransException {
		// String conn = Connects.uri2conn(body.uri());
		JUserMeta m = (JUserMeta) usr.meta(sysconn);
		DocOrgMeta orgMeta = new DocOrgMeta(sysconn);

		AnResultset rs = ((AnResultset) st
				.select(m.tbl, "u")
				.je("u", orgMeta.tbl, "o", m.org, orgMeta.pk)
				.col("u." + m.org).col(m.pk)
				.col(orgMeta.album0, "album") 
				.col(orgMeta.webroot)
				.whereEq(m.pk, usr.uid())
				.rs(st.instancontxt(sysconn, usr))
				.rs(0)).nxt();

		if (rs == null || isblank(rs.getString(m.org)))
			throw new SemanticException("Verifying user's profiles needs target user belongs to an organization / family.");
		return new Profiles(rs, m);
	}

	AlbumResp galleryTree(AlbumReq jreq, IUser usr, Profiles prf)
			throws SQLException, TransException {

		String conn = Connects.uri2conn(jreq.uri());
		// force org-id as first arg

		if (eq(jreq.sk, "tree-rel-folder-org")) {
			// force user-id as first arg
			PageInf page = isNull(jreq.pageInf)
					? new PageInf(0, -1, usr.uid())
					: eq(jreq.pageInf.arrCondts.get(0), usr.uid())
					? jreq.pageInf
					: jreq.pageInf.insertCondt(usr.uid());

			List<?> lst = DatasetHelper.loadStree(conn, jreq.sk, page);
			return new AlbumResp().albumForest(lst);
		}
		else {//  "tree-rel-photo-org") || "tree-album-sharing" || "tree-docs-folder"
			PageInf page = isNull(jreq.pageInf)
					? new PageInf(0, -1, usr.orgId())
					: eq(jreq.pageInf.arrCondts.get(0), usr.orgId())
					? jreq.pageInf
					: jreq.pageInf.insertCondt(usr.orgId());

			List<?> lst = DatasetHelper.loadStree(conn, jreq.sk, page);
			return new AlbumResp().albumForest(lst);
		}
	}

	AlbumResp profile(AlbumReq body, IUser usr, Profiles prf)
			throws SemanticException, TransException, SQLException {

		String conn = Connects.uri2conn(body.uri());
		DocOrgMeta orgMeta = new DocOrgMeta(conn);

		AnResultset rs = (AnResultset) st
				.select(orgMeta.tbl)
				.whereEq(orgMeta.pk, usr.orgId())
				.rs(st.instancontxt(conn, usr))
				.rs(0);

		rs.beforeFirst().next();
		String home = rs.getString(orgMeta.homepage);
		String webroot = rs.getString(orgMeta.webroot);

		return new AlbumResp().profiles(new Profiles(home).webroot(webroot));
	}

//	DocsResp startBlocks(DocsReq body, IUser usr, Profiles prf)
//			throws IOException, TransException, SQLException {
//
//		String conn = Connects.uri2conn(body.uri());
//		checkDuplicate(conn, ((DocUser)usr).deviceId(), body.doc.clientpath, usr, new PhotoMeta(conn));
//
//		if (blockChains == null)
//			blockChains = new HashMap<String, BlockChain>(2);
//
//		String tempDir = ((DocUser)usr).touchTempDir(conn, phm.tbl);
//
//		BlockChain chain = new BlockChain("h_photos", tempDir, body.device().id,
//				body.doc.clientpath, body.doc.createDate, body.doc.folder());
//
//		// FIXME security breach?
//		String id = chainId(usr, chain.doc.clientpath);
//
//		if (blockChains.containsKey(id))
//			throw new SemanticException("Why started again?");
//
//		blockChains.put(id, chain);
//		return new DocsResp()
//				.blockSeq(-1)
//				.doc((ExpSyncDoc) new ExpSyncDoc()
//					.clientname(chain.doc.clientname())
//					.cdate(body.doc.createDate)
//					.fullpath(chain.doc.clientpath));
//	}

	void checkDuplication(AlbumReq body, DocUser usr)
			throws SemanticException, TransException, SQLException {
		String conn = Connects.uri2conn(body.uri());
		checkDuplicate(conn,
				usr.deviceId(), body.photo.fullpath(), usr, new PhotoMeta(conn));
	}

	private void checkDuplicate(String conn, String device, String clientpath, IUser usr, PhotoMeta meta)
			throws SemanticException, TransException, SQLException {
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

//	DocsResp uploadBlock(DocsReq body, IUser usr) throws IOException, TransException {
//		String id = chainId(usr, body.doc.clientpath);
//		if (!blockChains.containsKey(id))
//			throw new SemanticException("Uploading blocks must accessed after starting chain is confirmed.");
//
//		BlockChain chain = blockChains.get(id);
//		chain.appendBlock(body);
//
//		return new DocsResp()
//				.blockSeq(body.blockSeq())
//				.doc((ExpSyncDoc) new ExpSyncDoc()
//					.clientname(chain.doc.clientname())
//					.cdate(body.doc.createDate)
//					.fullpath(body.doc.clientpath));
//	}

//	/**
//	 * Finishing doc (block chain) uploading.
//	 * 
//	 * <p>This method will trigger ext-file handling by which the uri is set to file path starting at
//	 * volume environment variable.</p>
//	 * 
//	 * @param body
//	 * @param usr
//	 * @return response
//	 * @throws SQLException
//	 * @throws IOException
//	 * @throws InterruptedException
//	 * @throws TransException
//	 */
//	DocsResp endBlock(DocsReq body, IUser usr)
//			throws SQLException, IOException, InterruptedException, TransException {
//		String id = chainId(usr, body.doc.clientpath);
//		BlockChain chain;
//		if (blockChains.containsKey(id)) {
//			blockChains.get(id).closeChain();
//			chain = blockChains.remove(id);
//		} else
//			throw new SemanticException("Ending block chain which is not existing.");
//
//		String conn = Connects.uri2conn(body.uri());
//		PhotoMeta meta = new PhotoMeta(conn);
//		PhotoRec photo = new PhotoRec();
//
//		photo.createDate = chain.doc.createDate;
//		photo.fullpath(chain.doc.clientpath);
//		photo.pname = chain.doc.clientname();
//		photo.uri64 = null; // accepting new value
//		String pid = createFile(conn, photo, usr);
//
//		// move file
//		String targetPath = DocUtils.resolvExtroot(st, conn, pid, usr, meta);
//		if (AlbumFlags.album)
//			Utils.logi("   [AlbumFlags.album: end block]\n   %s\n-> %s", chain.outputPath, targetPath);
//		Files.move(Paths.get(chain.outputPath), Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);
//
//		onPhotoCreated(pid, conn, meta, usr);
//
//		return new DocsResp()
//				.blockSeq(body.blockSeq())
//				/*
//				.doc((ExpSyncDoc) new ExpSyncDoc()
//					.recId(pid)
//					.device(body.device())
//					.folder(photo.folder())
//					.clientname(chain.doc.clientname())
//					.cdate(body.doc.createDate)
//					.fullpath(chain.doc.clientpath));
//				*/
//				.doc(photo.uri64(null));
//	}

//	DocsResp abortBlock(DocsReq body, IUser usr)
//			throws SQLException, IOException, InterruptedException, TransException {
//		String id = chainId(usr, body.doc.clientpath);
//		DocsResp ack = new DocsResp();
//		if (blockChains.containsKey(id)) {
//			blockChains.get(id).abortChain();
//			blockChains.remove(id);
//			ack.blockSeqReply = body.blockSeq();
//		} else
//			ack.blockSeqReply = -1;
//
//		return ack;
//	}

	/**
	 * Query devices.
	 * 
	 * @see DocsReq.A#devices
	 * 
	 * @param body
	 * @param usr
	 * @return respond
	 * @throws SemanticException
	 * @throws TransException
	 * @throws SQLException
	 */
	DocsResp devices(DocsReq body, DocUser usr)
			throws SemanticException, TransException, SQLException {
		String conn = Connects.uri2conn(body.uri());
		DeviceTableMeta devMeta = new DeviceTableMeta(conn);

		AnResultset rs = (AnResultset)st
				.select(devMeta.tbl)
				// .whereEq(devMeta.domain,   usr.orgId())
				.whereEq(devMeta.owner,   usr.uid())
				.rs(st.instancontxt(conn, usr))
				.rs(0)
				;

		return (DocsResp) new DocsResp().rs(rs)
				.data(devMeta.owner, usr.uid())
				.data("owner-name",  usr.userName())
				// .data(devMeta.domain, usr.orgId())
				;
	}

	/**
	 * @see DocsReq.A#checkDev
	 * 
	 * @param body
	 * @param usr
	 * @return
	 * @throws SemanticException
	 * @throws TransException
	 * @throws SQLException
	 */
	DocsResp chkDevname(DocsReq body, DocUser usr)
			throws SemanticException, TransException, SQLException {
		String conn = Connects.uri2conn(body.uri());
		DeviceTableMeta devMeta = new DeviceTableMeta(conn);

		AnResultset rs = ((AnResultset) st
			.select(devMeta.tbl, "d")
			.cols("d.*", "u." + userMeta.uname)
			.j(userMeta.tbl, "u", "u.%s = d.%s", userMeta.pk, devMeta.owner)
			.cols(devMeta.devname, devMeta.synoder, devMeta.cdate, devMeta.owner)
			.whereEq(devMeta.org,   usr.orgId())
			.whereEq(devMeta.pk, usr.deviceId())
			.whereEq(devMeta.owner,   usr.uid())
			.rs(st.instancontxt(conn, usr))
			.rs(0))
			.nxt();
		
		if (rs != null) {
			throw new SemanticException("{\"exists\": true, \"owner\": \"%s\", \"synode0\": \"%s\", \"create_on\": \"%s\"}",
				rs.getString(userMeta.uname),
				rs.getString(devMeta.synoder),
				rs.getString(devMeta.cdate)
			);
		}
		else 
			return (DocsResp) new DocsResp().device(new Device(
					null, AlbumSingleton.synode(),
					usr.deviceId()));
	}
	
	DocsResp registDevice(DocsReq body, DocUser usr)
			throws SemanticException, TransException, SQLException {
		String conn = Connects.uri2conn(body.uri());
		DeviceTableMeta devMeta = new DeviceTableMeta(conn);

		if (isblank(body.device().id)) {
			SemanticObject result = (SemanticObject) st
				.insert(devMeta.tbl, usr)
				.nv(devMeta.synoder, AlbumSingleton.synode())
				.nv(devMeta.devname, body.device().devname)
				.nv(devMeta.owner, usr.uid())
				.nv(devMeta.cdate, now())
				.nv(devMeta.org, usr.orgId())
				.ins(st.instancontxt(Connects.uri2conn(body.uri()), usr));

			String resulved = result.resulve(devMeta.tbl, devMeta.pk, -1);
			return new DocsResp().device(new Device(
				resulved, AlbumSingleton.synode(), body.device().devname));
		}
		else {
			if (isblank(body.device().id))
				throw new SemanticException("Error for pdating device name without a device id.");

			st  .update(devMeta.tbl, usr)
				.nv(devMeta.cdate, now())
				.whereEq(devMeta.org, usr.orgId())
				.whereEq(devMeta.pk, body.device().id)
				.u(st.instancontxt(Connects.uri2conn(body.uri()), usr));

			return new DocsResp().device(new Device(
				body.device().id, AlbumSingleton.synode(), body.device().devname));
		}
	}

//	/**
//	 * Query client paths
//	 * @param req
//	 * @param usr
//	 * @param prf
//	 * @param meta
//	 * @return album where clientpath in req's fullpath and device also matched
//	 * @throws SemanticException
//	 * @throws TransException
//	 * @throws SQLException
//	 */
//	DocsResp querySyncs(DocsReq req, IUser usr, Profiles prf)
//			throws SemanticException, TransException, SQLException {
//
//		if (req.syncQueries() == null)
//			throw new SemanticException("Null Query - invalide request.");
//
//		ArrayList<String> paths = new ArrayList<String>(req.syncQueries().size());
//		for (String s : req.syncQueries()) {
//			paths.add(s);
//		}
//
//		String conn = Connects.uri2conn(req.uri()); // TODO check uri and conn are mapping correctly
//		PhotoMeta meta = new PhotoMeta(conn);
//
//		Object[] kpaths = req.syncingPage().paths() == null ? new Object[0]
//				: req.syncingPage().paths().keySet().toArray();
//
//		musteqs(req.docTabl, phm.tbl,
//				f("This synode tier can only handle syntity %s, but requsted for %s.",
//				phm.tbl, req.docTabl));
//		
//		AnResultset rs = ((AnResultset) st
//				.select(req.docTabl, "t")
//				.cols((Object[])ExpSyncDoc.synPageCols(meta))
//				// .whereEq(meta.domain, req.org == null ? usr.orgId() : req.org)
//				.whereEq(meta.device, usr.deviceId())
//				.whereIn(meta.fullpath, Arrays.asList(kpaths).toArray(new String[kpaths.length]))
//				// TODO add file type for performance
//				// FIXME issue: what if paths length > limit ?
//				.limit(req.limit())
//				.rs(st.instancontxt(conn, usr))
//				.rs(0))
//				.beforeFirst();
//
//		DocsResp album = new DocsResp().syncingPage(req).pathsPage(rs, meta);
//
//		return album;
//	}

	void download(HttpServletResponse resp, DocsReq req, IUser usr)
			throws IOException, SemanticException, TransException, SQLException {

		String conn = Connects.uri2conn(req.uri());
		PhotoMeta meta = new PhotoMeta(conn);

		AnResultset rs = (AnResultset) st
				.select(meta.tbl, "p")
				.j("a_users", "u", "u.userId = p.shareby")
				.col(meta.pk)
				.col(meta.resname).col(meta.createDate)
				.col(meta.folder).col(meta.fullpath)
				.col(meta.uri)
				.col("userName", "shareby")
				.col(meta.shareDate).col(meta.tags)
				.col(meta.geox).col(meta.geoy)
				.col(meta.mime)
				.whereEq(meta.pk, req.doc.recId)
				.rs(st.instancontxt(conn, usr)).rs(0);

		if (!rs.next()) {
			resp.setContentType("image/png");
			FileStream.sendFile(resp.getOutputStream(), missingFile);
		}
		else {
			String mime = rs.getString("mime");
			resp.setContentType(mime);
			
			try ( OutputStream os = resp.getOutputStream() ) {
				FileStream.sendFile(os, DocUtils.resolvExtroot(st, conn, req.doc.recId, usr, meta));
				os.close();
			} catch (IOException e) {
				// If the user dosen't play a video, Chrome will close the connection before finishing downloading.
				// This is harmless: https://stackoverflow.com/a/70020526/7362888
				// Utils.warn(e.getMessage());
			}
		}
	}

//	AlbumResp createPhoto(AlbumReq req, IUser usr, Profiles prf)
//			throws TransException, SQLException, IOException {
//		String conn = Connects.uri2conn(req.uri());
//		checkDuplication(req, (DocUser) usr);
//
//		String pid = createFile(conn, req.photo, usr);
//		
//		onPhotoCreated(pid, conn, new PhotoMeta(conn), usr);
//		return new AlbumResp().photo(req.photo, pid);
//	}

//	static DocsResp delPhoto(AlbumReq req, IUser usr, Profiles prf)
//			throws TransException, SQLException {
//		String conn = Connects.uri2conn(req.uri());
//		PhotoMeta meta = new PhotoMeta(conn);
//
//		SemanticObject res = (SemanticObject) st
//				.delete(meta.tbl, usr)
//				.whereEq("device", req.device().id)
//				.whereEq("clientpath", req.doc.clientpath)
//				// .post(Docsyncer.onDel(req.clientpath, req.device()))
//				.d(st.instancontxt(conn, usr));
//
//		return (DocsResp) new DocsResp().data(res.props());
//	}

//	/**
//	 * <p>Create photo - call this after duplication is checked.</p>
//	 * <p>Photo is created as in the folder of user/month/.</p>
//	 *
//	 * @param conn
//	 * @param photo
//	 * @param usr
//	 * @return pid
//	 * @throws TransException
//	 * @throws SQLException
//	 * @throws IOException
//	 */
//	public static String createFile(String conn, PhotoRec photo, IUser usr)
//			throws TransException, SQLException, IOException {
//
//		PhotoMeta meta = new PhotoMeta(conn);
//
//		if (isblank(photo.shareby))
//			photo.share(usr.uid(), photo.shareflag, new Date());
//
//		String pid = DocUtils.createFileBy64((DBSynTransBuilder)st, conn, photo, usr, meta);
//
//		return pid;
//	}

//	/**
//	 * This method parse exif, update geox/y, date etc. - should only be used when file created.
//	 *
//	 * TODO generate preview
//	 *
//	 * @param pid
//	 * @param conn
//	 * @param usr
//	 * @return 
//	 */
//	static protected void onPhotoCreated(String pid, String conn, PhotoMeta m, IUser usr) {
//		new Thread(() -> {
//		try {
//			AnResultset rs = (AnResultset) st
//				.select(m.tbl, "p")
//				.col(m.folder).col(m.fullpath)
//				.col(m.uri)
//				.col(m.resname)
//				.col(m.createDate)
//				.col(m.mime)
//				.whereEq(m.pk, pid)
//				.rs(st.instancontxt(conn, usr))
//				.rs(0);
//
//			if (rs.next() && isVedioAudio(rs.getString(m.mime))) {
//				ISemantext stx = st.instancontxt(conn, usr);
//				String pth = EnvPath.decodeUri(stx, rs.getString("uri"));
//				PhotoRec p = new PhotoRec();
//				// Exif.parseExif(p, pth);
//				Exiftool.parseExif(p, pth);
//
//				Update u = st
//					.update(m.tbl, usr)
//					.nv(m.css, p.css)
//					.nv(m.size, String.valueOf(p.size))
//					.whereEq(m.pk, pid);
//
//				if (isblank(rs.getDate(m.createDate)))
//					u.nv(m.createDate, now());
//
//
//					if (!isblank(p.geox) || !isblank(p.geoy))
//						u.nv(m.geox, p.geox)
//						 .nv(m.geoy, p.geoy);
//					if (!isblank(p.exif))
//						u.nv(m.exif, p.exif);
//					else // figure out mime with file extension
//						;
//
//					if (!isblank(p.mime))
//						u.nv(m.mime, p.mime);
//				u.u(stx);
//			}
//		} catch (TransException | SQLException | IOException e) {
//			e.printStackTrace();
//		}})
//		.start();
//	}

//	static boolean isVedioAudio(String mime) {
//		return isblank(mime) || prefixOneOf(mime, "audio/", "image/");
//	}

	/**
	 * Read a media file record (id, uri)
	 *
	 * @param req
	 * @param usr
	 * @return loaded media record
	 * @throws SQLException
	 * @throws TransException
	 * @throws SemanticException
	 * @throws IOException
	 */
	protected static AlbumResp doc(AlbumReq req, IUser usr)
			throws SemanticException, TransException, SQLException, IOException {

		String conn = Connects.uri2conn(req.synuri());
		PhotoMeta meta = new PhotoMeta(conn);
		DocOrgMeta mp_o = new DocOrgMeta(conn);

		Query q = st
				.select(meta.tbl, "p")
				.j("a_users", "u", "u.userId = p.shareby")
				.l(mp_o.tbl , "po", "po.pid = p.pid");

		AnResultset rs = (AnResultset) q // PhotoRec.cols(q, meta)
				.cols_byAlias("p", meta.pk,
					meta.resname, meta.createDate, meta.folder,
					meta.fullpath, meta.device, meta.uri,
					meta.shareDate, meta.mime, meta.shareby)
				.col(count("po.oid"), "orgs")
				.whereEq("p." + meta.pk, req.pageInf.mergeArgs().getArg("pid"))
				.rs(st.instancontxt(conn, usr)).rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find file for id: '%s' (with permission of %s)",
					!isblank(req.doc.recId)
					? req.doc.recId
					: !isblank(req.pageInf)
					? !isblank(req.pageInf.mapCondts)
					  ? req.pageInf.mapCondts.get("pid")
					  : isNull(req.pageInf.arrCondts) ? null : req.pageInf.arrCondts.get(0)[1]
					: null,
					usr.uid());

		return new AlbumResp().photo(rs, meta);
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
	 */
	protected static AlbumResp folder(AlbumReq req, IUser usr)
			throws SemanticException, TransException, SQLException, IOException {

		String conn = Connects.uri2conn(req.uri());
		PhotoMeta mph = new PhotoMeta(conn);
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
		AnResultset rs = (AnResultset) st
				.select(mph.tbl, "p")
				.j(musr.tbl, "u", String.format("u.%s = p.%s", musr.pk, mph.shareby))
				.col(mph.pk).col(mph.css)
				.col(sum(ifElse("substr(mime, 0, 6) = 'image'", 1, 0)), "img")
				.col(sum(ifElse("substr(mime, 0, 6) = 'video'", 1, 0)), "mov")
				.col(sum(ifElse("substr(mime, 0, 6) = 'audio'", 1, 0)), "wav")
				.col(sum(ifElse("geox != 0", 1, 0)), "geo")
				.col(mph.mime).col(mph.createDate)
				.col(mph.folder, mph.resname)
				.col(musr.uname, mph.shareby)
				.whereEq("p." + mph.folder, req.pageInf.mergeArgs().getArg("pid"))
				.rs(st.instancontxt(conn, usr)).rs(0);

		return new AlbumResp().folder(rs.nxt(), mph);
	}

	protected static AlbumResp collect(AlbumReq req, IUser usr)
			throws SemanticException, TransException, SQLException, IOException {

		String cid = req.collectId;
		AnResultset rs = (AnResultset) st
				.select(tablCollects)
				.whereEq("cid", cid)
				.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr))
				.rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find photo collection for id = '%s' (permission of %s)", cid, usr.uid());

		AlbumResp album = new AlbumResp().setCollects(rs);

		String conn = Connects.uri2conn(req.uri());
		PhotoMeta meta = new PhotoMeta(conn);

		rs = (AnResultset) st
				.select(meta.tbl, "p")
				.j(tablCollectPhoto, "cp", "cp.pid = p.pid")
				.col("p.*")
				.whereEq("cp.cid", cid)
				.rs(st.instancontxt(conn, usr))
				.rs(0);

		album.photos(cid, rs, meta);

		return album;
	}

	/**
	 * <h4>Load album (aid = req.albumId)</h4>
	 * MEMO TODO Android client shouldn't reach here until now.
	 *
	 * <p>If albumId is empty, load according to the session's profile.
	 * </p>
	 *
	 * @param req
	 * @param usr
	 * @param prf
	 * @return album
	 * @throws SemanticException
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException
	 */
	protected AlbumResp album(DocsReq req, // should be AlbumReq (MVP 0.2.1)
			IUser usr, Profiles prf)
			throws SemanticException, TransException, SQLException, IOException {
		String conn = Connects.uri2conn(req.uri());
		PhotoMeta m = new PhotoMeta(conn);
		JUserMeta musr = new JUserMeta(conn);

		String aid = prf.defltAlbum;

		AnResultset rs = (AnResultset) synt
				.select(tablAlbums, "a")
				.j(musr.tbl, "u", "u.userId = a.shareby")
				.cols("a.*", "a.shareby ownerId", "u.userName owner")
				.whereEq("a.aid", aid)
				.rs(synt.instancontxt())
				.rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find album of id = %s (permission of %s)", aid, usr.uid());

		AlbumResp album = new AlbumResp(synode, synt.perdomain, synt.basictx().connId())
						.album(rs);

		rs = (AnResultset) st
				.select(m.tbl, "p").page(req.pageInf)
				.j(tablCollectPhoto , "ch", "ch.pid = p.pid")
				.j(tablAlbumCollect, "ac", "ac.cid = ch.cid")
				.j(tablCollects, "c", "c.cid = ch.cid")
				.j(tablAlbums, "a", "a.aid = ac.aid")
				.j(musr.tbl, "u", "u.userId = p.shareby")
				.cols("ac.aid", "ch.cid",
					  "p.pid", m.resname, m.createDate, "p." + m.tags,
					  m.mime, "p.css", m.folder, m.geox, m.geoy, m.shareDate,
					  "c.shareby collector", "c.cdate",
					  m.fullpath, m.device, "p." + m.shareby, "u.userName owner",
					  "storage", "aname", "cname")
				.whereEq("a.aid", aid)
				.rs(st.instancontxt(conn, usr))
				.rs(0);

		album.collectPhotos(rs, conn);

		return album;
	}
	
//	static String chainId(IUser usr, String clientpathRaw) {
//		return usr.sessionId() + " " + clientpathRaw;
//	}
}
