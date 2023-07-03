package io.oz.album.tier;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.transact.sql.parts.condition.Funcall.now;
import static io.odysz.transact.sql.parts.condition.Funcall.count;

import java.io.IOException;
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
import io.odysz.common.CheapMath;
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
import io.odysz.semantic.meta.DomainMeta;
import io.odysz.semantic.tier.docs.BlockChain;
import io.odysz.semantic.tier.docs.DocUtils;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.FileStream;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantic.tier.docs.SyncDoc.SyncFlag;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.PageInf;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;
import io.oz.album.AlbumFlags;
import io.oz.album.AlbumPort;
import io.oz.album.PhotoUser;
import io.oz.album.helpers.Exif;
import io.oz.album.tier.AlbumReq.A;

/**
 * <h5>The album tier 0.2.1 (MVP)</h5>
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

	static DomainMeta domainMeta;
	static final String tablUser = "a_users";

	/** uri db field */
	static final String uri = "uri";
	/** file state db field */
	static final String state = "state";

	private HashMap<String, BlockChain> blockChains;

	public static DATranscxt st;

	static IUser robot;

	static {
		try {
			st = new DATranscxt(null);
			robot = new PhotoUser("Robot Album");
			domainMeta = new DomainMeta();
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

		try {
			DocsReq jreq = msg.body(0);
			String a = jreq.a();
			if (A.download.equals(a))
				download(resp, msg.body(0), robot);
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			if (AlbumFlags.album)
				e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
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
				.select(m.tbl)
				.col(m.org).col(m.pk)
				.col("'a-001'", "album") // FIXME
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
		PageInf page = isNull(jreq.page)
				? new PageInf(0, -1, usr.orgId())
				: eq(jreq.page.condts.get(0), usr.orgId())
				? jreq.page
				: jreq.page.insertCondt(usr.orgId());

		List<?> lst = DatasetHelper.loadStree(conn, jreq.sk, page);
		return new AlbumResp().albumForest(lst);
	}

	AlbumResp profile(AlbumReq body, IUser usr, Profiles prf)
			throws SemanticException, TransException, SQLException {

		AnResultset rs = (AnResultset) st
				.select(domainMeta.tbl)
				.whereEq(domainMeta.pk, "home")
				.rs(st.instancontxt(Connects.uri2conn(body.uri()), usr))
				.rs(0);

		rs.beforeFirst().next();
		String home = rs.getString(domainMeta.domainName);

		return new AlbumResp().profiles(new Profiles(home));
	}

	DocsResp startBlocks(DocsReq body, IUser usr, Profiles prf)
			throws IOException, TransException, SQLException {

		String conn = Connects.uri2conn(body.uri());
		checkDuplicate(conn, ((PhotoUser)usr).deviceId(), body.clientpath(), usr, new PhotoMeta(conn));

		if (blockChains == null)
			blockChains = new HashMap<String, BlockChain>(2);

		// in jserv 1.4.3 and album 0.5.2, deleting temp dir is handled by PhotoRobot. 
		String tempDir = ((PhotoUser)usr).touchTempDir(conn);

		BlockChain chain = new BlockChain(tempDir, body.clientpath(), body.createDate, body.subFolder);

		// FIXME security breach?
		String id = usr.sessionId() + " " + chain.clientpath;

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
					.fullpath(chain.clientpath));
	}
	
	DocsResp endBlock(DocsReq body, IUser usr)
			throws SQLException, IOException, InterruptedException, TransException {
		String id = chainId(usr, body.clientpath());
		BlockChain chain;
		if (blockChains.containsKey(id)) {
			blockChains.get(id).closeChain();
			chain = blockChains.remove(id);
		} else
			throw new SemanticException("Ending block chain is not existing.");

		// insert photo (empty uri)
		String conn = Connects.uri2conn(body.uri());
		PhotoMeta meta = new PhotoMeta(conn);
		PhotoRec photo = new PhotoRec();

		photo.createDate = chain.cdate;
		Exif.parseExif(photo, chain.outputPath);

		photo.fullpath(chain.clientpath);
		photo.pname = chain.clientname;
		photo.uri = null;
		String pid = createFile(conn, photo, usr);

		// move file
		String targetPath = DocUtils.resolvExtroot(st, conn, pid, usr, meta);
		if (AlbumFlags.album)
			Utils.logi("   [AlbumFlags.album: end block] %s\n-> %s", chain.outputPath, targetPath);
		Files.move(Paths.get(chain.outputPath), Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);
		
//		try { onPhotoCreated(photo.recId, conn, meta, usr);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}

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
		// String id = body.chainId();
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
	
	private String chainId(IUser usr, String clientpathRaw) {
		return usr.sessionId() + " " + clientpathRaw;
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
				.limit(req.limit())	// FIXME issue: what if paths length > limit ?
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
				.col("pid")
				.col("pname").col("pdate")
				.col("folder").col("clientpath")
				.col("uri")
				.col("userName", "shareby")
				.col("sharedate").col("tags")
				.col("geox").col("geoy")
				.col("mime")
				.whereEq("pid", req.docId)
				.rs(st.instancontxt(conn, usr)).rs(0);

		if (!rs.next()) {
			// throw new SemanticException("Can't find file for id: %s (permission of %s)", req.docId, usr.uid());
			resp.setContentType("image/png");
			FileStream.sendFile(resp.getOutputStream(), missingFile);
		}
		else {
			String mime = rs.getString("mime");
			resp.setContentType(mime);
			FileStream.sendFile(resp.getOutputStream(), DocUtils.resolvExtroot(st, conn, req.docId, usr, meta));
		}
	}
	
	AlbumResp createPhoto(AlbumReq req, IUser usr, Profiles prf)
			throws TransException, SQLException, IOException {
		String conn = Connects.uri2conn(req.uri());
		checkDuplication(req, (PhotoUser) usr);

		String pid = createFile(conn, req.photo, usr);
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
	 * <p>TODO: replaced by SyncWorkerTest.createFileB64()</p>
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
		onPhotoCreated(pid, conn, meta, usr);

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
	 */
	static protected void onPhotoCreated(String pid, String conn, PhotoMeta m, IUser usr) {
		new Thread(() -> {
			AnResultset rs;
			try {
				rs = (AnResultset) st
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
					
					if (isblank(p.widthHeight)) {
						try { p.widthHeight = Exif.parseWidthHeight(pth); }
						catch (SemanticException e) {
							Utils.warn("Exif parse failed and can't parse width & height: %s", pth);
						}
					}
					if (isblank(p.wh))
						p.wh = CheapMath.reduceFract(p.widthHeight[0], p.widthHeight[1]);
					if (p.widthHeight[0] > p.widthHeight[1]) {
						int w = p.wh[0];
						p.wh[0] = p.wh[1];
						p.wh[1] = w;
					}

					Update u = st
						.update(m.tbl, usr)
						.nv(m.css, p.css())
						.nv(m.size, String.valueOf(p.size))
						.nv(m.syncflag, SyncFlag.publish)
						.whereEq(m.pk, pid);

					if (isblank(rs.getDate(m.createDate)))
						u.nv(m.createDate, now());

//					if (p.photoDate() != null) {
//					   u.nv(m.folder, p.folder())
//						.nv(m.createDate, p.photoDate())
//						.nv(m.uri, pth)
//						.nv(m.clientname, rs.getString(m.clientname))
//						.nv(m.shareby, usr.uid());

						if (!isblank(p.geox) || !isblank(p.geoy))
						   u.nv(m.exif, p.exif())
							.nv(m.geox, p.geox)
							.nv(m.geoy, p.geoy);

						if (!isblank(p.mime))
							u.nv(m.mime, p.mime);

//						if (!isblank(p.widthHeight))
//						 	u.nv(m.css, p.css());

						// u.whereEq("pid", pid)
						// .u(stx);
//					}
					u.u(stx);
				}
			} catch (TransException | SQLException | IOException e) {
				e.printStackTrace();
			}
		}).start();
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

		AnResultset rs = (AnResultset) st
				.select(meta.tbl, "p")
				.j("a_users", "u", "u.userId = p.shareby")
				.col("pid")
				.col("pname").col("pdate")
				.col("folder").col("clientpath").col("device")
				.col("uri")
				.col("userName", "shareby")
				.col("sharedate").col("tags")
				.col("geox").col("geoy")
				.col("mime").col("css")
				.whereEq("pid", req.docId)
				.rs(st.instancontxt(conn, usr)).rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find file for id: %s (permission of %s)", req.docId, usr.uid());

		return new AlbumResp().rec(rs);
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

		album.photos(cid, rs);

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
		PhotoMeta meta = new PhotoMeta(conn);

		String aid = prf.defltAlbum;

		AnResultset rs = (AnResultset) st
				.select(tablAlbums, "a")
				.j(tablUser, "u", "u.userId = a.shareby")
				.cols("a.*", "a.shareby ownerId", "u.userName owner")
				.whereEq("a.aid", aid)
				.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr))
				.rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find album of id = %s (permission of %s)", aid, usr.uid());

		AlbumResp album = new AlbumResp().album(rs);

		rs = (AnResultset) st
				.select(meta.tbl, "p").page(req.page)
				.j(tablCollectPhoto , "ch", "ch.pid = p.pid")
				.j(tablAlbumCollect, "ac", "ac.cid = ch.cid")
				.j(tablCollects, "c", "c.cid = ch.cid")
				.j(tablAlbums, "a", "a.aid = ac.aid")
				.j(tablUser, "u", "u.userId = p.shareby")
				.cols("ac.aid", "ch.cid",
					  "p.pid", "pname", "pdate", "p.tags", "mime", "p.css", "folder", "geox", "geoy", "sharedate",
					  "c.shareby collector", "c.cdate",
					  "clientpath", "device", "p.shareby", "u.userName owner",
					  "storage", "aname", "cname")
				.whereEq("a.aid", aid)
				.rs(st.instancontxt(conn, usr))
				.rs(0);

		album.collectPhotos(rs);

		return album;
	}
}
