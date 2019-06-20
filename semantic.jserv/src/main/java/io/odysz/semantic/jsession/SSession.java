package io.odysz.semantic.jsession;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.common.AESHelper;
import io.odysz.common.Configs;
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.jprotocol.IPort;
import io.odysz.semantic.jprotocol.JHeader;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jprotocol.JMessage;
import io.odysz.semantic.jprotocol.JMessage.MsgCode;
import io.odysz.semantic.jprotocol.JMessage.Port;
import io.odysz.semantic.jprotocol.JProtocol;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.helper.Html;
import io.odysz.semantic.jserv.helper.ServletAdapter;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

/**<p>1. Handle login-obj: {@link SessionReq}.<br>
 *  a: "login/logout",<br>
 *  uid: "user-id",<br>
 *  pswd: "uid-cipher-by-pswd",<br>
 *  iv: "session-iv"</p>
 * <p>2. Session verifying using session-header<br>
 * uid: “user-id”,<br>
 * ssid: “session-id-plain/cipher”,<br>
 * sys: “module-id”</p>
 * <p>Session object are required when login successfully, and removed automatically.
 * When removing, the SUser object is removed via session lisenter.</p>
 * <p><b>Note:</b></p>Session header is post by client in HTTP request's body other than in HTTP header.
 * It's HTTP body payload, understood by semantic-jserv as a request header semantically.</p>
 * <p>Also don't confused with servlet session - created via getSessionId(),
 * <br>and you'd better 
 * <a href='https://stackoverflow.com/questions/2255814/can-i-turn-off-the-httpsession-in-web-xml'>turn off it</a>.</p>
 * 
 * @author odys-z@github.com
 */
@WebServlet(description = "session manager", urlPatterns = { "/login.serv" })
public class SSession extends HttpServlet implements ISessionVerifier {
	public static enum Notify { changePswd, todo }

	/** * */
	private static final long serialVersionUID = 1L;

	private static final IPort p = Port.session;

	
	/**[session-id, SUser]*/
	static HashMap<String, IUser> users;

	private static ScheduledExecutorService scheduler;
	
	/**session pool reentrant lock*/
	public static ReentrantLock lock;

	private static ScheduledFuture<?> schedualed;
	
	static DATranscxt sctx;

	static JHelper<SessionReq> jreqHelper;

	/**Initialize semantext, schedule tasks,
	 * load root key from tomcat context.xml.
	 * To configure root key in tomcat, in context.xml, <pre>
	&lt;Context&gt;
		&lt;Parameter name="io.oz.root-key" value="*************" override="false"/&gt;
	&lt;/Context&gt;&lt;/pre>
	 * @param daSctx
	 * @param ctx context for loading context.xml/resource
	 * @throws SAXException something wrong with configuration files
	 * @throws IOException file accessing failed
	 * @throws SemanticException semantics error
	 * @throws SQLException database accessing error
	 */
	public static void init(DATranscxt daSctx, ServletContext ctx)
			throws SAXException, IOException, SemanticException, SQLException {
		sctx = daSctx;

		lock = new ReentrantLock();

		String conn = daSctx.basiconnId();
		Utils.logi("Initializing session based on connection %s, basic session tables, users, functions, roles, should located here", conn);
		DATranscxt.loadSemantics(conn,
					JSingleton.getFileInfPath("semantic-log.xml"));

		jreqHelper = new JHelper<SessionReq>();

		users = new HashMap<String, IUser>();
		// see https://stackoverflow.com/questions/34202701/how-to-stop-a-scheduledexecutorservice
		//scheduler = Executors.newSingleThreadScheduledExecutor();
		scheduler = Executors.newScheduledThreadPool(1);

		int m = 20;
		try { m = Integer.valueOf(Configs.getCfg("ss-timeout-min"));} catch (Exception e) {}
		if (ServFlags.session)
			Utils.warn("[ServFlags.session] SSession debug mode true (ServFlage.session)");

        schedualed = scheduler.scheduleAtFixedRate(
        		new SessionChecker(users, m),
        		0, 1, TimeUnit.MINUTES);
	}
	
	/**Stop all threads that were scheduled by SSession.
	 * @param msDelay delay in milliseconds.
	 */
	public static void stopScheduled(int msDelay) {
		Utils.logi("cancling session checker ... ");
		schedualed.cancel(true);
		scheduler.shutdown();
		try {
		    if (!scheduler.awaitTermination(msDelay, TimeUnit.MILLISECONDS)) {
		        scheduler.shutdownNow();
		    } 
		} catch (InterruptedException e) {
		    scheduler.shutdownNow();
		}
	}

	/**Hard coded string of user table information.
	 *
	 * @author odys-z@github.com
	 */
	public static class UserMeta {
		/**key in config.xml for class name, this class implementing IUser is used as user object's type. */
		static String clzz = "class-IUser";
		static String tbl = "a_user";
		static String pk = "userId";
		static String uname = "userName";
		static String pswd = "pswd";
		static String iv = "encAuxiliary";

		public static UserMeta config() { return new UserMeta(); }

		public UserMeta userName(String unamefield) {
			uname = unamefield;
			return this;
		}

		public UserMeta iv(String ivfield) {
			iv = ivfield;
			return this;
		}

		public UserMeta pswd(String pswdfield) {
			pswd = pswdfield;
			return this;
		}
	}

	/**FIXME: This is a security breach. Client request can duplicated request with plain ssid and uid.<br>
	 * Should we use a session key?
	 * @param jHeader
	 * @return {@link JUser} if succeed, which can be used for db logging
	 * - use this to load functions, etc.
	 * @throws SsException Session checking failed.
	 * @throws SQLException Reqest payload header.usrAct is null (TODO sure?)
	 */
	@Override
	public IUser verify(JHeader jHeader) throws SsException, SQLException {
		if (jHeader == null)
			throw new SsException("session header is missing");

		String ssid = (String)jHeader.ssid();
		if (users.containsKey(ssid)) {
			IUser usr = users.get(ssid);
			String slogid = (String)jHeader.logid();
			if (slogid != null && slogid.equals(usr.uid())) {
				usr.touch();
				return usr;
			}
			else throw new SsException("session token is not matching");
		}
		else throw new SsException("session info is missing or timeout");
	}

	public static IUser getUser(SemanticObject jheader) {
		return users.get(jheader.get("ssid"));
	}

	/**Handle ping(touch)
	 * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse resp)
			throws ServletException, IOException {
		if (ServFlags.session) System.out.println("login get ========");
		resp.setContentType("text/html;charset=UTF-8");
		try {
			String headstr = request.getParameter("header");
			if (headstr == null)
				throw new SsException("Query session with GET request neending a header string.");

			String t = request.getParameter("t");
			if (t != null) t = t.toLowerCase().trim();
			if ("ping".equals(t) || "touch".equals(t)) {
				// already touched by verify()
				IUser usr = verify(JHelper.readHeader(headstr));
				SemanticObject ok = new SemanticObject();
				ok.put(usr.uid(), (SemanticObject)usr);
				resp.getWriter().write(Html.map(ok));
			}
			else {
				ServletAdapter.write(resp, JProtocol.err(p, MsgCode.exGeneral,
						"Login.serv using GET to touch session info - use POST to login, logout, check session."));
			}
		} catch (SsException e) {
			ServletAdapter.write(resp, JProtocol.err(p, e.code, e.getMessage()));
		} catch (SQLException e) {
			e.printStackTrace();
		} finally { }
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		if (ServFlags.session) System.out.println("login post ------");
		jsonResp(request, response);
	}

	private void jsonResp(HttpServletRequest req, HttpServletResponse response) throws IOException {
		try {
			String connId = req.getParameter("conn");
			if (connId == null || connId.trim().length() == 0)
				connId = Connects.defltConn();
	
			JMessage<SessionReq> payload = ServletAdapter.<SessionReq>read(req, jreqHelper, SessionReq.class);
			// find user and check login info 
			// request-obj: {a: "login/logout", uid: "user-id", pswd: "uid-cipher-by-pswd", iv: "session-iv"}
			if (payload != null) {
			// if (payload != null && payload.header() != null) {
				SessionReq sessionBody = payload.body().get(0);
				String a = sessionBody.a();
				if ("login".equals(a)) {
					IUser login = loadUser(sessionBody, connId);
					if (login.login(sessionBody)) {
						lock.lock();
						users.put(login.sessionId(), login);
						lock.unlock();
						
						ServletAdapter.write(response, JProtocol.ok(p, (SemanticObject)login),
								payload.opts());
					}
					else throw new SsException("Password doesn't matching! Expecting token encrypt(uid, pswd, iv)");
				}
				else if ("logout".equals(a)) {
					JHeader header = payload.header();
					try {verify(header);}
					catch (SsException sx) {} // logout anyway if session check is failed
					// {uid: “user-id”,  ssid: “session-id-plain/cipher”, vi: "vi-b64"<, sys: “module-id”>}
					String ssid = (String) header.ssid();
	
					lock.lock();
					IUser usr = users.remove(ssid);
					lock.unlock();
	
					if (usr != null) {
						SemanticObject resp = usr.logout();
						ServletAdapter.write(response, JProtocol.ok(p, resp),
								payload.opts());
					}
					else
						ServletAdapter.write(response, JProtocol.ok(p,
								new SemanticObject().put("msg", "But no such session exists.")),
								payload.opts());
				}
				else if ("pswd".equals(a)) {
					// change password
					JHeader header = payload.header();
					IUser usr = verify(header);
					
					// client: encrypt with ssid, send cipher with iv
					// FIXME using of session key, see bug of verify()
					String ssid = (String) header.ssid();
//					String iv64 = sessionBody.iv;
					String iv64 = sessionBody.md("iv_pswd");
					String newPswd = sessionBody.md("pswd");
					usr.sessionKey(ssid);

					// dencrypt field of a_user.userId: pswd, encAuxiliary
					if (!DATranscxt.hasSemantics(connId, UserMeta.tbl, smtype.dencrypt)) {
						throw new SemanticException("Can't update pswd, because table %s is not protected by semantics %s",
								UserMeta.tbl, smtype.dencrypt.name());
					}

					Utils.logi("new pswd: %s",
						AESHelper.decrypt(newPswd, usr.sessionId(), AESHelper.decode64(iv64)));

					sctx.update(UserMeta.tbl, usr)
						.nv(UserMeta.pswd, newPswd)
						.nv(UserMeta.iv, iv64)
						.whereEq(UserMeta.pk, usr.uid())
						.u(sctx.instancontxt(usr));

					// ok, logout
					lock.lock();
					users.remove(ssid);
					lock.unlock();
	
					ServletAdapter.write(response, JProtocol.ok(p, "You must relogin!"));
				}
				else {
					String t = req.getParameter("t");
					if (t != null) t = t.toLowerCase().trim();
					if ("ping".equals(t) || "touch".equals(t)) {
						JHeader header = payload.header();
						verify(header);
						ServletAdapter.write(response, JProtocol.ok(p, ""),
								payload.opts());
					}
					else throw new SsException ("Session Request not supported: a=%s", a);
				}
			}
			else throw new SsException ("Session request not supported: request body is null");
		} catch (SsException | TransException e) {
			ServletAdapter.write(response, JProtocol.err(p, MsgCode.exSession, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			ServletAdapter.write(response, JProtocol.err(p, MsgCode.exGeneral, e.getMessage()));
		} finally {
		}
	}

	IUser jrobot = new JRobot();

	/**Load user instance form DB table (name = {@link UserMeta#tbl}).
	 * @param jreq
	 * @param connId
	 * @return new IUser instance loaded from database (from connId)
	 * @throws TransException
	 * @throws SQLException
	 * @throws SsException
	 * @throws ReflectiveOperationException
	 * @throws IOException 
	 * @throws GeneralSecurityException 
	 */
	private IUser loadUser(SessionReq jreq, String connId)
			throws TransException, SQLException, SsException, ReflectiveOperationException, GeneralSecurityException, IOException {
		SemanticObject s = sctx.select(UserMeta.tbl, "u")
			.col(UserMeta.pk, "uid")
			.col(UserMeta.uname, "uname")
			.col(UserMeta.pswd, "pswd")
			.col(UserMeta.iv, "iv")
			// .col(UserMeta.urlField, "url")
			.where_("=", "u." + UserMeta.pk, jreq.uid())
			.rs(sctx.instancontxt(jrobot));
		
		SResultset rs = (SResultset) s.rs(0);;
		if (rs.beforeFirst().next()) {
			String uid = rs.getString("uid");
			IUser obj = createUser(UserMeta.clzz, uid, rs.getString("pswd"), rs.getString("iv"), rs.getString("uname"));
			if (obj instanceof SemanticObject)
				return obj;
			throw new SemanticException("IUser implementation must extend SemanticObject.");
		}
		else
			throw new SsException("User Id not found: ", jreq.uid());
	}

	/**
	 * @param clsNamekey class name
	 * @param uid user id
	 * @param pswd 
	 * @param iv auxiliary encryption field
	 * @param userName 
	 * @return new IUser instance
	 * @throws ReflectiveOperationException 
	 * @throws SemanticException 
	 * @throws IOException 
	 * @throws GeneralSecurityException 
	 */
	private static IUser createUser(String clsNamekey, String uid, String pswd, String iv, String userName)
			throws ReflectiveOperationException, SemanticException, GeneralSecurityException, IOException {
		if (!Configs.hasCfg(clsNamekey))
			throw new SemanticException("No class name configured for creating user information, check config.xml/k=%s", clsNamekey);
		String clsname = Configs.getCfg(clsNamekey);
		if (clsname == null)
			throw new SemanticException("No class name configured for creating user information, check config.xml/k=%s", clsNamekey);

		@SuppressWarnings("unchecked")
		Class<IUser> cls = (Class<IUser>) Class.forName(clsname);
		Constructor<IUser> constructor = null;
//		try {
//			constructor = cls.getConstructor(String.class, String.class, String.class, String.class);
//		} catch (NoSuchMethodException ne) {
//			throw new SemanticException("Class %s needs a consturctor like SUser(String uid, String pswd, String iv, String usrName).", "clsname");
//		}
		try {
			constructor = cls.getConstructor(String.class, String.class, String.class);
		} catch (NoSuchMethodException ne) {
			throw new SemanticException("Class %s needs a consturctor like JUser(String uid, String pswd, String usrName).", "clsname");
		}

		try {
			if (!LangExt.isblank(iv)) {
				try {
					// still can be wrong with messed up data, e.g. with iv and plain pswd
					pswd = AESHelper.decrypt(pswd, DATranscxt.key("user-pswd"), AESHelper.decode64(iv));
				} catch (Exception e) {
					Utils.warn("Decrypting user pswd failed. cipher: %s, iv %s, rootkey: *(%s)",
							pswd, iv == null ? null : AESHelper.decode64(iv),
							DATranscxt.key("user-pswd") == null ? null : DATranscxt.key("user-pswd").length());
				}
				return (IUser) constructor.newInstance(uid, pswd, userName);
			}
			else 
				return (IUser) constructor
						.newInstance(uid, pswd, userName)
						.notify(Notify.changePswd.name());

		} catch (InvocationTargetException ie) {
			ie.printStackTrace();
			throw new SemanticException("create IUser instance failed: %s",
					ie.getTargetException() == null ? "" : ie.getTargetException().getMessage());
		}
	}

	/**Get a 24 chars random Id.
	 * @return
	public static String getSSId() {
		String ssid = null;
		while (ssid == null || users.containsKey(ssid)) {
			ssid = String.format("%s%s%s%s",
					Radix64.toString(random.nextInt()),
					Radix64.toString(random.nextInt()),
					Radix64.toString(random.nextInt()),
					Radix64.toString(random.nextInt()));
		}
		return ssid;
	}
	 */
}
