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
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.album.AlbumFlags;
import io.oz.album.AlbumPort;
import io.oz.album.AlbumSingleton;
import io.oz.album.tier.AlbumReq.A;

/**Manage album
 * 
 * @author ody
 *
 */
@WebServlet(description = "Album tier: albums", urlPatterns = { "/album.less" })
public class Albums extends ServPort<AlbumReq> {

	private static final long serialVersionUID = 1L;

	/** db table */
	static final String tabl = "h_photos";
	/** uri db field */
	static final String uri = "uri";
	/** file state db field */
	static final String state = "state";

	/** media file state */
	static enum S {
		uploading("uploading"),
		valid("valid"),
		synchronizing("synching"),
		archive("archive"),
		shared("shared");

		private String state;

		S(String state) {
			this.state = state;
		}

		public String s() {
			return this.state;
		}
	}

	protected static DATranscxt st;


	static {
		try {
			st = new DATranscxt(null);
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

			IUser usr = AlbumSingleton.getSessionVerifier().verify(jmsg.header());

			if (A.records.equals(a)) // load
				rsp = album(jmsg.body(0), usr);
			else if (A.collect.equals(a))
				rsp = photos(jmsg.body(0), usr);
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
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	private AlbumResp download(HttpServletResponse resp, AlbumReq body, IUser usr) {
		return null;
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
		AnResultset rs = (AnResultset) st.select(tabl)
			.col("uri")
			.whereEq("id", fileId)
			.rs(st.instancontxt(Connects.uri2conn(req.uri()), usr))
			.rs(0);

		if (!rs.next())
			throw new SemanticException("Can't find file for id: %s (permission of %s)", fileId, usr.uid());

		return new AlbumResp(rs);
	}

	private AlbumResp photos(AlbumReq body, IUser usr) {
		return null;
	}

	private AlbumResp album(AlbumReq body, IUser usr) {
		return null;
	}

}
