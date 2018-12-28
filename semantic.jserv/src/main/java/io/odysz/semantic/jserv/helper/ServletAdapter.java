package io.odysz.semantic.jserv.helper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
	/**<p>Read JMessage from request.</p>
	 * <p>FIXME req input stream is closed if there is no header string in url?</p>
	 * usage: <pre>jhelperReq  = new JHelper&lt;QueryReq&gt;();
QueryReq msg = ServletAdapter.&lt;QueryReq&gt;read(req, jhelperReq, QueryReq.class);</pre>
	 * @param req
	 * @param jreqHelper
	 * @param bodyItemclazz
	 * @return
	 * @throws IOException
	 * @throws SemanticException
	 * @throws ReflectiveOperationException
	 */
	public static <T extends JBody> JMessage<T> read(HttpServletRequest req,
				JHelper<T> jreqHelper, Class<? extends JBody> bodyItemclazz)
				throws IOException, SemanticException, ReflectiveOperationException {
		InputStream in = null; 
		String headstr = req.getParameter("header");
		if (headstr != null && headstr.length() > 3) {
			byte[] b = headstr.getBytes();
			in = new ByteArrayInputStream(b);
		}
		else in = req.getInputStream();
		
		JMessage<T> msg = jreqHelper.readJson(in, bodyItemclazz);
		in.close();

		return msg;
	}

//	public static void write(HttpServletResponse resp, JHelper<? extends JMessage> jrespHelper,
//				SemanticObject msg)
//				throws IOException {
//		resp.setCharacterEncoding("UTF-8");
//		OutputStream os = resp.getOutputStream();
//		
//		jrespHelper.writeJson(os, msg);
//		os.flush();
//	}

	public static void write(HttpServletResponse resp, 
				SemanticObject msg) throws IOException {
		resp.setCharacterEncoding("UTF-8");
		resp.setContentType("application/json");
		OutputStream os = resp.getOutputStream();
		
		JHelper.writeJson(os, msg);
		/*
		// TODO move to JProtocol
		JsonWriter writer = new JsonWriter(new OutputStreamWriter(os, "UTF-8"));
		writer.beginObject();
		writer.name("port").value(msg.getString("port"));
		writer.name("code").value(msg.getString("code"));
		if (msg.getType("error") != null)
			writer.name("error").value(msg.getString("error"));
		if (msg.getType("msg") != null)
			writer.name("msg").value(msg.getString("msg"));
		// TODO body ...
		writer.endObject();
		writer.close();
		*/
		os.flush();
	}

}
