package io.odysz.semantic.jserv;

import static io.odysz.common.LangExt.isblank;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.odysz.anson.Anson;
import io.odysz.anson.JsonOpt;
import io.odysz.anson.x.AnsonException;
import io.odysz.common.AESHelper;
import io.odysz.common.LangExt;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.ISessionVerifier;
import io.odysz.semantic.tier.docs.Docs206;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

/**
 * <p>Base serv class for handling json request.</p>
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

	public ServPort(IPort port) { this.p = port; }

	
//	@Override
//    protected void doGet(HttpServletRequest request, HttpServletResponse response)
//            throws ServletException, IOException {
//    	String range = request.getHeader("Range");
//    	if (!isblank(range)) {
//    		Docs206.get206(request, response, robot);
//    	}
//    	else super.doGet(request, response);
//    }

	/**
	 * Since 1.4.28, semantic.jserv support for Range header for all ports, which is critical for 
	 * some steam features a client side, such as resume downloading or play back from a position.
	 * 
	 * Example of Chrome request header for MP4
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
	 * Example of Chrome request header for MP3<pre>
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
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
    	String range = req.getHeader("Range");
    	if (!isblank(range)) {
    		try {
				Docs206.get206(req, resp);
			} catch (SsException e) {
				write(resp, err(MsgCode.exSession, e.getMessage()));
			}
			return;
    	}

		InputStream in;
		String headstr = req.getParameter("header");
		String anson64 = req.getParameter("anson64");

		if (headstr != null && headstr.length() > 1) {
			byte[] b = headstr.getBytes();
			in = new ByteArrayInputStream(b);
		}
		else if (!LangExt.isEmpty(anson64)) {
			byte[] b = AESHelper.decode64(anson64);
			in = new ByteArrayInputStream(b);
		}
		else {
			if (req.getContentLength() <= 0) {
				write(resp, err(MsgCode.exGeneral, "Empty Request"));
				return ;
			}
			in = req.getInputStream();
		}
		resp.setCharacterEncoding("UTF-8");
		resp.setContentType("application/json");
		try {
			@SuppressWarnings("unchecked")
			AnsonMsg<T> msg = (AnsonMsg<T>) Anson.fromJson(in);
			onGet(msg.addr(req.getRemoteAddr()), resp);
		} catch (AnsonException e) {
			onGetAnsonException(e, resp, req.getParameterMap());
		} catch (SemanticException e) {
			if (ServFlags.port)
				e.printStackTrace();
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (Throwable t) {
			t.printStackTrace();
			write(resp, err(MsgCode.exGeneral, "Internal error at sever side."));
		} finally {
			in.close();
		}
	}

	/**
	 * Override this to handle null envelop in GET requests.
	 * @param e
	 * @param resp
	 * @param map 
	 * @throws IOException 
	 * @throws SQLException 
	 * @throws TransException 
	 */
	protected void onGetAnsonException(AnsonException e,
			HttpServletResponse resp, Map<String, String[]> map) throws IOException, ServletException {
		if (ServFlags.port)
			e.printStackTrace();
		write(resp, err(MsgCode.exSemantic, e.getMessage() + "\n Usually this is an error raised from browser visiting."));
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

			onPost(msg.addr(req.getRemoteAddr()), resp);
		} catch (SemanticException | AnsonException e) {
			if (ServFlags.query)
				e.printStackTrace();
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exGeneral, e.getClass().getName(), e.getMessage()));
		}
	}

	/**
	 * Write message to resp.
	 * 
	 * @param resp can be null if user handled response already
	 * @param msg
	 * @param opts
	 */
	protected void write(HttpServletResponse resp, AnsonMsg<? extends AnsonResp> msg, JsonOpt... opts) {
		try {
			if (msg != null)
				msg.toBlock(resp.getOutputStream(), opts);
		} catch (AnsonException | IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Response with OK message.
	 * @param arrayList
	 * @return AnsonMsg code = ok 
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
	
	static public AnsonMsg<AnsonResp> ok(IPort p) {
		AnsonMsg<AnsonResp> msg = new AnsonMsg<AnsonResp>(p, MsgCode.ok);
		return msg.body(new AnsonResp().msg(MsgCode.ok.name()));
	}
	
	protected AnsonMsg<AnsonResp> ok(String templ, Object... args) {
		AnsonMsg<AnsonResp> msg = AnsonMsg.ok(p, String.format(templ, args));
		return msg;
	}
	
	protected AnsonMsg<AnsonResp> err(MsgCode code, String templ, Object ... args) {
		AnsonMsg<AnsonResp> msg = new AnsonMsg<AnsonResp>(p, code);
		AnsonResp bd = new AnsonResp(msg,
				// sql error messages can have '%'
				args == null ? templ :
				String.format(templ == null ? "" : templ, args));
		return msg.body(bd);
	}
	
	abstract protected void onGet(AnsonMsg<T> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException;

	abstract protected void onPost(AnsonMsg<T> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException;

}
