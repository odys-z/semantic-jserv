package io.odysz.semantic.jsession;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
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

import org.apache.commons.io.FilenameUtils;
import org.xml.sax.SAXException;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
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

/**Handle login-obj: {a: "login/logout", uid: "user-id", pswd: "uid-cipher-by-pswd", iv: "session-iv"}<br>
 * and session-header: {uid: “user-id”,  ssid: “session-id-plain/cipher”, sys: “module-id”}<br>
	 * Session object are required when login successfully, and removed automatically.
	 * When removing, the SUser object is removed via session lisenter.<br>
	 * Be careful with servlet session - created via getSessionId():<br>
	 * https://stackoverflow.com/questions/2255814/can-i-turn-off-the-httpsession-in-web-xml
 * @author ody
 */
@WebServlet(description = "session manager", urlPatterns = { "/login.serv" })
public class SSession extends HttpServlet implements ISessionVerifier {
	/** * */
	private static final long serialVersionUID = 1L;

	static String rootK = null;
	
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
	 * @throws SAXException
	 * @throws IOException
	 */
	public static void init(DATranscxt daSctx, ServletContext ctx) throws SAXException, IOException {
		rootK = ctx.getInitParameter("io.oz.root-key");

		lock = new ReentrantLock();

		sctx = daSctx;
		DATranscxt.initConfigs(daSctx.basiconnId(),
				FilenameUtils.concat(JSingleton.rootINF(), "semantic-log.xml"));

		jreqHelper = new JHelper<SessionReq>();

		users = new HashMap<String, IUser>();
		// see https://stackoverflow.com/questions/34202701/how-to-stop-a-scheduledexecutorservice
		//scheduler = Executors.newSingleThreadScheduledExecutor();
		scheduler = Executors.newScheduledThreadPool(1);

		int m = 20;
		try { m = Integer.valueOf(Configs.getCfg("ss-timeout-min"));} catch (Exception e) {}
		if (ServFlags.session)
			Utils.warn("SSession debug mode true (ServFlage.session)");

        schedualed = scheduler.scheduleAtFixedRate(
        		new SessionChecker(users, m),
        		0, 1, TimeUnit.MINUTES);
	}
	
	/**Stop all threads that were scheduled by IrSession.
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
	 * @author ody
	 */
	public static class UserMeta {
		/**key in config.xml for class name, this class implementing IUser is used as user object's type. */
		static String clzz = "class-IUser";
		static String tbl = "a_user";
		static String uidField = "userId";
		static String unameField = "userName";
		static String pswdField = "pwd";
		static String ivField = "encAuxiliary";

		public static UserMeta config() { return new UserMeta(); }

		public UserMeta userName(String unamefield) {
			unameField = unamefield;
			return this;
		}

		public UserMeta iv(String ivfield) {
			ivField = ivfield;
			return this;
		}

		public UserMeta pswd(String pswdfield) {
			pswdField = pswdfield;
			return this;
		}
	}

	
	/**FIXME: This is a security breach. Client request can duplicated request with plain ssid and uid.<br>
	 * Should we use a session key?
	 * @param jHeader
	 * @return {@link JUser} if succeed, which can be used for db logging
	 * - use this to load functions, etc.
	 * @throws SsException Session checking failed.
	 * @throws SQLException Reqest payload header.usrAct is null 
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
				// return new DbLog(usr, jHeader);
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
				IUser usr = verify(jreqHelper.readHeader(headstr));
				SemanticObject ok = new SemanticObject();
				ok.put(usr.uid(), (SemanticObject)usr);
				resp.getWriter().write(Html.map(ok));
			}
//			else if ("init".equals(t)) {
//				String k = request.getParameter("k");
//				rootK = k;
//				String r = String.format("Root key re-initialized: %s%s",
//						k.substring(0, 1), k.replaceAll(".", "*"));
//				Utils.warn(r);
//				resp.getWriter().write(Html.ok(r));
//			}
			else {
				//msg.err("Login.serv using GET to touch session info - use POST to login, logout, check session.");
				ServletAdapter.write(resp, JProtocol.err(Port.session, MsgCode.exGeneral,
						"Login.serv using GET to touch session info - use POST to login, logout, check session."));
			}
		} catch (SsException e) {
			ServletAdapter.write(resp, JProtocol.err(Port.session, e.code, e.getMessage()));
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
						ServletAdapter.write(response, JProtocol.ok(Port.session, (SemanticObject)login));
					}
					else throw new SsException("passwords not matching - pswd = encrypt(uid, pswd, iv)");
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
						ServletAdapter.write(response, JProtocol.ok(Port.session, resp));
					}
					else
						ServletAdapter.write(response, JProtocol.ok(Port.session,
								new SemanticObject().put("msg", "But no such session exists.")));
				}
				else {
					String t = req.getParameter("t");
					if (t != null) t = t.toLowerCase().trim();
					if ("ping".equals(t) || "touch".equals(t)) {
						JHeader header = payload.header();
						verify(header);
						ServletAdapter.write(response, JProtocol.ok(Port.session, ""));
					}
					else throw new SsException ("Session Request not supported: a=%s", a);
				}
			}
			else throw new SsException ("Session request not supported: login-obj=null");
		} catch (SsException | TransException e) {
			ServletAdapter.write(response, JProtocol.err(Port.session, MsgCode.exSession, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			ServletAdapter.write(response, JProtocol.err(Port.session, MsgCode.exGeneral, e.getMessage()));
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
	 */
	private IUser loadUser(SessionReq jreq, String connId)
			throws TransException, SQLException, SsException, ReflectiveOperationException {
		SResultset rs = (SResultset) sctx.select(UserMeta.tbl, "u")
			.col(UserMeta.uidField, "uid")
			.col(UserMeta.unameField, "uname")
			.col(UserMeta.pswdField, "pswd")
			.col(UserMeta.ivField, "iv")
			// .col(UserMeta.urlField, "url")
			.where("=", "u." + UserMeta.uidField, "'" + jreq.uid() + "'")
			.rs(sctx.instancontxt(jrobot));
		
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
	 */
	private static IUser createUser(String clsNamekey, String uid, String pswd, String iv, String userName)
			throws ReflectiveOperationException, SemanticException {
		if (!Configs.hasCfg(clsNamekey))
			throw new SemanticException("No class name configured for creating user information, check config.xml/k=%s", clsNamekey);
		String clsname = Configs.getCfg(clsNamekey);
		if (clsname == null)
			throw new SemanticException("No class name configured for creating user information, check config.xml/k=%s", clsNamekey);

		@SuppressWarnings("unchecked")
		Class<IUser> cls = (Class<IUser>) Class.forName(clsname);
		Constructor<IUser> constructor = null;
		try {
			constructor = cls.getConstructor(String.class, String.class, String.class, String.class);
		} catch (NoSuchMethodException ne) {
			throw new SemanticException("Class %s needs a consturctor like SUser(String uid, String pswd, String iv, String usrName).", "clsname");
		}
		try {
			return (IUser) constructor.newInstance(uid, pswd, iv, userName);
		} catch (InvocationTargetException ie) {
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
