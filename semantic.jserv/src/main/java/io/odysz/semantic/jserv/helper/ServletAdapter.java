package io.odysz.semantic.jserv.helper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.SQLException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.semantic.jprotocol.JBody;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;

/**<p>Servlet request / response IO helper</p>
 * TODO: all serv extends this class?
 * @author ody
 *
 */
public class ServletAdapter {
	static final int bufLen = 1024 * 32;

	/**<p>Read JMessage from request.</p>
	 * <p>FIXME req input stream is closed if there is no header string in url?</p>
	 * usage: <pre>jhelperReq  = new JHelper&lt;QueryReq&gt;();
QueryReq msg = ServletAdapter.&lt;QueryReq&gt;read(req, jhelperReq, QueryReq.class);</pre>
	 * @param req
	 * @param jreqHelper
	 * @param bodyItemclazz
	 * @return deserialized JMessage
	 * @throws IOException
	 * @throws SemanticException
	 * @throws ReflectiveOperationException
	 */
	public static <T extends JBody> JMessage<T> read(HttpServletRequest req,
				JHelper<T> jreqHelper, Class<? extends JBody> bodyItemclazz)
				throws IOException, SemanticException, ReflectiveOperationException {
		InputStream in = null; 
		String headstr = req.getParameter("header");
		if (headstr != null && headstr.length() > 1) {
			byte[] b = headstr.getBytes();
			in = new ByteArrayInputStream(b);
		}
		else {
			if (req.getContentLength() == 0)
				return null;
			in = req.getInputStream();
		}
		
		JMessage<T> msg = jreqHelper.readJson(in, bodyItemclazz);
		in.close();

		return msg;
	}

	public static void write(HttpServletResponse resp, 
				SemanticObject msg) throws IOException {
		resp.setCharacterEncoding("UTF-8");
		resp.setContentType("application/json");
		OutputStream os = resp.getOutputStream();
		
		try {
			JHelper.writeJsonResp(os, msg);
		} catch (SQLException e) {
			e.printStackTrace();
		}
		os.flush();
	}

	public static void write(HttpServletResponse resp, InputStream ins) throws IOException {
		resp.setCharacterEncoding("UTF-8");
		resp.setContentType("application/json");
		OutputStream os = resp.getOutputStream();

		copy(os, ins);
	}

	public static void copy(OutputStream outs, InputStream ins) throws IOException {
		int numRead;
		byte[] buf = new byte[bufLen];

		while ( (numRead = ins.read(buf)) >= 0 )
			outs.write(buf, 0, numRead);
	}
}
