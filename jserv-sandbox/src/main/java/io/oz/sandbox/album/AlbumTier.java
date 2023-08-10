package io.oz.sandbox.album;

import static io.odysz.common.LangExt.eq;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.isblank;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.SQLException;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DatasetHelper;
import io.odysz.semantic.ext.AnDatasetResp;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.tier.docs.DocUtils;
import io.odysz.semantic.tier.docs.Docs206;
import io.odysz.semantic.tier.docs.FileStream;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.PageInf;
import io.odysz.transact.x.TransException;
import io.oz.album.tier.AlbumReq;
import io.oz.album.tier.AlbumReq.A;
import io.oz.sandbox.protocol.Sandport;
import io.oz.sandbox.utils.SandFlags;

@WebServlet(description = "Semantic sessionless: Album", urlPatterns = { "/album.less" })
public class AlbumTier extends ServPort<AlbumReq> {
	private static final long serialVersionUID = 1L;

	public class PhotoMeta extends DocTableMeta {

		public final String exif;
		public final String folder;
		public final String family;

		public PhotoMeta(String conn) throws TransException {
			super("h_photos", "pid", conn);

			exif = "exif";
			folder = "folder";
			family = "family";
		}
	}

	static DATranscxt st;
	static JRobot robot;

	String missingFile;

	public AlbumTier() throws SemanticException, SQLException, SAXException, IOException {
		super(Sandport.album);

		st = new DATranscxt(null);
		robot = new JRobot();

		missingFile = "";
		
		
		Docs206.getMeta = (String uri) -> {
			try { return new PhotoMeta(Connects.uri2conn(uri)); }
			catch (TransException e) {
				e.printStackTrace();
				return null;
		} };
	}

	@Override
	protected void doHead(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		super.doHead(request, response);
//    	String range = request.getHeader("Range");
//
//    	if (!isblank(range))
//			try {
//				Docs206.get206Head(request, response);
//			} catch (SsException e) {
//				
//			}
//		else super.doHead(request, response);
	}

	/**
	 * Chrome request header for MP4
	 * <pre>
	Accept: * / *
	Accept-Encoding: identity;q=1, *;q=0
	Accept-Language: en-US,en;q=0.9,zh-CN;q=0.8,zh-TW;q=0.7,zh;q=0.6
	Connection: keep-alive
	Host: localhost:8081
	Range: bytes=0-
	Referer: http://localhost:8889/
	Sec-Fetch-Dest: video
	Sec-Fetch-Mode: no-cors
	Sec-Fetch-Site: same-site
	User-Agent: Mozilla/5.0 ...
	sec-ch-ua: "Not/A)Brand";v="99", "Google Chrome";v="115", "Chromium";v="115"
	sec-ch-ua-mobile: ?1
	sec-ch-ua-platform: "Android"
		</pre>
	 *
	 * Chrome request header for MP3<pre>
	 * 
	Accept-Encoding:
	identity;q=1, *;q=0
	Range:
	bytes=0-
	Referer: http://localhost:8889/
	Sec-Ch-Ua: "Not/A)Brand";v="99", "Google Chrome";v="115", "Chromium";v="115"
	Sec-Ch-Ua-Mobile: ?1
	Sec-Ch-Ua-Platform: "Android"
	User-Agent: Mozilla/5.0 ...
	 </pre>
	 */
//	@Override
//    protected void doGet(HttpServletRequest request, HttpServletResponse response)
//            throws ServletException, IOException {
//    	String range = request.getHeader("Range");
//    	if (!isblank(range)) {
//    		// FileServlet.download206(request, response);
//
//    		Docs206.get206(request, response, robot);
//    	}
//    	else super.doGet(request, response);
//    }

	@Override
	protected void onGet(AnsonMsg<AlbumReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		if (SandFlags.album)
			Utils.logAnson(msg);

		try {
			AlbumReq jreq = msg.body(0);
			String a = jreq.a();
			if (A.download.equals(a))
				download(resp, msg.body(0), robot);
			else
				throw new SemanticException("Request GET (request.body.a = %s) can not be handled", jreq.a());
		} catch (SemanticException e) {
			if (SandFlags.album)
				e.printStackTrace();
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (SQLException | TransException e) {
			if (SandFlags.album)
				e.printStackTrace();
			write(resp, err(MsgCode.exTransct, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	protected void download(HttpServletResponse resp, AlbumReq req, JRobot usr)
			throws IOException, SemanticException, TransException, SQLException {

		String conn = Connects.uri2conn(req.uri());
		PhotoMeta meta = new PhotoMeta(conn);

		AnResultset rs = (AnResultset) st
				.select(meta.tbl, "p")
				.col("pid")
				.col("folder")
				.col("clientpath")
				.col("uri")
				.col("mime")
				.whereEq("pid", req.docId)
				.rs(st.instancontxt(conn, usr)).rs(0);

		if (!rs.next()) {
			Utils.warn("[AlbumTier#download] Upgrade download 206: %s", missingFile);
			resp.setContentType("image/png");
			FileStream.sendFile(resp.getOutputStream(), missingFile);
		}
		else {
			String mime = rs.getString("mime");
			resp.setContentType(mime);
			String p = DocUtils.resolvExtroot(conn, rs.getString("uri"), meta);
			if (SandFlags.album)
				Utils.logi(p);
			try (OutputStream os = resp.getOutputStream()) {
				Utils.warn("[AlbumTier#download] Upgrade download 206: %s", p);
				FileStream.sendFile(os, p);
			} catch (FileNotFoundException e) {
				Utils.warn("File not found: %s", e.getMessage());
			} catch (IOException e) {
				// If the user dosen't play a video, Chrome will close the connection before finishing downloading.
				// This is harmless: https://stackoverflow.com/a/70020526/7362888
				Utils.warn(e.getMessage());
			}
		}
	}

	@Override
	protected void onPost(AnsonMsg<AlbumReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		AlbumReq jreq = msg.body(0);

		try {
			AnDatasetResp rsp = null;
			IUser usr = verifier.verify(msg.header());
			if (A.insertPhoto.equals(jreq.a()))
				rsp = insert(jreq);
			else if (A.update.equals(jreq.a()))
				rsp = update(jreq);
			else if ((A.stree).equals(jreq.a()))
				rsp = albumtree(jreq, usr);
			else if (A.album.equals(jreq.a()))
				rsp = records(jreq, usr);
			else
				throw new SemanticException("Request (request.body.a = %s) can not be handled", jreq.a());

			write(resp, ok(rsp));
		} catch (SsException e) {
			write(resp, err(MsgCode.exSession, e.getMessage()));
		} catch (TransException | SQLException e) {
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} finally {
			resp.flushBuffer();
		}
	}

	private AnDatasetResp insert(AlbumReq jreq) throws TransException, SQLException {
		// TODO Auto-generated method stub
		return null;
	}

	private AnDatasetResp update(AlbumReq jreq) {
		// TODO Auto-generated method stub
		return null;
	}

	protected AnDatasetResp records(AlbumReq jreq, IUser usr) {
		return new AnDatasetResp();
	}

	protected AnDatasetResp albumtree(AlbumReq jreq, IUser usr)
			throws SQLException, TransException {
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
		return (AnDatasetResp) new AnDatasetResp().forest(lst);
	}
}
