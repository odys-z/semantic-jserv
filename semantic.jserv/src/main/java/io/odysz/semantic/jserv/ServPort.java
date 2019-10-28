package io.odysz.semantic.jserv;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.Anson;
import io.odysz.anson.JsonOpt;
import io.odysz.anson.x.AnsonException;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jsession.ISessionVerifier;
import io.odysz.semantics.x.SemanticException;

/**<p>Base serv class for handling json request.</p>
 * Servlet extending this must subclass this class, and override
 * {@link #onGet(AnsonMsg, HttpServletResponse) onGet()} and {@link #onPost(AnsonMsg, HttpServletResponse) onPost()}.
 * 
 * @author odys-z@github.com
 *
 * @param <T> any subclass extends {@link AnsonBody}.
 */
public abstract class ServPort<T extends AnsonBody> extends HttpServlet {
	protected static ISessionVerifier verifier;
	protected IPort p;
	
	static {
		verifier = JSingleton.getSessionVerifier();
	}

	public ServPort(Port port) { this.p = port; }

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		InputStream in;
		String headstr = req.getParameter("header");
		if (headstr != null && headstr.length() > 1) {
			byte[] b = headstr.getBytes();
			in = new ByteArrayInputStream(b);
		}
		else {
			if (req.getContentLength() == 0)
				return ;
			in = req.getInputStream();
		}
		resp.setCharacterEncoding("UTF-8");
		resp.setContentType("application/json");
		try {
			@SuppressWarnings("unchecked")
			AnsonMsg<T> msg = (AnsonMsg<T>) Anson.fromJson(in);
			onGet(msg, resp);
		} catch (AnsonException | SemanticException e) {
			if (ServFlags.query)
				e.printStackTrace();
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		}
		in.close();
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			resp.setCharacterEncoding("UTF-8");

			// Firefox will complain "XML Parsing Error: not well-formed" even parsed resp correctly.
			resp.setContentType("application/json");

			InputStream in = req.getInputStream(); 
			@SuppressWarnings("unchecked")
			AnsonMsg<T> msg = (AnsonMsg<T>) Anson.fromJson(in);

			onPost(msg, resp);
		} catch (SemanticException | AnsonException e) {
			if (ServFlags.query)
				e.printStackTrace();
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		}
	}

	protected void write(HttpServletResponse resp, AnsonMsg<? extends AnsonResp> msg, JsonOpt... opts) {
		try {
			msg.toBlock(resp.getOutputStream(), opts);
		} catch (AnsonException | IOException e) {
			e.printStackTrace();
		}
	}

	/**Response with OK message.
	 * @param arrayList
	 * @return 
	 */
	protected AnsonMsg<AnsonResp> ok(ArrayList<AnResultset> arrayList) {
		AnsonMsg<AnsonResp> msg = new AnsonMsg<AnsonResp>(p, MsgCode.ok);
		AnsonResp bd = new AnsonResp(msg);
		msg.body(bd.rs(arrayList));
		return msg;
	}

	protected AnsonMsg<AnsonResp> ok(AnResultset rs) {
		AnsonMsg<AnsonResp> msg = new AnsonMsg<AnsonResp>(p, MsgCode.ok);
		AnsonResp bd = new AnsonResp(msg);
		msg.body(bd.rs(rs));
		return msg;
	}

	protected <U extends AnsonResp> AnsonMsg<U> ok(U body) {
		AnsonMsg<U> msg = new AnsonMsg<U>(p, MsgCode.ok);
		msg.body(body);
		return msg;
	}
	
	protected AnsonMsg<AnsonResp> ok(String templ, Object... args) {
		AnsonMsg<AnsonResp> msg = AnsonMsg.ok(p, String.format(templ, args));
		return msg;
	}
	
	protected AnsonMsg<AnsonResp> err(MsgCode code, String templ, Object ... args) {
		AnsonMsg<AnsonResp> msg = new AnsonMsg<AnsonResp>(p, code);
		AnsonResp bd = new AnsonResp(msg, String.format(templ, args));
		return msg.body(bd);
	}
	
	abstract protected void onGet(AnsonMsg<T> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException;

	abstract protected void onPost(AnsonMsg<T> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException;

}
