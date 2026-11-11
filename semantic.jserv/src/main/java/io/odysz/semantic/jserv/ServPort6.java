package io.odysz.semantic.jserv;

import static io.odysz.common.LangExt.f;
import static io.odysz.common.LangExt.isNull;
import static io.odysz.common.LangExt.join;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Map;

import io.odysz.anson.Anson;
import io.odysz.anson.AnsonException;
import io.odysz.anson.JsonOpt;
import io.odysz.common.AESHelper2;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.jprotocol.AnsonBody;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.ISessionVerifier;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * <p>{@link ServPort} for Jakarta Servlet 6.0 (jakarta.servlet.*), the servlet API
 * of Jetty 12's ee10 environment.</p>
 *
 * Servlets extending this override
 * {@link #onGet(AnsonMsg, HttpServletResponse) onGet()} and
 * {@link #onPost(AnsonMsg, HttpServletResponse) onPost()}, the same as with ServPort.
 *
 * <p>Differences to {@link ServPort}:</p><ol>
 * <li>No javax.servlet type is touched, so a ServPort6 runs without the javax.servlet-api jar.
 *     For this, the session verifier is not created by {@link JSingleton} (which creates an
 *     AnSession, a javax servlet). Set it with {@link #verifier(ISessionVerifier)}.</li>
 * <li>Range requests (http 206, {@link io.odysz.semantic.tier.docs.Docs206}) are not ported.
 *     A Range header is ignored.</li>
 * </ol>
 *
 * @since 1.5.21
 *
 * @param <T> any subclass extends {@link AnsonBody}.
 */
public abstract class ServPort6<T extends AnsonBody> extends HttpServlet {
	private static final long serialVersionUID = 1L;

	@FunctionalInterface
	public interface PrintstreamProvider {
		PrintStream get();
	}

	@FunctionalInterface
	public interface OnHttpCallback {
		void onHttp();
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		if (os != null) {
			Utils.logOut(os.get());
			os = null;
		}

		if (es != null) {
			Utils.logErr(es.get());
			es = null;
		}
	}

	protected ISessionVerifier verifier;

	protected IPort p;

	/** @return port-name : url patterns (if annotated with {@link WebServlet}) */
	public String port() {
		WebServlet info = getClass().getAnnotation(WebServlet.class);
		return info == null ? p.name()
			 : f("%s : %s", p.name(), join(", ", (Object[])info.urlPatterns()));
	}

	/**
	 * Unlike {@link ServPort#verifier()}, there is no default verifier.
	 * @return the verifier, null if not set
	 */
	public ISessionVerifier verifier() {
		return verifier;
	}

	/**
	 * Set the session verifier.
	 * @param v
	 * @return this
	 */
	public ServPort6<T> verifier(ISessionVerifier v) {
		this.verifier = v;
		return this;
	}

	public ServPort6(IPort port) {
		this.p = port;
	}

	protected DATranscxt st;

	private OnHttpCallback ongetback;
	private OnHttpCallback onpostback;

	/**
	 * Set call back handlers when {@link #onGet(AnsonMsg, HttpServletResponse)} &amp;
	 * {@link #onPost(AnsonMsg, HttpServletResponse)} are returned successfully.
	 * @see ServPort#setCallbacks(io.odysz.semantic.jserv.ServPort.OnHttpCallback, io.odysz.semantic.jserv.ServPort.OnHttpCallback...)
	 * @param onpost
	 * @param onget
	 * @return this
	 */
	public ServPort6<T> setCallbacks(OnHttpCallback onpost, OnHttpCallback... onget) {
		this.onpostback = onpost;
		this.ongetback = isNull(onget) ? null : onget[0];
		return this;
	}

	/**
	 * Set the default transaction builder.
	 *
	 * @param trb0
	 * @return this
	 */
	public ServPort6<T> trb(DATranscxt trb0) {
		st = trb0;
		return this;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		InputStream in;
		String headstr = req.getParameter("header");
		String anson64 = req.getParameter("anson64");

		if (headstr != null && headstr.length() > 1) {
			byte[] b = headstr.getBytes();
			in = new ByteArrayInputStream(b);
		}
		else if (!LangExt.isEmpty(anson64)) {
			byte[] b = AESHelper2.decode64(anson64);
			in = new ByteArrayInputStream(b);
		}
		else {
			if (req.getContentLength() <= 0) {
				write(resp, err(MsgCode.exGeneral, String.format(
					"Empty Request, calling to Servport, %s.onGet(), is ignored.",
					this.getClass().getName())));
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

			if (ongetback != null)
				ongetback.onHttp();
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
	 */
	protected void onGetAnsonException(AnsonException e,
			HttpServletResponse resp, Map<String, String[]> map) throws IOException, ServletException {
		if (ServFlags.port)
			e.printStackTrace();
		write(resp, err(MsgCode.exSemantic, "%s\n%s", e.getMessage(),
				"Usually this is an error raised from browser visiting."));
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		try {
			resp.setCharacterEncoding("UTF-8");
			resp.setContentType("application/json");

			InputStream in = req.getInputStream();
			@SuppressWarnings("unchecked")
			AnsonMsg<T> msg = (AnsonMsg<T>) Anson.fromJson(in);

			if (ServFlags.port)
				Utils.logi("[ServFlags.port] Dispatching %s : %s - %s",
						req.getRemoteAddr(), msg.port(), msg.body(0).a());

			onPost(msg.addr(req.getRemoteAddr()), resp);

			if (onpostback != null)
				onpostback.onHttp();
		} catch (SemanticException | AnsonException e) {
			if (ServFlags.port)
				e.printStackTrace();
			write(resp, err(MsgCode.exSemantic, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			write(resp, err(MsgCode.exGeneral, "%s: %s", e.getClass().getName(), e.getMessage()));
		}
	}

	/**
	 * @param anHeader
	 * @param seq
	 * @return the user
	 * @throws SsException verifying failed, or no verifier is set
	 */
	public IUser verify(AnsonHeader anHeader, int ...seq) throws SsException {
		if (verifier == null)
			throw new SsException("No session verifier is set to port %s.", p.name());
		return verifier.verify(anHeader, seq);
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
				isNull(args) ? templ :
				f(templ, args));
		return msg.body(bd);
	}

	abstract protected void onGet(AnsonMsg<T> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException;

	abstract protected void onPost(AnsonMsg<T> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException;

	protected static PrintstreamProvider os;
	public static void outstream(PrintstreamProvider out) {
		os = out;
	}

	static PrintstreamProvider es;
	public static void errstream(PrintstreamProvider err) {
		es = err;
	}
}
