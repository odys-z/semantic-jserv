/*
 * Create and modified by Ody Zhou,
 * 
 * Credits to <a href='https://github.com/omnifaces/omnifaces'>OmniFaces</a>, 
 * org.omnifaces.servlet.FileServlet.
 * Licensed under the Apache License, Version 2.0.
 * 
 */
package io.odysz.semantic.tier.docs;

import static io.odysz.common.LangExt.ifnull;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.common.LangExt.split;
import static io.odysz.common.LangExt.startsOneOf;
import static io.odysz.common.Utils.logi;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.FINE;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
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

import org.xml.sax.SAXException;

import io.odysz.anson.Anson;
import io.odysz.common.AESHelper;
import io.odysz.common.LangExt;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

/**
 * <p>Feed response with ranged file content.</p>

 * @since 0.1.50
 */
public abstract class Docs206 {

	/**
	 * Caller of {@link Docs206#get206(HttpServletRequest, HttpServletResponse, IUser) uses this 
	 * to provide doc table meta. 
	 * 
	 * @author Ody
	 */
	@FunctionalInterface
	public interface IDocMeta {
		/**
		 * example:
		 * <pre>
		(uri) -> new PhotoMeta(Connects.uri2conn(uri))
		 * </pre>
		 * @param uri
		 * @return
		 */
		DocTableMeta get(String uri);
	}

	private static final String Disposition_Header = "%s;filename=\"%2$s\"; filename*=UTF-8''%2$s";
	private static final long Expire_1Hour = 3600L;
	private static final long Second_ms = TimeUnit.SECONDS.toMillis(1);
	private static final String ETAG = "W/\"%s-%s\"";
	private static final Pattern Regex_Range = Pattern.compile("^bytes=[0-9]*-[0-9]*(,[0-9]*-[0-9]*)*+$");
	private static final String Multipart_boundary = UUID.randomUUID().toString();
	private static final int Range_Size = 1024 * 8;

	public static DATranscxt st;

	public static IDocMeta getMeta;

	static {
		try {
			st = new DATranscxt(null);
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
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
			return replyHeaders(req, resp, msg, usr);
		} catch (IOException | TransException | SQLException e) {
			e.printStackTrace();
			resp.sendError(HttpServletResponse.SC_PRECONDITION_FAILED); // right semantics?
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

	public static void get206(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, SsException {
		try {
			AnsonMsg<DocsReq> msg = ansonMsg(req);
			IUser usr = JSingleton.getSessionVerifier().verify(msg.header());
			List<Range> ranges = replyHeaders(req, resp, msg, usr);
			Resource resource = new Resource(getDoc(req, msg.body(0), st, usr), msg.body(0).docId);
			writeContent(resp, resource, ranges, "");
		}
		catch (IllegalArgumentException e) {
			logi("%s Got an IllegalArgumentException from user code; interpreting it as 400 Bad Request.\n%s",
					FINE, e.getMessage());
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
		} catch (TransException | SQLException e) {
			e.printStackTrace();
			resp.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
		}
	}
	
	public static List<Range> replyHeaders(HttpServletRequest request, HttpServletResponse response,
			AnsonMsg<DocsReq>msg, IUser usr) throws IOException, TransException, SQLException {
		response.reset();

		Resource resource;

		try {
			resource = new Resource(getDoc(request, msg.body(0), st, usr), msg.body(0).docId);
		}
		catch (IllegalArgumentException e) {
			logi("%s Got an IllegalArgumentException from user code; interpreting it as 400 Bad Request.\n%s",
					FINE, e.getMessage());
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
			response.setHeader("Content-Range", "bytes */" + resource.length);
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

	protected static File getDoc(HttpServletRequest request, DocsReq req, DATranscxt st, IUser usr)
			throws TransException, SQLException, IOException {

		String conn = Connects.uri2conn(req.uri());
		DocTableMeta meta = getMeta.get(req.uri());

		AnResultset rs = (AnResultset) st
				.select(meta.tbl, "p")
				.col(meta.pk)
				.col(meta.clientname).col(meta.createDate)
				.col(meta.fullpath)
				.col(meta.uri)
				.col("mime")
				.whereEq(meta.pk, req.docId)
				.rs(st.instancontxt(conn, usr)).rs(0);
		
		if (!rs.next())
			throw new SemanticException("File not found: %s, %s", req.docId, req.docName);

		String p = DocUtils.resolvExtroot(st, conn, req.docId, usr, meta);
		File f = new File(p);
		if (f.exists() && f.isFile())
			return f;
		else throw new IOException("File not found: " + rs.getString(meta.fullpath));
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
	protected void handleFileNotFound(HttpServletRequest request, HttpServletResponse response) throws IOException {
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
		return !startsOneOf(contentType, "text", "image") && (accept == null || !accepts(accept, contentType));
	}

	/**
	 * Returns true (not matched or older than modified) if it's a conditional request which must return 412.
	 */
	private static boolean preconditionFailed(HttpServletRequest request, Resource resource) {
		String match = request.getHeader("If-Match");
		long unmodified = request.getDateHeader("If-Unmodified-Since");
		return (match != null) ? !matches(match, resource.eTag) : (unmodified != -1 && modified(unmodified, resource.lastModified));
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
	 * <p>Set the cache headers. If the <code>expires</code> argument is larger than 0 seconds, then the following headers
	 * will be set:
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
			response.setHeader("Cache-Control", "public,max-age=" + expires + ",must-revalidate");
			response.setDateHeader("Expires", System.currentTimeMillis() + SECONDS.toMillis(expires));
			response.setHeader("Pragma", ""); // Explicitly set pragma to prevent container from overriding it.
		}
		else {
			// setNoCacheHeaders(response);
			response.setHeader("Cache-Control", "no-cache,no-store,must-revalidate");
			response.setDateHeader("Expires", 0);
			response.setHeader("Pragma", "no-cache"); // Backwards compatibility for HTTP 1.0.
		}
	}

	/**
	 * Returns true if it's a conditional request which must return 304.
	 */
	static boolean notModified(HttpServletRequest request, Resource resource) {
		String noMatch = request.getHeader("If-None-Match");
		long modified = request.getDateHeader("If-Modified-Since");
		return (noMatch != null) ? matches(noMatch, resource.eTag) : (modified != -1 && !modified(modified, resource.lastModified));
	}

	/**
	 * <p>Get requested ranges.</p>
	 * 
	 * If this is null, then we must return 416;<br>
	 * if this is empty, then we must return full file.
	 */
	static List<Range> getRanges(HttpServletRequest request, Resource resource) {
		List<Range> ranges = new ArrayList<>(1);
		String rangeHeader = request.getHeader("Range");

		if (rangeHeader == null) {
			return ranges;
		}
		else if (!Regex_Range.matcher(rangeHeader).matches()) {
			return null;
		}

		String ifRange = request.getHeader("If-Range");

		if (ifRange != null && !ifRange.equals(resource.eTag)) {
			try {
				long ifRangeTime = request.getDateHeader("If-Range");

				if (ifRangeTime != -1 && modified(ifRangeTime, resource.lastModified)) {
					return ranges;
				}
			}
			catch (IllegalArgumentException ifRangeHeaderIsInvalid) {
				logi("%s If-Range header is invalid. Return full file then.\n%s",
						FINE,ifRangeHeaderIsInvalid);
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
			response.setHeader("Content-Length", String.valueOf(range.length));

			if (response.getStatus() == HttpServletResponse.SC_PARTIAL_CONTENT) {
				response.setHeader("Content-Range", "bytes " + range.start + "-" + range.end + "/" + resource.length);
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

		if (ranges.size() == 1) {
			Range range = ranges.get(0);
			stream(resource.file, output, range.start, range.length);
		}
		else {
			for (Range range : ranges) {
				output.println();
				output.println("--" + Multipart_boundary);
				output.println("Content-Type: " + contentType);
				output.println("Content-Range: bytes " + range.start + "-" + range.end + "/" + resource.length);
				stream(resource.file, output, range.start, range.length);
			}

			output.println();
			output.println("--" + Multipart_boundary + "--");
		}
	}

	public static long stream(File file, OutputStream output, long start, long length) throws IOException {
		if (start == 0 && length >= file.length()) {
			try (ReadableByteChannel inputChannel = Channels.newChannel(new FileInputStream(file));
					WritableByteChannel outputChannel = Channels.newChannel(output)) {
				ByteBuffer buffer = ByteBuffer.allocateDirect(Range_Size);
				long size = 0;

				while (inputChannel.read(buffer) != -1) {
					buffer.flip();
					size += outputChannel.write(buffer);
					buffer.clear();
				}

				return size;
			}
		}
		else {
			try (FileChannel fileChannel = (FileChannel) Files.newByteChannel(file.toPath(), StandardOpenOption.READ)) {
				WritableByteChannel outputChannel = Channels.newChannel(output);
				ByteBuffer buffer = ByteBuffer.allocateDirect(Range_Size);
				long size = 0;

				while (fileChannel.read(buffer, start + size) != -1) {
					buffer.flip();

					if (size + buffer.limit() > length) {
						buffer.limit((int) (length - size));
					}

					size += outputChannel.write(buffer);

					if (size >= length) break;

					buffer.clear();
				}

				return size;
			}
		}
	}

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
				// eTag = format(ETAG, URLEncoder.encode(file.getName(), UTF_8.name()), lastModified);
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

	static class Range {
		long start;
		long end;
		long length;
		
		public Range(long start, long end) {
			this.start = start;
			this.end = end;
			length = end - start + 1;
		}
	}
}