package io.oz.album.tier;

import static io.odysz.common.LangExt.isblank;

import java.io.IOException;
import java.io.OutputStream;
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

import io.odysz.anson.Anson;
import io.odysz.anson.x.AnsonException;
import io.odysz.common.EnvPath;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.ShExtFile;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.tier.docs.BlockChain;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.FileStream;
import io.odysz.semantic.tier.docs.SyncRec;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Update;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;
import io.oz.album.AlbumFlags;
import io.oz.album.AlbumPort;
import io.oz.album.PhotoRobot;
import io.oz.album.helpers.Exif;
import io.oz.album.tier.AlbumReq.A;

/**
 * <h5>The album tier</h5> Although this tie is using the pattern of
 * <i>less</i>, it's also verifying user when uploading - for subfolder name of
 * user
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
	public static final String tablPhotos = "h_photos";
	/** db photo table */
	static final String tablAlbums = "h_albums";
	/** db collection table */
	static final String tablCollects = "h_collects";

	static final String tablAlbumCollect = "h_album_coll";

	static final String tablCollectPhoto = "h_coll_phot";

	static final String tablDomain = "a_domain";
	static final String tablUser = "a_users";

	/** uri db field */
	static final String uri = "uri";
	/** file state db field */
	static final String state = "state";

	private HashMap<String, BlockChain> blockChains;

	protected static DATranscxt st;

	static IUser robot;

	static {
		try {
			st = new DATranscxt(null);
			robot = new PhotoRobot("Robot Album");
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}
	
	public Albums() {
		super(AlbumPort.album);
	}
	
	@Override
	protected void onGet(AnsonMsg<AlbumReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {

		if (AlbumFlags.album)
			Utils.logi("---------- ever-connect /album.less GET  ----------");

		try {
			DocsReq jreq = msg.body(0);
			String a = jreq.a();
			if (A.download.equals(a))
				download(resp.getOutputStream(), msg.body(0), robot);
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			if (AlbumFlags.album)
				e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
//		} catch (InterruptedException e) {
//			if (Anson.verbose)
//				e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void onPost(AnsonMsg<AlbumReq> jmsg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {

		if (AlbumFlags.album)
			Utils.logi("========== ever-connect /album.less POST ==========");

		try {
			DocsReq jreq = jmsg.body(0);
			String a = jreq.a();
			DocsResp rsp = null;

			if (A.records.equals(a) || A.collect.equals(a) || A.rec.equals(a) || A.download.equals(a)) {
				// Session less
				IUser usr = robot;
				if (A.records.equals(a)) // load
					rsp = album(jmsg.body(0), usr);
				else if (A.collect.equals(a))
					rsp = collect(jmsg.body(0), usr);
				else if (A.rec.equals(a))
					rsp = rec(jmsg.body(0), usr);
				else if (A.download.equals(a))
					download(resp.getOutputStream(), jmsg.body(0), usr);
			} else {
				// session required
				IUser usr = JSingleton.getSessionVerifier().verify(jmsg.header());
				if (A.insertPhoto.equals(a))
					rsp = createPhoto(jmsg.body(0), usr);
				else if (A.del.equals(a))
					rsp = delPhoto(jmsg.body(0), usr);
				else if (A.selectSyncs.equals(a))
					rsp = querySyncs(jmsg.body(0), usr);
				else if (A.getPrefs.equals(a))
					rsp = profile(jmsg.body(0), usr);

				//
				else if (DocsReq.A.blockStart.equals(a))
					rsp = startBlocks(jmsg.body(0), usr);
				else if (DocsReq.A.blockUp.equals(a))
					rsp = uploadBlock(jmsg.body(0), usr);
				else if (DocsReq.A.blockEnd.equals(a))
					rsp = endBlock(jmsg.body(0), usr);
				else if (DocsReq.A.blockAbort.equals(a))
					rsp = abortBlock(jmsg.body(0), usr);

				else
					throw new SemanticException("request.body.a can not handled request: %s", jreq.a());
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

	AlbumResp profile(AlbumReq body, IUser usr) throws SemanticException, TransException, SQLException {
		AnResultset rs = (AnResultset) st
				.select(tablDomain)
				.whereEq("domainId", "home")
				.rs(st.instancontxt(Connects.uri2conn(body.uri()), usr))
				.rs(0);

		rs.beforeFirst().next();
		String home = rs.getString("domainName");

		return new AlbumResp().profiles(new Profiles(home));
	}

	DocsResp startBlocks(DocsReq body, IUser usr) throws IOException, TransException, SQLException {
		checkDuplicate(Connects.uri2conn(body.uri()), ((PhotoRobot)usr).deviceId(), body.clientpath, usr);

		if (blockChains == null)
			blockChains = new HashMap<String, BlockChain>(2);

		// in jserv 1.4.3 and album 0.5.2, deleting temp dir is handled by PhotoRobot. 
		String conn = Connects.uri2conn(body.uri());
		String tempDir = ((PhotoRobot)usr).touchTempDir(conn);

		BlockChain chain = new BlockChain(tempDir, body.clientpath, body.createDate);

		// FIXME security breach?
		String id = usr.sessionId() + " " + chain.clientpath;

		if (blockChains.containsKey(id))
			throw new SemanticException("Why started again?");

		blockChains.put(id, chain);
		return new DocsResp()
				.blockSeq(-1)
				.clientname(chain.clientname)
				.fullpath(chain.clientpath)
				.cdate(body.createDate);
	}

	void checkDuplication(AlbumReq body, PhotoRobot usr)
			throws SemanticException, TransException, SQLException {

		checkDuplicate(Connects.uri2conn(body.uri()),
				usr.deviceId(), body.photo.clientpath, usr);
	}

	private void checkDuplicate(String conn, String device, String clientpath, IUser usr)
			throws SemanticException, TransException, SQLException {

		AnResultset rs = (AnResultset) st
				.select(tablPhotos, "p")
				.col(Funcall.count("pid"), "cnt")
				.whereEq("device", device)
				.whereEq("clientpath", clientpath)
				.rs(st.instancontxt(conn, usr))
				.rs(0);
		rs.beforeFirst().next();

		if (rs.getInt("cnt") > 0)
			throw new SemanticException("Found existing file for device %s, client path: %s",
					device, clientpath);
	}
	
	DocsResp uploadBlock(DocsReq body, IUser usr) throws IOException, TransException {
		// String id = body.chainId();
		String id = chainId(usr, body.clientpath);
		if (!blockChains.containsKey(id))
			throw new SemanticException("Uploading blocks must accessed after starting chain is confirmed.");

		BlockChain chain = blockChains.get(id);
		chain.appendBlock(body);

		return new DocsResp()
				.blockSeq(body.blockSeq())
				.clientname(chain.clientname)
				.fullpath(chain.clientpath)
				.cdate(body.createDate);
	}
	
	DocsResp endBlock(DocsReq body, IUser usr)
			throws SQLException, IOException, InterruptedException, TransException {
		String id = chainId(usr, body.clientpath);
		BlockChain chain;
		if (blockChains.containsKey(id)) {
			blockChains.get(id).closeChain();
			chain = blockChains.remove(id);
		} else
			throw new SemanticException("Ending block chain is not existing.");

		// insert photo (empty uri)
		String conn = Connects.uri2conn(body.uri());
		Photo photo = new Photo();

		photo.createDate = chain.cdate;
		Exif.parseExif(photo, chain.outputPath);

		photo.clientpath = chain.clientpath;
		photo.pname = chain.clientname;
		photo.uri = null;
		String pid = createFile(conn, photo, usr);

		// move file
		String targetPath = resolvExtroot(conn, pid, usr);
		if (AlbumFlags.album)
			Utils.logi("   %s\n-> %s", chain.outputPath, targetPath);
		Files.move(Paths.get(chain.outputPath), Paths.get(targetPath), StandardCopyOption.REPLACE_EXISTING);

		return new DocsResp()
				.recId(pid)
				.blockSeq(body.blockSeq())
				.clientname(chain.clientname)
				.fullpath(chain.clientpath)
				.cdate(body.createDate);
	}
	
	DocsResp abortBlock(DocsReq body, IUser usr)
			throws SQLException, IOException, InterruptedException, TransException {
		// String id = body.chainId();
		String id = chainId(usr, body.clientpath);
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
	
	AlbumResp querySyncs(AlbumReq req, IUser usr)
			throws SemanticException, TransException, SQLException {

		if (req.syncQueries() == null)
			throw new SemanticException("Null Query - invalide request.");

		ArrayList<String> paths = new ArrayList<String>(req.syncQueries().size());
		// ArrayList<String[]> orders = new ArrayList<String[]>(req.syncQueries().size());
		for (SyncRec s : req.syncQueries()) {
			paths.add(s.fullpath());
			// orders.add(new String[] { String.format("pid = '%s'", s.fullpath()) });
		}

		AnResultset rs = (AnResultset) st.select(tablPhotos).col("clientpath").col("1", "syncFlag")
				.whereIn("clientpath", paths).whereEq("device", req.syncing().device)
				// .orderby(orders)
				.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr)).rs(0);

		AlbumResp album = new AlbumResp().syncRecords("sync-temp-id", rs);

		return album;
	}
	
	void download(OutputStream ofs, DocsReq freq, IUser usr)
			throws IOException, SemanticException, TransException, SQLException {
		String conn = Connects.uri2conn(freq.uri());
		FileStream.sendFile(ofs, resolvExtroot(conn, freq.docId, usr));
	}
	
	static String resolvExtroot(String conn, String docId, IUser usr) throws TransException, SQLException {
		ISemantext stx = st.instancontxt(conn, usr);
		AnResultset rs = (AnResultset) st.select(tablPhotos).col("uri").col("folder").whereEq("pid", docId).rs(stx)
				.rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find file for id: %s (permission of %s)", docId, usr.uid());

		String extroot = ((ShExtFile) DATranscxt.getHandler(conn, tablPhotos, smtype.extFile)).getFileRoot();
		return EnvPath.decodeUri(extroot, rs.getString("uri"));
	}
	
	AlbumResp createPhoto(AlbumReq req, IUser usr) throws TransException, SQLException, IOException {
		String conn = Connects.uri2conn(req.uri());
		checkDuplication(req, (PhotoRobot) usr);

		String pid = createFile(conn, req.photo, usr);
		return new AlbumResp().photo(req.photo, pid);
	}

	DocsResp delPhoto(AlbumReq req, IUser usr) throws TransException, SQLException {
		String conn = Connects.uri2conn(req.uri());

		SemanticObject res = (SemanticObject) st
				.delete(tablPhotos, usr)
				.whereEq("device", req.device())
				.whereEq("clientpath", req.clientpath)
				.d(st.instancontxt(conn, usr));

		return (DocsResp) new DocsResp().data(res.props()); 
	}

	/**create photo - call this after duplication is checked.
	 * 
	 * <p>Photo is created as in the folder of user/month/.
	 * @param conn
	 * @param photo
	 * @param usr
	 * @return pid
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException
	 */
	String createFile(String conn, Photo photo, IUser usr)
			throws TransException, SQLException, IOException {
		// clearer message is better here
		if (LangExt.isblank(photo.clientpath))
			throw new SemanticException("Client path can't be null/empty.");
		
		if (LangExt.isblank(photo.month(), " - - "))
			throw new SemanticException("Month of photo creating is important for saving files. It's required for creating media file.");

		Insert ins = st.insert(tablPhotos, usr)
				.nv("uri", photo.uri).nv("pname", photo.pname)
				.nv("pdate", photo.photoDate())
				.nv("folder", photo.month())
				.nv("device", ((PhotoRobot) usr).deviceId())
				.nv("geox", photo.geox).nv("geoy", photo.geoy)
				.nv("clientpath", photo.clientpath)
				.nv("exif", photo.exif)
				.nv("shareby", usr.uid())
				.nv("sharedate", Funcall.now());
		
		if (!LangExt.isblank(photo.mime))
			ins.nv("mime", photo.mime);

		SemanticObject res = (SemanticObject) ins.ins(st.instancontxt(conn, usr));
		String pid = ((SemanticObject) ((SemanticObject) res.get("resulved"))
				.get("h_photos"))
				.getString("pid");
		
		if (photo.geox == null || photo.month == null)
			onPhotoCreated(pid, conn, usr);

		return pid;
	}

	/**This method update geox,y and date automatically - should only used when creating pictures.
	 * @param pid
	 * @param conn
	 * @param usr
	 */
	static protected void onPhotoCreated(String pid, String conn, IUser usr) {
		new Thread(() -> {
			AnResultset rs;
			try {
				rs = (AnResultset) st
					.select(tablPhotos, "p")
					.col("folder").col("clientpath")
					.col("uri")
					.col("pname")
					.whereEq("pid", pid)
					.rs(st.instancontxt(conn, usr))
					.rs(0);

				if (rs.next()) {
					ISemantext stx = st.instancontxt(conn, usr);
					String pth = EnvPath.decodeUri(stx, rs.getString("uri"));
					Photo p = new Photo();
					Exif.parseExif(p, pth);
					// Utils.logi(p.exif);
					if (p.photoDate() != null) {
						Update u = st
							.update(tablPhotos, usr)
							.nv("folder", p.month())
							.nv("pdate", p.photoDate())
							.nv("uri", pth)
							.nv("pname", rs.getString("pname"))
							.nv("shareby", usr.uid())
							.nv("geox", p.geox).nv("geoy", p.geoy)
							.whereEq("pid", pid);
						if (!LangExt.isblank(p.mime))
							u.nv("mime", p.mime);
						u.u(stx);
					}
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
	 */
	protected static AlbumResp rec(AlbumReq req, IUser usr)
			throws SemanticException, TransException, SQLException {

		AnResultset rs = (AnResultset) st
				.select(tablPhotos, "p")
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
				.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr)).rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find file for id: %s (permission of %s)", req.docId, usr.uid());

		return new AlbumResp().rec(rs);
	}
	
	protected static AlbumResp collect(AlbumReq req, IUser usr)
			throws SemanticException, TransException, SQLException {

		String cid = req.collectId;
		AnResultset rs = (AnResultset) st
				.select(tablCollects)
				.whereEq("cid", cid)
				.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr))
				.rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find photo collection for id = '%s' (permission of %s)", cid, usr.uid());

		AlbumResp album = new AlbumResp().setCollects(rs);

		rs = (AnResultset) st
				.select(tablPhotos, "p")
				.j(tablCollectPhoto, "cp", "cp.pid = p.pid")
				.col("p.*")
				.whereEq("cp.cid", cid)
				.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr))
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
	 * @return album
	 * @throws SemanticException
	 * @throws TransException
	 * @throws SQLException
	 */
	protected static AlbumResp album(AlbumReq req, IUser usr)
			throws SemanticException, TransException, SQLException {

		String aid = req.albumId;
		if (isblank(aid))
			aid = ((PhotoRobot)usr).defaultAlbum();

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
				.select(tablPhotos, "p").page(req.page)
				.j(tablCollectPhoto , "ch", "ch.pid = p.pid")
				.j(tablAlbumCollect, "ac", "ac.cid = ch.cid")
				.j(tablCollects, "c", "c.cid = ch.cid")
				.j(tablAlbums, "a", "a.aid = ac.aid")
				.j(tablUser, "u", "u.userId = p.shareby")
				.cols("ac.aid", "ch.cid",
					  "p.pid", "pname", "pdate", "p.tags", "mime", "uri", "folder", "geox", "geoy", "sharedate",
					  "c.shareby collector", "c.cdate",
					  "device", "p.shareby ownerId", "u.userName owner",
					  "storage", "aname", "cname")
				.whereEq("a.aid", aid)
				.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr))
				.rs(0);

		album.collectPhotos(rs);

		return album;
	}
}
