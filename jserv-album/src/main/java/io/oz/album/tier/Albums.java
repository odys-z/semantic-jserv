package io.oz.album.tier;

import java.io.IOException;
import java.sql.SQLException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.tier.docs.FileStream;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.album.AlbumFlags;
import io.oz.album.AlbumPort;
import io.oz.album.PhotoRobot;
import io.oz.album.tier.AlbumReq.A;
import io.oz.album.tier.AlbumReq.fileState;

/**Manage album
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

			// IUser usr = AlbumSingleton.getSessionVerifier().verify(jmsg.header());
			IUser usr = robot;

			if (A.records.equals(a)) // load
				rsp = album(jmsg.body(0), usr);
			else if (A.collect.equals(a))
				rsp = collect(jmsg.body(0), usr);
			else if (A.rec.equals(a))
				rsp = rec(jmsg.body(0), usr);
			else if (A.insert.equals(a))
				rsp = create(jmsg.body(0), usr);
			else if (A.upload.equals(a))
				upload(resp, jmsg.body(0), usr);
			else if (A.download.equals(a))
				download(resp, jmsg.body(0), usr);
			else
				throw new SemanticException(
						"request.body.a can not handled: %s\\n" +
						"Only a = [%s, %s, %s, %s, %s, %s, %s, %s] are supported.",
						jreq.a(), A.records, A.collect, A.rec, A.insert,
								  A.update, A.download, A.upload, A.del );
			write(resp, ok(rsp));
		} catch (SemanticException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			if (AlbumFlags.album)
				e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	private void download(HttpServletResponse resp, AlbumReq freq, IUser usr)
			throws IOException, SemanticException, TransException, SQLException {
		AnResultset rs = (AnResultset) st
			.select(tablPhotos)
			.col("uri")
			.whereEq("pid", freq.fileId)
			.rs(st.instancontxt(Connects.uri2conn(uri), usr))
			.rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find file for id: %s (permission of %s)", freq.fileId, usr.uid());
	
		FileStream.sendFile(resp.getOutputStream(), rs.getString("uri"));
	}

	private AlbumResp create(AlbumReq body, IUser usr) {
		return null;
	}

	private AlbumResp upload(HttpServletResponse resp, AlbumReq body, IUser usr) {
		return null;
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
		String fileId = req.fileId;
		AnResultset rs = (AnResultset) st.select(tablPhotos)
			.whereEq("pid", fileId)
			.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr))
			.rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find file for id: %s (permission of %s)", fileId, usr.uid());

		return new AlbumResp().rec(rs);
	}

	protected static AlbumResp collect(AlbumReq req, IUser usr) throws SemanticException, TransException, SQLException {
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

	protected static AlbumResp album(AlbumReq req, IUser usr) throws SemanticException, TransException, SQLException {
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
