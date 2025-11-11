/*
 * Create and modified by Ody Zhou,
 * 
 * Credits to <a href='https://github.com/omnifaces/omnifaces'>OmniFaces</a>, 
 * org.omnifaces.servlet.FileServlet.
 * Licensed under the Apache License, Version 2.0.
 * 
 */
package io.odysz.semantic.tier.docs;

import static io.odysz.common.AESHelper.stream206;
import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.ifnull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.split;
import static io.odysz.common.LangExt.prefixOneOf;
import static io.odysz.common.Utils.logT;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static io.odysz.semantic.jprotocol.JProtocol.Headers.AnsonReq;
import static io.odysz.semantic.jprotocol.JProtocol.Headers.Reason;
import static io.odysz.semantic.jprotocol.JProtocol.Headers.Error;
import static io.odysz.semantic.jprotocol.JProtocol.Headers.Server;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.Anson;
import io.odysz.common.AESHelper;
import io.odysz.common.LangExt;
import io.odysz.common.Regex;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.ShExtFilev2;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.meta.ExpDocTableMeta;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.ExchangeException;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.syn.DBSynTransBuilder;
import io.oz.syn.ExessionAct;

/**
 * <p>Helper class for handling http 206 request,
 * feeding response with ranged file content.</p>

 * @since 1.4.35
 */
public abstract class Docs206 {

	private static final String Disposition_Header = "%s;filename=\"%2$s\"; filename*=UTF-8''%2$s";
	private static final long Expire_1Hour = 3600L;
	private static final long Second_ms = TimeUnit.SECONDS.toMillis(1);
	private static final String ETAG = "W/\"%s-%s\"";
	private static final Pattern Regex_Range = Pattern.compile("^bytes=[0-9]*-[0-9]*(,[0-9]*-[0-9]*)*+$");
	private static final String Multipart_boundary = UUID.randomUUID().toString();

	/**
	 * Used for telling the client with request header:<br>
	 * "exchanging error:" + {@link ExessionAct#ext_docref} - 
	 * target file is a {@link io.odysz.semantic.meta.DocRef} object.
	 */
	public static final String reason_doc_ref = "exchanging error: " + ExessionAct.ext_docref;

	public static DATranscxt st;

	static {
		try {
			st = new DATranscxt(null);
		} catch ( Exception e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Verify Anson header in "?anson64=" and call
	 * {@link #replyHeaders(HttpServletRequest, HttpServletResponse, AnsonMsg, IUser)}
	 * to reply with a http header which is compatible / understandable to browsers. 
	 * 
	 * <p>Reference:</p>
	 * 
	 * [1] Problem: https://stackoverflow.com/a/52520120/7362888 <br>
	 * [2] HTTP 206: https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/206 <br>
	 * [3] Example: https://balusc.omnifaces.org/2009/02/fileservlet-supporting-resume-and.html <br>
	 * 
	 * @param req
	 * @param resp
	 * @return range headers
	 * @throws IOException
	 * @throws SsException
	 */
	public static List<Range> get206Head(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, SsException {
		AnsonMsg<DocsReq> msg = ansonMsg(req); 
		try {
			IUser usr = JSingleton.getSessionVerifier().verify(msg.header());
			return replyHeadersv2(req, resp, msg, usr);
		} catch (IOException e) {
			headerr(resp, msg, HttpServletResponse.SC_PRECONDITION_FAILED, e);
			return null;
		}
	}

	@SuppressWarnings("unchecked")
	protected static AnsonMsg<DocsReq> ansonMsg(HttpServletRequest req) throws IOException {
		InputStream in = null;
		try {
			String anson64 = req.getParameter("anson64");
			if (!LangExt.isEmpty(anson64)) {
				byte[] b = AESHelper.decode64(anson64);
				in = new ByteArrayInputStream(b);
			}
			else {
				if (req.getContentLength() == 0)
					return null;
				in = req.getInputStream();
			}
			return (AnsonMsg<DocsReq>) Anson.fromJson(in);
		} finally {
			if (in != null)
				in.close();
		}
	}
	
	/**
	 * @deprecated don't delete until verified with @anclient/anreact.
	 * @param req
	 * @param resp
	 * @throws IOException
	 * @throws SsException
	 */
 	public static void get206(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, SsException {
		try {
			AnsonMsg<DocsReq> msg = ansonMsg(req);
			IUser usr = JSingleton.getSessionVerifier().verify(msg.header());
			List<Range> ranges = replyHeaders(req, resp, msg, usr);
			Resource resource = new Resource(getDocByEid(req, msg.body(0), st, usr), msg.body(0).doc.recId);
			
			resp.setHeader(JProtocol.Headers.Length, String.valueOf(resource.length));
			writeContent(resp, resource, ranges, "");
		}
		catch (IllegalArgumentException e) {
			logT(new Object() {}, "IllegalArgumentException, reply 400 Bad Request. Message:\n%s",
				e.getMessage());
			resp.setHeader(JProtocol.Headers.Error, e.getMessage());
			resp.setHeader(JProtocol.Headers.Server, JSingleton.appName);
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
		} catch (TransException | SQLException e) {
			e.printStackTrace();
			resp.setHeader(JProtocol.Headers.Error, e.getMessage());
			resp.setHeader(JProtocol.Headers.Server, JSingleton.appName);
			resp.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
		}
	}
	
	public static void get206v2(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, SsException {
		AnsonMsg<DocsReq> msg = null;
		try {
			msg = ansonMsg(req);
			IUser usr = JSingleton.getSessionVerifier().verify(msg.header());
			List<Range> ranges = replyHeadersv2(req, resp, msg, usr);
			
			Resource resource = new Resource(isblank(msg.body(0).doc.uids) ?
										getDocByEid(req, msg.body(0), st, usr) :
										getDocByUid(req, msg.body(0), st, usr),
									msg.body(0).doc.recId);
			
			resp.setHeader(JProtocol.Headers.Length, String.valueOf(resource.length));
			writeContent(resp, resource, ranges, "");
		}
		catch (FileNotFoundException e) {
			logT(new Object() {}, "File is not found, reply SC_CONFLICT 409 Bad Request. Message:\n%s",
				e.getMessage());
			headerr(resp, msg, HttpServletResponse.SC_CONFLICT, e);
		}
		catch (SQLException e) {
			e.printStackTrace();
			headerr(resp, msg, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e);
		}
		catch (ExchangeException e) {
			// That's a doc-ref, nothing to download
			logT(new Object() {}, "ExchangeException, reply SC_CONFLICT 409 Bad Request. Message:\n%s",
				e.getMessage());
			headerr(resp, msg, HttpServletResponse.SC_CONFLICT, e);
		}
		catch (IllegalArgumentException e) {
			logT(new Object() {}, "IllegalArgumentException, reply SC_BAD_REQUEST400 Bad Request. Message:\n%s",
				e.getMessage());
			headerr(resp, msg, HttpServletResponse.SC_BAD_REQUEST, e);
		} catch (TransException e) {
			e.printStackTrace();
			headerr(resp, msg, HttpServletResponse.SC_BAD_REQUEST, e);
		}
	}
	
	private static void headerr(HttpServletResponse resp, AnsonMsg<DocsReq> msg, int respCode, Exception e) throws IOException {
		resp.setHeader(Error, e.getMessage());
		resp.setHeader(Server, JSingleton.appName);

		if (e instanceof ExchangeException
				&& ((ExchangeException) e).requires() == ExessionAct.ext_docref)
			resp.setHeader(Reason, reason_doc_ref);

		if (msg != null && msg.body(0) != null)
			resp.setHeader(AnsonReq, msg.body(0).toBlock());

		resp.sendError(respCode);
	}

	/**
	 * @deprecated Don't delete until the js client, @anclient/anreact, is verified.
	 * @param request
	 * @param response
	 * @param msg
	 * @param usr
	 * @return
	 * @throws IOException
	 * @throws TransException
	 * @throws SQLException
	 */
	public static List<Range> replyHeaders(HttpServletRequest request, HttpServletResponse response,
			AnsonMsg<DocsReq>msg, IUser usr) throws IOException, TransException, SQLException {
		response.reset();

		Resource resource;

		try {
			resource = new Resource(getDocByEid(request, msg.body(0), st, usr), msg.body(0).doc.recId);
		}
		catch (IllegalArgumentException e) {
			logT(new Object() {}, "IllegalArgumentException, reply 400 Bad Request. Message:\n%s",
				e.getMessage());
			response.setHeader("Error", e.getMessage());
			response.setHeader("Server", JSingleton.appName);
			response.sendError(HttpServletResponse.SC_BAD_REQUEST);
			return null;
		}
		
		if (preconditionFailed(request, resource)) {
			response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
			return null;
		}

		setCacheHeaders(response, resource);

		if (notModified(request, resource)) {
			response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
			return null;
		}

		List<Range> ranges = getRanges(request, resource);

		if (ranges == null) {
			response.setHeader(JProtocol.Headers.Content_range, "bytes */" + resource.length);
			response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
			return null;
		}

		if (!ranges.isEmpty()) {
			response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
		}
		else {
			ranges.add(new Range(0, resource.length - 1));
		}

		setContentHeaders(request, response, resource, ranges);
		return ranges;
	}

	public static List<Range> replyHeadersv2(HttpServletRequest request, HttpServletResponse response,
			AnsonMsg<DocsReq>msg, IUser usr) throws IOException {
		response.reset();

		Resource resource = null;

		try {
			resource = new Resource(isblank(msg.body(0).doc.uids)
					? getDocByEid(request, msg.body(0), st, usr)
					: getDocByUid(request, msg.body(0), st, usr),
					msg.body(0).doc.recId);
		}
		catch (SQLException e) {
			e.printStackTrace();
			headerr(response, msg, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e);
		}
		catch (IllegalArgumentException e) {
			logT(new Object() {}, "IllegalArgumentException, reply 400 Bad Request. Message:\n%s",
				e.getMessage());
			headerr(response, msg, HttpServletResponse.SC_BAD_REQUEST, e);
			return null;
		}
		catch (ExchangeException e) {
			// That's a doc-ref, nothing to download
			logT(new Object() {}, "ExchangeException, reply 409 Bad Request. Message:\n%s",
				e.getMessage());
			headerr(response, msg, HttpServletResponse.SC_CONFLICT, e);
			return null;
		}
		catch (TransException e) {
			e.printStackTrace();
			headerr(response, msg, HttpServletResponse.SC_BAD_REQUEST, e);
		}
	
		if (preconditionFailed(request, resource)) {
			response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
			return null;
		}

		setCacheHeaders(response, resource);

		if (notModified(request, resource)) {
			response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
			return null;
		}

		List<Range> ranges = getRanges(request, resource);

		if (ranges == null) {
			response.setHeader(JProtocol.Headers.Content_range, "bytes */" + resource.length);
			response.sendError(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
			return null;
		}

		if (!ranges.isEmpty()) {
			response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
		}
		else {
			ranges.add(new Range(0, resource.length - 1));
		}

		setContentHeaders(request, response, resource, ranges);
		return ranges;
	}

	protected static File getDocByEid(HttpServletRequest request, DocsReq req, DATranscxt st, IUser usr)
			throws TransException, SQLException, IOException {

		String conn = Connects.uri2conn(req.uri());
		// ExpDocTableMeta meta = getMeta.get(req.uri());
		if (req.doc == null || isblank(req.doc.recId) || isblank(req.docTabl))
			throw new IllegalArgumentException(f("File informoation is missing: doc: %s, table %s",
					req.doc == null ? null : req.doc.recId, req.docTabl));

		ExpDocTableMeta meta = (ExpDocTableMeta) Connects.getMeta(conn, req.docTabl);

		AnResultset rs = (AnResultset) st
				.select(meta.tbl, "p")
				.col(meta.pk)
				.col(meta.resname).col(meta.createDate)
				.col(meta.fullpath)
				.col(meta.uri)
				.col("mime")
				.whereEq(meta.pk, req.doc.recId)
				.rs(st.instancontxt(conn, usr)).rs(0);
		
		if (!rs.next())
			throw new SemanticException("File not found: %s, %s", req.doc.recId, req.doc.pname);

		// String p = DocUtils.resolvExtroot(st, conn, req.doc.recId, usr, meta);
		// FIXME to be refactored in branch docsync-refactor 
		String p = ShExtFilev2.resolvUri(conn, req.doc.recId, rs.getString(meta.uri), rs.getString(meta.resname), meta);

		File f = new File(p);
		if (f.exists() && f.isFile())
			return f;
		else throw new IOException("File not found: " + rs.getString(meta.fullpath));
	}

	/**
	 * Handling syntity's doc downloading, an extend and hard syn-docref function into lower layer
	 * than docsync.jserv, with semantics and metas from DBSyntaransBuilder.
	 * 
	 * @param request
	 * @param req
	 * @param st
	 * @param usr
	 * @return
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException
	 */
	protected static File getDocByUid(HttpServletRequest request, DocsReq req, DATranscxt st, IUser usr)
			throws TransException, SQLException, IOException {

		String conn = Connects.uri2conn(req.uri());
		if (req.doc == null || isblank(req.doc.uids) || isblank(req.docTabl))
			throw new IllegalArgumentException(f("File informoation is missing: uids: %s, table %s",
					req.doc == null ? null : req.doc.uids, req.docTabl));

		ExpDocTableMeta meta = (ExpDocTableMeta) DBSynTransBuilder.getEntityMeta(conn, req.docTabl);

		AnResultset rs = (AnResultset) st
				.select(meta.tbl, "p")
				.col(meta.pk)
				.col(meta.resname).col(meta.createDate)
				.col(meta.fullpath)
				.col(meta.uri)
				.col(meta.mime)
				.whereEq(meta.io_oz_synuid, req.doc.uids)
				.rs(st.instancontxt(conn, usr)).rs(0);
		
		if (!rs.next())
			throw new SemanticException("File not found: %s, %s", req.doc.recId, req.doc.pname);
		
		if (Regex.startsEvelope(rs.getString(meta.uri)))
			throw new ExchangeException(ExessionAct.ext_docref, null, "DocRef: %s, %s, %s", req.doc.uids, req.doc.recId, req.doc.pname);

		String p = ShExtFilev2.resolvUri(conn, rs.getString(meta.pk), rs.getString(meta.uri), rs.getString(meta.resname), meta);
		File f = new File(p);
		if (f.exists() && f.isFile())
			return f;
		else throw new FileNotFoundException("File not found: " + rs.getString(meta.fullpath));
	}

	/**
	 * Handles the case when the file is not found.
	 * <p>
	 * The default implementation sends a HTTP 404 error.
	 * @param request The involved HTTP servlet request.
	 * @param response The involved HTTP servlet response.
	 * @throws IOException When something fails at I/O level.
	 * @since 0.1.50
	 */
	protected void handleFileNotFound(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		response.sendError(HttpServletResponse.SC_NOT_FOUND);
	}

	/**
	 * Returns the content type associated with the given HTTP servlet request and file.
	 * If null, fallback to * <code>application/octet-stream</code>.
	 * @param request The involved HTTP servlet request.
	 * @param file The involved file.
	 * @return The content type associated with the given HTTP servlet request and file.
	 */
	static String getContentType(HttpServletRequest request, File file) {
		return ifnull(request.getServletContext().getMimeType(file.getName()), "application/octet-stream");
	}

	static boolean isAttachment(HttpServletRequest request, String contentType) {
		String accept = request.getHeader("Accept");
		return !prefixOneOf(contentType, "text", "image") && (accept == null || !accepts(accept, contentType));
	}

	/**
	 * Returns true (not matched or older than modified) if it's a conditional request which must return 412.
	 */
	private static boolean preconditionFailed(HttpServletRequest request, Resource resource) {
		String match = request.getHeader("If-Match");
		long unmodified = request.getDateHeader("If-Unmodified-Since");
		return (match != null)
				? !matches(match, resource.eTag)
				: (unmodified != -1 && modified(unmodified, resource.lastModified));
	}

	/**
	 * Set cache headers.
	 */
	static void setCacheHeaders(HttpServletResponse response, Resource resource) {
		setCacheHeaders(response, Expire_1Hour);
		response.setHeader("ETag", resource.eTag);
		response.setDateHeader("Last-Modified", resource.lastModified);
	}

	/**
	 * <p>Set the cache headers. If the <code>expires</code> argument is larger than 0 seconds,
	 * then the following headers will be set:
	 * <ul>
	 * <li><code>Cache-Control: public,max-age=[expiration time in seconds],must-revalidate</code></li>
	 * <li><code>Expires: [expiration date of now plus expiration time in seconds]</code></li>
	 * </ul>
	 * <p>Else the method will delegate to {@link #setNoCacheHeaders(HttpServletResponse)}.
	 * @param response The HTTP servlet response to set the headers on.
	 * @param expires The expire time in seconds (not milliseconds!).
	 * @since 0.1.50
	 */
	static void setCacheHeaders(HttpServletResponse response, long expires) {
		if (expires > 0) {
			response.setHeader(JProtocol.Headers.Cache_control, "public,max-age=" + expires + ",must-revalidate");
			response.setDateHeader(JProtocol.Headers.Expires, System.currentTimeMillis() + SECONDS.toMillis(expires));
		}
		else {
			// setNoCacheHeaders(response);
			response.setHeader(JProtocol.Headers.Cache_control, "no-cache,no-store,must-revalidate");
			response.setDateHeader(JProtocol.Headers.Expires, 0);
		}

		// Explicitly set pragma to prevent container from overriding it.
		response.setHeader(JProtocol.Headers.Pragma, "");
	}

	/**
	 * Returns true if it's a conditional request which must return 304.
	 */
	static boolean notModified(HttpServletRequest request, Resource resource) {
		String noMatch = request.getHeader(JProtocol.Headers.If_none_match);
		long modified = request.getDateHeader(JProtocol.Headers.If_modified_since);
		return (noMatch != null)
			? matches(noMatch, resource.eTag)
			: (modified != -1 && !modified(modified, resource.lastModified));
	}

	/**
	 * <p>Get requested ranges.</p>
	 * 
	 * If this is null, then we must return 416;<br>
	 * if this is empty, then we must return full file.
	 */
	static List<Range> getRanges(HttpServletRequest request, Resource resource) {
		List<Range> ranges = new ArrayList<>(1);
		String rangeHeader = request.getHeader(JProtocol.Headers.Range);

		if (rangeHeader == null) {
			return ranges;
		}
		else if (!Regex_Range.matcher(rangeHeader).matches()) {
			return null;
		}

		String ifRange = request.getHeader(JProtocol.Headers.If_range);

		if (ifRange != null && !ifRange.equals(resource.eTag)) {
			try {
				long ifRangeTime = request.getDateHeader(JProtocol.Headers.If_range);

				if (ifRangeTime != -1 && modified(ifRangeTime, resource.lastModified)) {
					return ranges;
				}
			}
			catch (IllegalArgumentException ifRangeHeaderIsInvalid) {
				logT(new Object() {}, "If-Range header is invalid. Return full file then.\n%s",
					 ifRangeHeaderIsInvalid);
				return ranges;
			}
		}

		for (String rangeHeaderPart : rangeHeader.split("=")[1].split(",")) {
			Range range = parseRange(rangeHeaderPart, resource.length);

			if (range == null) {
				return null; // Logic error.
			}

			ranges.add(range);
		}

		return ranges;
	}

	/**
	 * Parse range header part. Returns null if there's a logic error (i.e. start after end).
	 */
	private static Range parseRange(String range, long length) {
		long start = sublong(range, 0, range.indexOf('-'));
		long end = sublong(range, range.indexOf('-') + 1, range.length());

		if (start == -1) {
			start = length - end;
			end = length - 1;
		}
		else if (end == -1 || end > length - 1) {
			end = length - 1;
		}

		if (start > end) {
			return null;
		}

		return new Range(start, end);
	}

	/**
	 * Returns a substring of the given string value from the given begin index to the given end index as a long.
	 * If the substring is empty, then -1 will be returned.
	 */
	private static long sublong(String value, int beginIndex, int endIndex) {
		String substring = value.substring(beginIndex, endIndex);
		return substring.isEmpty() ? -1 : Long.parseLong(substring);
	}

	/**
	 * Set content headers.
	 * @throws UnsupportedEncodingException 
	 */
	private static String setContentHeaders(HttpServletRequest request, HttpServletResponse response, Resource resource, List<Range> ranges)
			throws UnsupportedEncodingException {
		String contentType = getContentType(request, resource.file);
		String filename = resource.file.getName();
		boolean attachment = isAttachment(request, contentType);
		response.setHeader("Content-Disposition", dispositionHeader(filename, attachment));
		response.setHeader("Accept-Ranges", "bytes");

		if (ranges.size() == 1) {
			Range range = ranges.get(0);
			response.setContentType(contentType);
			response.setHeader(JProtocol.Headers.Content_length, String.valueOf(range.length));

			if (response.getStatus() == HttpServletResponse.SC_PARTIAL_CONTENT) {
				response.setHeader(JProtocol.Headers.Content_range, "bytes " + range.start + "-" + range.end + "/" + resource.length);
			}
		}
		else {
			response.setContentType("multipart/byteranges; boundary=" + Multipart_boundary);
		}

		return contentType;
	}

	/**
	 * Write given file to response with given content type and ranges.
	 */
	public static void writeContent(HttpServletResponse response, Resource resource, List<Range> ranges, String contentType) throws IOException {
		ServletOutputStream output = response.getOutputStream();
		if (ranges == null) {
			stream206(resource.file, output, 0, Math.max(resource.length - 1, 0));
		}
		else if (ranges.size() == 1) {
			Range range = ranges.get(0);
			stream206(resource.file, output, range.start, range.length);
		}
		else {
			for (Range range : ranges) {
				output.println();
				output.println("--" + Multipart_boundary);
				output.println("Content-Type: " + contentType);
				output.println("Content-Range: bytes " + range.start + "-" + range.end + "/" + resource.length);
				stream206(resource.file, output, range.start, range.length);
			}

			output.println();
			output.println("--" + Multipart_boundary + "--");
		}
	}

//	public static long stream(File file, OutputStream output, long start, long length) throws IOException {
//		if (start == 0 && length >= file.length()) {
//			try (ReadableByteChannel inputChannel = Channels.newChannel(new FileInputStream(file));
//					WritableByteChannel outputChannel = Channels.newChannel(output)) {
//				ByteBuffer buffer = ByteBuffer.allocateDirect(Range_Size);
//				long size = 0;
//
//				while (inputChannel.read(buffer) != -1) {
//					buffer.flip();
//					size += outputChannel.write(buffer);
//					buffer.clear();
//				}
//
//				return size;
//			}
//		}
//		else {
//			try (FileChannel fileChannel = (FileChannel) Files.newByteChannel(file.toPath(), StandardOpenOption.READ)) {
//				WritableByteChannel outputChannel = Channels.newChannel(output);
//				ByteBuffer buffer = ByteBuffer.allocateDirect(Range_Size);
//				long size = 0;
//
//				while (fileChannel.read(buffer, start + size) != -1) {
//					buffer.flip();
//
//					if (size + buffer.limit() > length) {
//						buffer.limit((int) (length - size));
//					}
//
//					size += outputChannel.write(buffer);
//
//					if (size >= length) break;
//
//					buffer.clear();
//				}
//
//				return size;
//			}
//		}
//	}

	public static String dispositionHeader(String filename, boolean attachment)
			throws UnsupportedEncodingException {
		return format(Disposition_Header,
				attachment ? "attachment" : "inline",
				isblank(filename) ? null : URLEncoder
					.encode(filename, UTF_8.name())
					.replace("+", "%20")
					.replace("*", "%2A")
					.replace("%7E", "~"));
	}

	/**
	 * Returns true if the given match header matches the given ETag value.
	 */
	static boolean matches(String matchHeader, String eTag) {
		String[] matchValues = matchHeader.split("\\s*,\\s*");
		Arrays.sort(matchValues);
		return Arrays.binarySearch(matchValues, eTag) > -1
			|| Arrays.binarySearch(matchValues, "*") > -1;
	}

	/**
	 * Returns true if the given modified header is older than the given last modified value.
	 */
	static boolean modified(long modifiedHeader, long lastModified) {
		// That second is because the header is in seconds, not millis.
		return (modifiedHeader + Second_ms <= lastModified);
	}

	/**
	 * Returns true if the given accept header accepts the given value.
	 */
	static boolean accepts(String acceptHeader, String toAccept) {
		String[] acceptValues = split(acceptHeader, "\\s*[,;]\\s*");
		Arrays.sort(acceptValues);
		return Arrays.binarySearch(acceptValues, toAccept) > -1
			|| Arrays.binarySearch(acceptValues, toAccept.replaceAll("/.*$", "/*")) > -1
			|| Arrays.binarySearch(acceptValues, "*/*") > -1;
	}

	/**
	 * File Resource.
	 */
	static class Resource {
		final File file;
		final long length;
		final long lastModified;
		final String eTag;

		public Resource(File file, String docId) throws UnsupportedEncodingException {
			if (file != null && file.isFile()) {
				this.file = file;
				length = file.length();
				lastModified = file.lastModified();
				eTag = format(ETAG, docId, lastModified);
			}
			else {
				this.file = null;
				length = 0;
				lastModified = 0;
				eTag = null;
			}
		}
	}

	static class Range extends Anson {
		long start;
		long end;
		long length;
		
		public Range() {
			super();
		}
		
		public Range(long start, long end) {
			this.start = start;
			this.end = end;
			length = end - start + 1;
		}
	}
}