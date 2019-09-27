//package io.odysz.semantic.jserv;
//
//import java.io.ByteArrayInputStream;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.io.OutputStream;
//
//import javax.servlet.http.HttpServlet;
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
//
//import io.odysz.semantic.jprotocol.AnsonBody;
//import io.odysz.semantic.jprotocol.AnsonMsg;
//import io.odysz.semantic.jprotocol.JHelper;
//import io.odysz.semantic.jprotocol.JOpts;
//import io.odysz.semantics.SemanticObject;
//import io.odysz.semantics.x.SemanticException;
//
///**<p>Service Implementation - SOAP port manager</p>
// * @author odys-z@github.com
// *
// * @param <T> e.g {@link io.odysz.semantic.jserv.R.AnQueryReq}
// */
//public class Serv extends HttpServlet {
//	/** * */
//	private static final long serialVersionUID = 1L;
//
//	static final int bufLen = 1024 * 32;
//
//	static JOpts _opts = new JOpts();
//
//	/**<p>Read JMessage from request.</p>
//	 * <p>FIXME req input stream is closed if there is no header string in url?</p>
//	 * usage: <pre>jhelperReq  = new JHelper&lt;QueryReq&gt;();
//QueryReq msg = ServletAdapter.&lt;QueryReq&gt;read(req, jhelperReq, QueryReq.class);</pre>
//	 * @param req
//	 * @return deserialized {@link AnsonMsg}
//	 * @throws IOException
//	 * @throws SemanticException
//	 * @throws ReflectiveOperationException
//	 */
//	public static <T extends AnsonBody> AnsonMsg<T> read(HttpServletRequest req)
//				throws IOException, SemanticException, ReflectiveOperationException {
//		InputStream in = null; 
//		String headstr = req.getParameter("header");
//		if (headstr != null && headstr.length() > 1) {
//			byte[] b = headstr.getBytes();
//			in = new ByteArrayInputStream(b);
//		}
//		else {
//			if (req.getContentLength() == 0)
//				return null;
//			in = req.getInputStream();
//		}
//		
//		AnsonMsg<T> msg = null;// Anson.parse(in);
//		in.close();
//
//		return msg;
//	}
//
//	public static void write(HttpServletResponse resp, SemanticObject msg) throws IOException {
//		write(resp, msg, _opts);
//	}
//
//	public static void write(HttpServletResponse resp, 
//				SemanticObject msg, JOpts opts) throws IOException {
//		resp.setCharacterEncoding("UTF-8");
//		resp.setContentType("application/json");
//		OutputStream os = resp.getOutputStream();
//		
//		try {
//			JHelper.writeJsonResp(os, msg, opts);
//		} catch (SemanticException e) {
//			e.printStackTrace();
//		}
//		os.flush();
//	}
//
//	public static void write(HttpServletResponse resp, InputStream ins) throws IOException {
//		resp.setCharacterEncoding("UTF-8");
//		resp.setContentType("application/json");
//		OutputStream os = resp.getOutputStream();
//
//		copy(os, ins);
//	}
//
//	public static void copy(OutputStream outs, InputStream ins) throws IOException {
//		int numRead;
//		byte[] buf = new byte[bufLen];
//
//		while ( (numRead = ins.read(buf)) >= 0 )
//			outs.write(buf, 0, numRead);
//	}
//
//	public static void copy(FileOutputStream outs, String line) throws IOException {
//		outs.write(line.getBytes());
//	}
//}
