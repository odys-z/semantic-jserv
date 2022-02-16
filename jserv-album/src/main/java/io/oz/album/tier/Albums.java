package io.oz.album.tier;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.EnvPath;
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
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.FileStream;
import io.odysz.semantics.ISemantext;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.parts.condition.Funcall;
import io.odysz.transact.x.TransException;
import io.oz.album.AlbumFlags;
import io.oz.album.AlbumPort;
import io.oz.album.PhotoRobot;
import io.oz.album.tier.AlbumReq.A;
import io.oz.album.tier.AlbumReq.fileState;

/**<h5>The album tier</h5>
 * Although this tie is using the pattern of <i>less</i>, it's also verifying user when uploading - for subfolder name of user
 * 
 * <h5>Design note for version 0.1</h5>
 * <p>A photo always have a default collection Id.</p>
 * The clients collect image files etc., create photo records and upload ({@link AlbumReq.A.insertPhoto A.insertPhoto})
 * - without collection Id; <br>
 * the browsing clients are supported with a default collection: home/usr/month.
 * 
 * @author ody
 *
 */
@WebServlet(description = "Album tier: albums", urlPatterns = { "/album.less" })
public class Albums extends ServPort<AlbumReq> {

	private static final long serialVersionUID = 1L;

	/** db photo table */
	static final String tablPhotos = "h_photos";
	/** db photo table */
	static final String tablAlbums = "h_albums";
	/** db collection table */
	static final String tablCollects = "h_collects";

	static final String tablCollectPhoto = "h_coll_phot";

	/** uri db field */
	static final String uri = "uri";
	/** file state db field */
	static final String state = "state";


	fileState fileState;

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
	}

	@Override
	protected void onPost(AnsonMsg<AlbumReq> jmsg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {

		if (AlbumFlags.album)
			Utils.logi("========== ever-connect /album.less POST ==========");

		try {
			AlbumReq jreq = jmsg.body(0);
			String a = jreq.a();
			AlbumResp rsp = null;

			IUser usr = robot;

			if (A.upload.equals(a)) {
				usr = JSingleton.getSessionVerifier().verify(jmsg.header());
				upload(resp, jmsg.body(0), usr);
			}
			else if (A.download.equals(a))
				download(resp.getOutputStream(), jmsg.body(0), usr);
			else {
				if (A.records.equals(a)) // load
					rsp = album(jmsg.body(0), usr);
				else if (A.collect.equals(a))
					rsp = collect(jmsg.body(0), usr);
				else if (A.rec.equals(a))
					rsp = rec(jmsg.body(0), usr);
				else if (A.insertPhoto.equals(a)) {
					usr = JSingleton.getSessionVerifier().verify(jmsg.header());
					rsp = createPhoto(jmsg.body(0), usr);
				}
				else if (A.selectSyncs.equals(a)) {
					usr = JSingleton.getSessionVerifier().verify(jmsg.header());
					rsp = selectSyncs(jmsg.body(0), usr);
				}
				else
					throw new SemanticException(
							"request.body.a can not handled: %s\\n" +
							"Only a = [%s, %s, %s, %s, %s, %s, %s, %s] are supported.",
							jreq.a(), A.records, A.collect, A.rec, A.insertPhoto,
									  A.update, A.download, A.upload, A.del );
				write(resp, ok(rsp));
			}
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			if (AlbumFlags.album)
				e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	AlbumResp selectSyncs(AlbumReq req, IUser usr) throws SemanticException, TransException, SQLException {
		String[] pids = new String[req.syncQueries.size()];
		ArrayList<String[]> orders = new ArrayList<String[]>(req.syncQueries.size());
		for (SyncRec s : req.syncQueries)
			orders.add(new String[] {String.format("pid = '%s'", s.fullpath())});

		AnResultset rs = (AnResultset) st.select(tablCollects)
			.whereIn("pid", pids)
			.whereEq("device", req.device)
			.orderby(orders)
			.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr))
			.rs(0);

		AlbumResp album = new AlbumResp().collects(rs);

		album.photos("sync-temp-id", rs);

		return album;
	}

	void download(OutputStream ofs, DocsReq freq, IUser usr)
			throws IOException, SemanticException, TransException, SQLException {
		String conn = Connects.uri2conn(uri);
		ISemantext stx = st.instancontxt(conn, usr);
		AnResultset rs = (AnResultset) st
			.select(tablPhotos)
			.col("uri").col("folder")
			.whereEq("pid", freq.docId)
			.rs(stx)
			.rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find file for id: %s (permission of %s)",
					freq.docId, usr.uid());
	
		// keep file system root the same with semantics configuration 
		String extroot = ((ShExtFile) DATranscxt
				.getHandler(conn, tablPhotos, smtype.extFile))
				.getFileRoot();
		FileStream.sendFile(ofs, EnvPath.decodeUri(extroot, rs.getString("uri")));
	}

	AlbumResp createPhoto(AlbumReq req, IUser usr) throws TransException, SQLException, IOException {
		String conn = Connects.uri2conn(uri);

		Insert ins = st
				.insert(tablPhotos, usr)
				.nv("uri", req.photo.uri)
				.nv("pname", req.photo.pname)
				.nv("pdate", req.photo.photoDate())
				.nv("device", ((PhotoRobot)usr).deviceId())
				.nv("shareby", usr.uid())
				.nv("folder", req.photo.month())
				.nv("sharedate", Funcall.now());
		
		if (req.photo.collectId == null)
			// create a default collection - uid/month/file.ext
			// This can not been supported by db semantics because it's business required for complex handling
			req.photo.collectId = getMonthCollection(conn, req.photo, usr);

		ins.post( st.insert(tablCollectPhoto)
					// pid is resulved
					.nv("cid", req.photo.collectId) );

		SemanticObject res = (SemanticObject) ins
				.ins(st.instancontxt(conn, usr));

		String pid = ((SemanticObject) ((SemanticObject)res.get("resulved")).get("h_photos")).getString("pid");
		return new AlbumResp().photo(req.photo, pid);
	}
	
	/**map uid/month -> collect-id
	 * @param photo
	 * @param usr
	 * @return collect id
	 * @throws IOException 
	 * @throws SQLException 
	 * @throws TransException 
	 */
	private String getMonthCollection(String conn, Photo photo, IUser usr) throws IOException, TransException, SQLException {
		// TODO hit collection LRU
		AnResultset rs = (AnResultset) st.select(tablCollects, "c")
				.whereEq("yyyy_mm", photo.month())
				.whereEq("shareby", usr.uid())
				.rs(st.instancontxt(conn, usr))
				.rs(0);
		String cid = null;
		if (rs.next())
			cid = rs.getString("cid");
		else {
			ISemantext s1 = st.instancontxt(conn, usr);
			st.insert(tablCollects, usr)
			  .nv("yyyy_mm", photo.month())
			  .nv("shareby", usr.uid())
			  .nv("cname", photo.month())
			  .nv("cdate", Funcall.now())
			  .ins(s1);
			// cid = res.resulve(tablCollects, "cid");
			cid = (String) s1.resulvedVal(tablCollects, "cid");
			System.err.println("resulved cid: " + cid);
		}
		return cid;
	}

	/**@deprecate
	 * @param resp
	 * @param body
	 * @param usr
	 * @return
	 * @throws AnsonException
	 */
	AlbumResp upload(HttpServletResponse resp, AlbumReq body, IUser usr) throws AnsonException {
		throw new AnsonException(0, "Needing antson support stream mode ...");
	}

	/**Read a media file record (id, uri), TODO touch LRU.
	 * @param req
	 * @param usr
	 * @return loaded media record
	 * @throws SQLException 
	 * @throws TransException 
	 * @throws SemanticException 
	 */
	protected static AlbumResp rec(AlbumReq req, IUser usr)
			throws SemanticException, TransException, SQLException {
		String fileId = req.docId;
		AnResultset rs = (AnResultset) st.select(tablPhotos)
			.whereEq("pid", fileId)
			.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr))
			.rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find file for id: %s (permission of %s)", fileId, usr.uid());

		return new AlbumResp().rec(rs);
	}

	protected static AlbumResp collect(AlbumReq req, IUser usr)
			throws SemanticException, TransException, SQLException {
		String cid = req.collectId;
		AnResultset rs = (AnResultset) st.select(tablCollects)
			.whereEq("cid", cid)
			.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr))
			.rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find photo collection for id = %s (permission of %s)", cid, usr.uid());

		AlbumResp album = new AlbumResp().collects(rs);

		rs = (AnResultset) st.select(tablPhotos, "p")
			.col("p.*")
			.j(tablCollectPhoto, "cp", "cp.pid = p.pid")
			.whereEq("cp.cid", cid)
			.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr))
			.rs(0);

		album.photos(cid, rs);

		return album;
	}

	protected static AlbumResp album(AlbumReq req, IUser usr)
			throws SemanticException, TransException, SQLException {
		String aid = req.albumId;
		AnResultset rs = (AnResultset) st.select(tablPhotos)
			.whereEq("aid", aid)
			.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr))
			.rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find album of id = %s (permission of %s)", aid, usr.uid());

		AlbumResp album = new AlbumResp().album(rs);

		rs = (AnResultset) st.select(tablCollects)
			.whereEq("aid", aid)
			.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr))
			.rs(0);

		album.collects(rs);

		return album;
	}

}
