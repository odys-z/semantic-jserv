package io.oz.album.tier;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.transact.sql.parts.condition.Funcall.count;
import static io.odysz.transact.sql.parts.condition.Funcall.now;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.Anson;
import io.odysz.anson.x.AnsonException;
import io.odysz.common.EnvPath;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetHelper;
import io.odysz.semantic.ext.DocTableMeta.Share;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantic.tier.docs.BlockChain;
import io.odysz.semantic.tier.docs.Device;
import io.odysz.semantic.tier.docs.DocUtils;
import io.odysz.semantic.tier.docs.Docs206;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.FileStream;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Delete;
import io.odysz.transact.sql.PageInf;
import io.odysz.transact.sql.Query;
import io.odysz.transact.sql.Update;
import io.odysz.transact.sql.parts.Logic;
import io.odysz.transact.sql.parts.Sql;
import io.odysz.transact.sql.parts.condition.ExprPart;
import io.odysz.transact.x.TransException;
import io.oz.album.AlbumFlags;
import io.oz.album.AlbumPort;
import io.oz.album.AlbumSingleton;
import io.oz.album.PhotoUser;
import io.oz.album.PhotoUser.PUserMeta;
import io.oz.album.helpers.Exif;
import io.oz.album.tier.AlbumReq.A;
import io.oz.jserv.docsync.DeviceTableMeta;

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
@WebServlet(description = "Album tier: albums", urlPatterns = { "/album.less" })
public class Albums extends ServPort<AlbumReq> {
	private static final long serialVersionUID = 1L;

	/** tringger exif parsing when new photo inserted */
	public static int POST_ParseExif = 1;

	/** db photo table */
	static final String tablAlbums = "h_albums";
	/** db collection table */
	static final String tablCollects = "h_collects";

	static final String tablAlbumCollect = "h_album_coll";

	static final String tablCollectPhoto = "h_coll_phot";

	static final DocOrgMeta orgMeta = new DocOrgMeta();
	
	static final DeviceTableMeta devMeta = new DeviceTableMeta(null);


	/** uri db field */
	static final String uri = "uri";
	/** file state db field */
	static final String state = "state";

	private HashMap<String, BlockChain> blockChains;

	public static DATranscxt st;

	static IUser robot;

	PUserMeta userMeta;

	static {
		try {
			st = new DATranscxt(null);
			robot = new PhotoUser("Robot Album");
			
			Docs206.getMeta = (String uri) -> {
				try {
					String conn = Connects.uri2conn(uri);
					return new PhotoMeta(conn);
				}
				catch (TransException e) {
					e.printStackTrace();
					return null;
				}
			};
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	public Albums() {
		super(AlbumPort.album);
		
		missingFile = "";
	}

	String missingFile = "";

	public Albums missingFile(String onlyPng) {
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

			if (A.collect.equals(a) || A.rec.equals(a) || A.download.equals(a)) {
				// Session less
				IUser usr = robot;
				if (A.collect.equals(a))
					rsp = collect(jmsg.body(0), usr);
				else if (A.rec.equals(a))
					rsp = rec(jmsg.body(0), usr);
				else if (A.download.equals(a))
					download(resp, jmsg.body(0), usr);
			} else {
				// session required
				PhotoUser usr = (PhotoUser) JSingleton.getSessionVerifier().verify(jmsg.header());
				
				if (userMeta == null)
					userMeta = (PUserMeta) usr.meta();

				Profiles prf = verifyProfiles(jmsg.body(0), usr, a);

				if (A.insertPhoto.equals(a))
					rsp = createPhoto(jmsg.body(0), usr, prf);
				else if (A.del.equals(a))
					rsp = delPhoto(jmsg.body(0), usr, prf);
				else if (A.selectSyncs.equals(a))
					rsp = querySyncs(jmsg.body(0), usr, prf);
				else if (A.getPrefs.equals(a))
					rsp = profile(jmsg.body(0), usr, prf);
				else if (A.album.equals(a)) // load
					rsp = album(jmsg.body(0), usr, prf);
				else if (A.stree.equals(a))
					rsp = galleryTree(jmsg.body(0), usr, prf);

				//
				else if (DocsReq.A.blockStart.equals(a))
					rsp = startBlocks(jmsg.body(0), usr, prf);
				else if (DocsReq.A.blockUp.equals(a))
					rsp = uploadBlock(jmsg.body(0), usr);
				else if (DocsReq.A.blockEnd.equals(a))
					rsp = endBlock(jmsg.body(0), usr);
				else if (DocsReq.A.blockAbort.equals(a))
					rsp = abortBlock(jmsg.body(0), usr);
				else if (DocsReq.A.devices.equals(a))
					rsp = devices(jmsg.body(0), usr);
				else if (DocsReq.A.checkDev.equals(a))
					rsp = chkDevname(jmsg.body(0), usr);
				else if (DocsReq.A.registDev.equals(a))
					rsp = registDevice(jmsg.body(0), usr);
				else if (AlbumReq.A.updateFolderel.equals(a))
					rsp = updateFolderel(jmsg.body(0), usr);

				else
					throw new SemanticException("Request.a can not be handled, request.a: %s", jreq.a());
			}

			if (rsp != null) { // no rsp for a == download
				rsp.syncing(jreq.syncing());
				write(resp, ok(rsp).port(AlbumPort.album));
			}
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			if (AlbumFlags.album)
				e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} catch (InterruptedException e) {
			if (Anson.verbose)
				e.printStackTrace();
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
	DocsResp updateFolderel(AlbumReq req, PhotoUser usr) throws TransException, SQLException {
		String conn = Connects.uri2conn(req.uri());
		PhotoMeta phm = new PhotoMeta(conn);
		Photo_OrgMeta pom = new Photo_OrgMeta(conn);

		Delete d = st
				.delete(pom.tbl, usr)
				.whereIn(pom.pid, st.select(phm.tbl).col(phm.pk).whereEq(phm.folder, req.subfolder))
				.whereIn(pom.oid, req.getChecks("oid"));
		if (!req.clearels) {
			d.post(st.insert(pom.tbl)
				.cols(pom.pid, pom.oid)
				.select(st
					.select(phm.tbl, "ph")
					.distinct()
					.col("ph." + phm.pk).col("po." + pom.oid)
					.j(pom.tbl, "po", Sql.condt(Logic.op.in, "po.oid", new ExprPart(req.getChecks("oid")))
									 .and(Sql.condt(Logic.op.eq, "ph.folder", ExprPart.constr(req.subfolder))))// new Predicate(op.in, "po.orgId", req.getChecks("oid")))
					.whereEq(phm.folder, req.subfolder)));
		}

		SemanticObject res = (SemanticObject)d
				.d(st.instancontxt(conn, usr));

		return (DocsResp) new DocsResp().data(res.props());
	}

	/**
	 * Generate user's profile - used at server side,
	 * yet {@link IUser#profile()} is used for loading profile for client side.
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
		String conn = Connects.uri2conn(body.uri());
		JUserMeta m = (JUserMeta) usr.meta(conn);
		AnResultset rs = ((AnResultset) st
				.select(m.tbl, "u")
				.je("u", orgMeta.tbl, "o", m.org, orgMeta.pk)
				.col("u." + m.org).col(m.pk)
				.col(orgMeta.album0, "album") 
				.col(orgMeta.webroot)
				.whereEq(m.pk, usr.uid())
				.rs(st.instancontxt(conn, usr))
				.rs(0)).nxt();

		if (isblank(rs.getString(m.org)))
			throw new SemanticException("Verifying user's profiles needs target user belongs to an organization / family.");
		return new Profiles(rs, m);
	}

	AlbumResp galleryTree(AlbumReq jreq, IUser usr, Profiles prf) throws SQLException, TransException {
		if (isblank(jreq.sk))
			throw new SemanticException("AlbumReq.sk is required.");

		String conn = Connects.uri2conn(jreq.uri());
		// force org-id as first arg
		PageInf page = isNull(jreq.pageInf)
				? new PageInf(0, -1, usr.orgId())
				: eq(jreq.pageInf.arrCondts.get(0), usr.orgId())
				? jreq.pageInf
				: jreq.pageInf.insertCondt(usr.orgId());

		List<?> lst = DatasetHelper.loadStree(conn, jreq.sk, page);
		return new AlbumResp().albumForest(lst);
	}

	AlbumResp profile(AlbumReq body, IUser usr, Profiles prf)
			throws SemanticException, TransException, SQLException {

		AnResultset rs = (AnResultset) st
				.select(orgMeta.tbl)
				.whereEq(orgMeta.pk, usr.orgId())
				.rs(st.instancontxt(Connects.uri2conn(body.uri()), usr))
				.rs(0);

		rs.beforeFirst().next();
		String home = rs.getString(orgMeta.orgName);
		String webroot = rs.getString(orgMeta.webroot);

		return new AlbumResp().profiles(new Profiles(home).webroot(webroot));
	}

	DocsResp startBlocks(DocsReq body, IUser usr, Profiles prf)
			throws IOException, TransException, SQLException {

		String conn = Connects.uri2conn(body.uri());
		checkDuplicate(conn, ((PhotoUser)usr).deviceId(), body.clientpath(), usr, new PhotoMeta(conn));

		if (blockChains == null)
			blockChains = new HashMap<String, BlockChain>(2);

		String tempDir = ((PhotoUser)usr).touchTempDir(conn);

		BlockChain chain = new BlockChain(tempDir, body.clientpath(), body.createDate, body.subfolder);

		// FIXME security breach?
		String id = chainId(usr, chain.clientpath);

		if (blockChains.containsKey(id))
			throw new SemanticException("Why started again?");

		blockChains.put(id, chain);
		return new DocsResp()
				.blockSeq(-1)
				.doc((SyncDoc) new SyncDoc()
					.clientname(chain.clientname)
					.cdate(body.createDate)
					.fullpath(chain.clientpath));
	}

	void checkDuplication(AlbumReq body, PhotoUser usr)
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
				.whereEq(meta.synoder, device)
				.whereEq(meta.fullpath, clientpath)
				.rs(st.instancontxt(conn, usr))
				.rs(0)).nxt();

		if (rs.getInt("cnt") > 0)
			throw new SemanticException("Found existing file for device %s, client path: %s",
					device, clientpath);
	}

	DocsResp uploadBlock(DocsReq body, IUser usr) throws IOException, TransException {
		String id = chainId(usr, body.clientpath());
		if (!blockChains.containsKey(id))
			throw new SemanticException("Uploading blocks must accessed after starting chain is confirmed.");

		BlockChain chain = blockChains.get(id);
		chain.appendBlock(body);

		return new DocsResp()
				.blockSeq(body.blockSeq())
				.doc((SyncDoc) new SyncDoc()
					.clientname(chain.clientname)
					.cdate(body.createDate)
					.fullpath(body.clientpath()));
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
	 * @throws InterruptedException
	 * @throws TransException
	 */
	DocsResp endBlock(DocsReq body, IUser usr)
			throws SQLException, IOException, InterruptedException, TransException {
		String id = chainId(usr, body.clientpath());
		BlockChain chain;
		if (blockChains.containsKey(id)) {
			blockChains.get(id).closeChain();
			chain = blockChains.remove(id);
		} else
			throw new SemanticException("Ending block chain which is not existing.");

		// insert photo (empty uri)
		String conn = Connects.uri2conn(body.uri());
		PhotoMeta meta = new PhotoMeta(conn);
		PhotoRec photo = new PhotoRec();

		photo.createDate = chain.cdate;
		photo.fullpath(chain.clientpath);
		photo.pname = chain.clientname;
		photo.uri = null; // accepting new value
		String pid = createFile(conn, photo, usr);

		// move file
		String targetPath = DocUtils.resolvExtroot(st, conn, pid, usr, meta);
		if (AlbumFlags.album)
			Utils.logi("   [AlbumFlags.album: end block] %s\n-> %s", chain.outputPath, targetPath);
		Files.move(Paths.get(chain.outputPath), Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);

		onPhotoCreated(pid, conn, meta, usr);

		return new DocsResp()
				.blockSeq(body.blockSeq())
				.doc((SyncDoc) new SyncDoc()
					.recId(pid)
					.device(body.device())
					.folder(photo.folder())
					.clientname(chain.clientname)
					.cdate(body.createDate)
					.fullpath(chain.clientpath));
	}

	DocsResp abortBlock(DocsReq body, IUser usr)
			throws SQLException, IOException, InterruptedException, TransException {
		String id = chainId(usr, body.clientpath());
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
	DocsResp devices(DocsReq body, PhotoUser usr)
			throws SemanticException, TransException, SQLException {
		AnResultset rs = (AnResultset)st
				.select(devMeta.tbl)
				.whereEq(devMeta.org(),   usr.orgId())
				.whereEq(devMeta.owner,   usr.uid())
				.rs(st.instancontxt(Connects.uri2conn(body.uri()), usr))
				.rs(0)
				;

		return (DocsResp) new DocsResp().rs(rs)
				.data(devMeta.owner, usr.uid())
				.data("owner-name",  usr.userName())
				.data(devMeta.org(), usr.orgId());
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
	DocsResp chkDevname(DocsReq body, PhotoUser usr)
			throws SemanticException, TransException, SQLException {

		AnResultset rs = ((AnResultset) st
			.select(devMeta.tbl, "d")
			.cols("d.*", "u." + userMeta.uname)
			.j(userMeta.tbl, "u", "u.%s = d.%s", userMeta.pk, devMeta.owner)
			.cols(devMeta.devname, devMeta.synode0, devMeta.cdate, devMeta.owner)
			.whereEq(devMeta.pk, usr.deviceId())
			// .whereEq(devMeta.synode0, eq(owner, devMeta.synode0) ? synode0 : null)
			.whereEq(devMeta.org(),   usr.orgId())
			.whereEq(devMeta.owner,   usr.uid())
			// .whereEq(devMeta.market, eq(market[0], devMeta.market) ? market[1] : null)
			.rs(st.instancontxt(Connects.uri2conn(body.uri()), usr))
			.rs(0))
			.nxt();
		
		if (rs.next()) {
			throw new SemanticException("{\"exists\": true, \"owner\": \"%s\", \"synode0\": \"%s\", \"create_on\": \"%s\"}",
				rs.getString(userMeta.uname),
				rs.getString(devMeta.synode0),
				rs.getString(devMeta.cdate)
			);
		}
		else 
			return (DocsResp) new DocsResp().device(new Device(
					null, AlbumSingleton.synode(),
					rs.getString(devMeta.devname)));
	}
	
	DocsResp registDevice(DocsReq body, PhotoUser usr)
			throws SemanticException, TransException, SQLException {
		if (isblank(body.device().id)) {
			SemanticObject result = (SemanticObject) st
				.insert(devMeta.tbl, usr)
				.nv(devMeta.synode0, AlbumSingleton.synode())
				.nv(devMeta.devname, body.device().devname)
				.nv(devMeta.owner, usr.uid())
				.nv(devMeta.cdate, now())
				.nv(devMeta.org(), usr.orgId())
				// .nv(devMeta.mac, body.mac())
				.ins(st.instancontxt(Connects.uri2conn(body.uri()), usr));

			String resulved = result.resulve(devMeta.tbl, devMeta.pk);
			return new DocsResp().device(new Device(
				resulved, AlbumSingleton.synode(), body.device().devname));
		}
		else {
			if (isblank(body.device().id))
				throw new SemanticException("Error for pdating device name without a device id.");

			st  .update(devMeta.tbl, usr)
				.nv(devMeta.cdate, now())
				.whereEq(devMeta.org(), usr.orgId())
				.whereEq(devMeta.pk, body.device().id)
				.u(st.instancontxt(Connects.uri2conn(body.uri()), usr));

			return new DocsResp().device(new Device(
				body.device().id, AlbumSingleton.synode(), body.device().devname));
		}
	}

	/**
	 * Query client paths
	 * @param req
	 * @param usr
	 * @param prf
	 * @param meta
	 * @return album where clientpath in req's fullpath and device also matched
	 * @throws SemanticException
	 * @throws TransException
	 * @throws SQLException
	 */
	@SuppressWarnings("deprecation")
	DocsResp querySyncs(DocsReq req, IUser usr, Profiles prf)
			throws SemanticException, TransException, SQLException {

		if (req.syncQueries() == null)
			throw new SemanticException("Null Query - invalide request.");

		ArrayList<String> paths = new ArrayList<String>(req.syncQueries().size());
		for (String s : req.syncQueries()) {
			paths.add(s);
		}

		String conn = Connects.uri2conn(req.uri());
		PhotoMeta meta = new PhotoMeta(conn);

		Object[] kpaths = req.syncing().paths() == null ? new Object[0]
				: req.syncing().paths().keySet().toArray();

		AnResultset rs = ((AnResultset) st
				.select(req.docTabl, "t")
				.cols(SyncDoc.synPageCols(meta))
				.whereEq(meta.org(), req.org == null ? usr.orgId() : req.org)
				.whereEq(meta.synoder, usr.deviceId())
				.whereIn(meta.fullpath, Arrays.asList(kpaths).toArray(new String[kpaths.length]))
				// TODO add file type for performance
				// FIXME issue: what if paths length > limit ?
				.limit(req.limit())
				.rs(st.instancontxt(conn, usr))
				.rs(0))
				.beforeFirst();

		DocsResp album = new DocsResp().syncing(req).pathsPage(rs, meta);

		return album;
	}

	void download(HttpServletResponse resp, DocsReq req, IUser usr)
			throws IOException, SemanticException, TransException, SQLException {

		String conn = Connects.uri2conn(req.uri());
		PhotoMeta meta = new PhotoMeta(conn);

		AnResultset rs = (AnResultset) st
				.select(meta.tbl, "p")
				.j("a_users", "u", "u.userId = p.shareby")
				.col(meta.pk)
				.col(meta.clientname).col(meta.createDate)
				.col(meta.folder).col(meta.fullpath)
				.col(meta.uri)
				.col("userName", "shareby")
				.col(meta.shareDate).col(meta.tags)
				.col("geox").col("geoy")
				.col("mime")
				.whereEq("pid", req.docId)
				.rs(st.instancontxt(conn, usr)).rs(0);

		if (!rs.next()) {
			resp.setContentType("image/png");
			FileStream.sendFile(resp.getOutputStream(), missingFile);
		}
		else {
			String mime = rs.getString("mime");
			resp.setContentType(mime);
			
			try ( OutputStream os = resp.getOutputStream() ) {
				FileStream.sendFile(os, DocUtils.resolvExtroot(st, conn, req.docId, usr, meta));
				os.close();
			} catch (IOException e) {
				// If the user dosen't play a video, Chrome will close the connection before finishing downloading.
				// This is harmless: https://stackoverflow.com/a/70020526/7362888
				// Utils.warn(e.getMessage());
			}
		}
	}

	AlbumResp createPhoto(AlbumReq req, IUser usr, Profiles prf)
			throws TransException, SQLException, IOException {
		String conn = Connects.uri2conn(req.uri());
		checkDuplication(req, (PhotoUser) usr);

		String pid = createFile(conn, req.photo, usr);
		
		onPhotoCreated(pid, conn, new PhotoMeta(conn), usr);
		return new AlbumResp().photo(req.photo, pid);
	}

	static DocsResp delPhoto(AlbumReq req, IUser usr, Profiles prf)
			throws TransException, SQLException {
		String conn = Connects.uri2conn(req.uri());
		PhotoMeta meta = new PhotoMeta(conn);

		SemanticObject res = (SemanticObject) st
				.delete(meta.tbl, usr)
				.whereEq("device", req.device())
				.whereEq("clientpath", req.clientpath())
				// .post(Docsyncer.onDel(req.clientpath, req.device()))
				.d(st.instancontxt(conn, usr));

		return (DocsResp) new DocsResp().data(res.props());
	}

	/**
	 * <p>Create photo - call this after duplication is checked.</p>
	 * <p>Photo is created as in the folder of user/month/.</p>
	 *
	 * @param conn
	 * @param photo
	 * @param usr
	 * @return pid
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException
	 */
	public static String createFile(String conn, PhotoRec photo, IUser usr)
			throws TransException, SQLException, IOException {

		PhotoMeta meta = new PhotoMeta(conn);

		if (isblank(photo.shareby))
			photo.share(usr.uid(), Share.priv);

		String pid = DocUtils.createFileB64(conn, photo, usr, meta, st, null);

		return pid;
	}

	/**
	 * This method parse exif, update geox/y, date etc. - should only be used when file created.
	 *
	 * TODO generate preview
	 *
	 * @param pid
	 * @param conn
	 * @param usr
	 * @return 
	 */
	static protected void onPhotoCreated(String pid, String conn, PhotoMeta m, IUser usr) {
		new Thread(() -> {
		try {
			AnResultset rs = (AnResultset) st
				.select(m.tbl, "p")
				.col(m.folder).col(m.fullpath)
				.col(m.uri)
				.col(m.clientname)
				.col(m.createDate)
				.whereEq(m.pk, pid)
				.rs(st.instancontxt(conn, usr))
				.rs(0);

			if (rs.next()) {
				ISemantext stx = st.instancontxt(conn, usr);
				String pth = EnvPath.decodeUri(stx, rs.getString("uri"));
				PhotoRec p = new PhotoRec();
				Exif.parseExif(p, pth);

				Update u = st
					.update(m.tbl, usr)
					.nv(m.css, p.css())
					.nv(m.size, String.valueOf(p.size))
					.whereEq(m.pk, pid);

				if (isblank(rs.getDate(m.createDate)))
					u.nv(m.createDate, now());


					if (!isblank(p.geox) || !isblank(p.geoy))
						u.nv(m.geox, p.geox)
						 .nv(m.geoy, p.geoy);
					if (!isblank(p.exif()))
						u.nv(m.exif, p.exif());
					else // figure out mime with file extension
						;

					if (!isblank(p.mime))
						u.nv(m.mime, p.mime);
				u.u(stx);
			}
		} catch (TransException | SQLException e) {
			e.printStackTrace();
		}})
		.start();
	}

	/**
	 * Read a media file record (id, uri), TODO touch LRU.
	 *
	 * @param req
	 * @param usr
	 * @return loaded media record
	 * @throws SQLException
	 * @throws TransException
	 * @throws SemanticException
	 * @throws IOException
	 */
	protected static AlbumResp rec(AlbumReq req, IUser usr)
			throws SemanticException, TransException, SQLException, IOException {

		String conn = Connects.uri2conn(req.uri());
		PhotoMeta meta = new PhotoMeta(conn);
		Photo_OrgMeta mp_o = new Photo_OrgMeta(conn);

		Query q = st
				.select(meta.tbl, "p")
				.j("a_users", "u", "u.userId = p.shareby")
				.l(mp_o.tbl , "po", "po.pid = p.pid");

		AnResultset rs = (AnResultset) PhotoRec.cols(q, meta)
				.col(meta.shareby, "shareby").col(count("po.oid"), "orgs")
				.whereEq("p." + meta.pk, req.pageInf.mergeArgs().getArg("pid"))
				.rs(st.instancontxt(conn, usr)).rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find file for id: '%s' (permission of %s)",
					!isblank(req.docId)
					? req.docId
					: !isblank(req.pageInf)
					? !isblank(req.pageInf.mapCondts)
					  ? req.pageInf.mapCondts.get("pid")
					  : isNull(req.pageInf.arrCondts) ? null : req.pageInf.arrCondts.get(0)[1]
					: null,
					usr.uid());

		return new AlbumResp().rec(rs, meta);
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
	protected static AlbumResp album(DocsReq req, // should be AlbumReq (MVP 0.2.1)
			IUser usr, Profiles prf)
			throws SemanticException, TransException, SQLException, IOException {
		String conn = Connects.uri2conn(req.uri());
		PhotoMeta m = new PhotoMeta(conn);
		PUserMeta musr = new PUserMeta(conn);

		String aid = prf.defltAlbum;

		AnResultset rs = (AnResultset) st
				.select(tablAlbums, "a")
				.j(musr.tbl, "u", "u.userId = a.shareby")
				.cols("a.*", "a.shareby ownerId", "u.userName owner")
				.whereEq("a.aid", aid)
				.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr))
				.rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find album of id = %s (permission of %s)", aid, usr.uid());

		AlbumResp album = new AlbumResp().album(rs);

		rs = (AnResultset) st
				.select(m.tbl, "p").page(req.pageInf)
				.j(tablCollectPhoto , "ch", "ch.pid = p.pid")
				.j(tablAlbumCollect, "ac", "ac.cid = ch.cid")
				.j(tablCollects, "c", "c.cid = ch.cid")
				.j(tablAlbums, "a", "a.aid = ac.aid")
				.j(musr.tbl, "u", "u.userId = p.shareby")
				.cols("ac.aid", "ch.cid",
					  "p.pid", m.clientname, m.createDate, "p." + m.tags,
					  m.mime, "p.css", m.folder, m.geox, m.geoy, m.shareDate,
					  "c.shareby collector", "c.cdate",
					  m.fullpath, m.synoder, "p." + m.shareby, "u.userName owner",
					  "storage", "aname", "cname")
				.whereEq("a.aid", aid)
				.rs(st.instancontxt(conn, usr))
				.rs(0);

		album.collectPhotos(rs, conn);

		return album;
	}
	
	static String chainId(IUser usr, String clientpathRaw) {
		return usr.sessionId() + " " + clientpathRaw;
	}
}
