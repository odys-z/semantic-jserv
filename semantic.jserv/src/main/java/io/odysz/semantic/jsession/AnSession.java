package io.odysz.semantic.jsession;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.AESHelper;
import io.odysz.common.Configs;
import io.odysz.common.LangExt;
import io.odysz.common.Radix64;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.JRobot;
import io.odysz.semantic.jserv.ServFlags;
import io.odysz.semantic.jserv.ServPort;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantics.IUser;
import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.SessionInf;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

/**
 * <h5>1. Handle login-obj: {@link AnSessionReq}.</h5>
 *  <p>a: "login | logout | pswd | init | ping(touch)",<br>
 *  uid: "user-id",<br>
 *  pswd: "uid-cipher-by-pswd",<br>
 *  iv: "session-iv"</p>
 *
 * <h5>2. Session verifying using session-header</h5>
 * <p>uid: “user-id”,<br>
 * ssid: “session-id-plain/cipher”,<br>
 * sys: “module-id”</p>
 * <p>Session object are required when login successfully, and removed automatically.
 * When removing, the {@link JUser} object is removed via session lisenter.</p>
 * <p><b>Note:</b></p>Session header is post by client in HTTP request's body other than in HTTP header.
 * It's HTTP body payload, understood by semantic-jserv as a request header semantically.</p>
 * <p>Also don't confused with servlet session - created via getSessionId(),
 * <br>and you'd better
 * <a href='https://stackoverflow.com/questions/2255814/can-i-turn-off-the-httpsession-in-web-xml'>turn off it</a>.</p>
 *
 * <h5>3. User action can be logged with session information</h5>
 * <p>AnSession requires loggings sql semantics explicitly defined in "semantic-log.xml",
 * with file name hard coded.</p>
 * <p>Logging can be disabled by connection configuration.
 * Each connection requiring loggin must have a table named "a_logs".</p>
 *
 * @author odys-z@github.com
 */
@WebServlet(description = "session manager", urlPatterns = { "/login.serv11" })
public class AnSession extends ServPort<AnSessionReq> implements ISessionVerifier {
	public AnSession() {
		super(Port.session);
	}

	private static final long serialVersionUID = 1L;

	public static enum Notify { changePswd, todo }

	/**[session-id, SUser]*/
	static HashMap<String, IUser> users;

	private static ScheduledExecutorService scheduler;

	/**session pool reentrant lock*/
	public static ReentrantLock lock;

	/** Session checking task buffer */
	private static ScheduledFuture<?> schedualed;

	static DATranscxt sctx;

	/** key of JUser class name, "class-IUser" used in config.xml */
	public static final String usrClzz = "class-IUser";

	private static JUserMeta usrMeta;

	IUser jrobot = new JRobot();

	/**Initialize semantext, schedule tasks,
	 * load root key from tomcat context.xml.
	 * To configure root key in tomcat, in context.xml, <pre>
	&lt;Context&gt;
		&lt;Parameter name="io.oz.root-key" value="*************" override="false"/&gt;
	&lt;/Context&gt;</pre>
	 * @param daSctx
	 * @throws SAXException something wrong with configuration files
	 * @throws IOException file accessing failed
	 * @throws SemanticException semantics error
	 * @throws SQLException database accessing error
	 */
	public static void init(DATranscxt daSctx)
			throws SAXException, IOException, SemanticException, SQLException {
		sctx = daSctx;

		lock = new ReentrantLock();

		/*
		String conn = daSctx.getSysConnId();
		if (!DATranscxt.alreadyLoaded(conn)) {
			Utils.logi("Initializing session based on connection %s, basic session tables, users, functions, roles, should located here.", conn);
			DATranscxt.loadSemantics(conn);
						// JSingleton.getFileInfPath(JUser.sessionSmtXml), daSctx.getSysDebug());
		}
		*/

		users = new HashMap<String, IUser>();
		// see https://stackoverflow.com/questions/34202701/how-to-stop-a-scheduledexecutorservice
		scheduler = Executors.newScheduledThreadPool(1);

		try {
			IUser tmp = createUser(usrClzz, "temp", "pswd", null, "temp user");
			usrMeta = (JUserMeta) tmp.meta(daSctx.getSysConnId());
		}
		catch (Exception ex) {
			Utils.warn("SSesion: Implementation class of IUser doesn't be configured correctly in: config.xml/t[id=default]/k=%s, check the value.",
					usrClzz);
			ex.printStackTrace();
		}

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

	/**FIXME where is token verification?
	 * @param anHeader
	 * @return {@link JUser} if succeed, which can be used for db logging
	 * - use this to load functions, etc.
	 * @throws SsException Session checking failed.
	 * @throws SQLException Reqest payload header.usrAct is null (TODO sure?)
	 */
	@Override
	public IUser verify(AnsonHeader anHeader) throws SsException {
		if (anHeader == null)
			throw new SsException("session header is missing");

		String ssid = (String)anHeader.ssid();
		if (users.containsKey(ssid)) {
			IUser usr = users.get(ssid);
			String slogid = (String)anHeader.logid();
			// FIXME Album can not be published without fixing this
			// FIXME
			// FIXME
			// FIXME token = (string)anheader.token;
			// FIXME if (token != null && token.equals(usr.untoken())) {
			// FIXME
			if (slogid != null && slogid.equals(usr.uid())) {
				return usr.touch();
			}
			else throw new SsException("session token is not matching");
		}
		else throw new SsException("session info is missing or timeout");
	}

	public static IUser getUser(SemanticObject jheader) {
		return users.get(jheader.get("ssid"));
	}

	@Override
	protected void onGet(AnsonMsg<AnSessionReq> msg, HttpServletResponse resp)
			throws ServletException, IOException, AnsonException, SemanticException {
		jsonResp(msg, resp);
	}

	@Override
	protected void onPost(AnsonMsg<AnSessionReq> msg, HttpServletResponse resp) throws IOException {
		jsonResp(msg, resp);
	}

	protected void jsonResp(AnsonMsg<AnSessionReq> msg, HttpServletResponse response) throws IOException {
		try {
			String connId = Connects.defltConn();
			if (connId == null || connId.trim().length() == 0)
				connId = Connects.defltConn();

			// find user and check login info
			// request-obj: {a: "login/logout", uid: "user-id", pswd: "uid-cipher-by-pswd", iv: "session-iv"}
			if (msg != null) {
				AnSessionReq sessionBody = msg.body(0);
				String a = sessionBody.a();
				if ("login".equals(a)) {
					IUser login = loadUser(sessionBody, connId);
					if (login.login(sessionBody)) {
						lock.lock();
						login.sessionId(allocateSsid());
						users.put(login.sessionId(), login);
						lock.unlock();

						SessionInf ssinf = login.getClientSessionInf(login);
						AnSessionResp bd = new AnSessionResp(null, ssinf);
						AnsonMsg<AnSessionResp> rspMsg = ok(bd);
						write(response, rspMsg, msg.opts());
					}
					else throw new SsException(
							"Password doesn't match!\\n"
							+ "Additional Details: %s",
							login.notifies() != null && login.notifies().size() > 0 ? login.notifies().get(0) : "");
				}
				else if ("logout".equals(a)) {
					AnsonHeader header = msg.header();
					try {verify(header);}
					catch (SsException sx) {} // logout anyway if session check is failed
					// {uid: “user-id”,  ssid: “session-id-plain/cipher”, vi: "vi-b64"<, sys: “module-id”>}
					String ssid = (String) header.ssid();

					lock.lock();
					IUser usr = users.remove(ssid);
					lock.unlock();

					if (usr != null) {
						SemanticObject resp = usr.logout();
						write(response, AnsonMsg.ok(p, resp.msg()),
								msg.opts());
					}
					else
						write(response, AnsonMsg.ok(p, "But no such session exists."),
								msg.opts());
				}
				else if ("pswd".equals(a)) {
					// change password
					AnsonHeader header = msg.header();
					IUser usr = verify(header);

					// dencrypt field of a_user.userId: pswd, encAuxiliary
					if (!DATranscxt.hasSemantics(connId, usrMeta.tbl, smtype.dencrypt)) {
						throw new SemanticException("Can't update pswd, because data entry %s is not protected by semantics %s",
								usrMeta.tbl, smtype.dencrypt.name());
					}

					// client: encrypt with ssid, send cipher with iv
					// FIXME using of session key, see bug of verify()
					String ssid = (String) header.ssid();
					String iv64 = sessionBody.md("iv_pswd");

					// check old password
					if (!usr.guessPswd(sessionBody.md("oldpswd"), sessionBody.md("iv_old")))
						throw new SemanticException("Can not verify old password!");

					sctx.update(usrMeta.tbl, usr)
						.nv(usrMeta.pswd, sessionBody.md("pswd")) // depends on semantics: dencrypt
						.nv(usrMeta.iv, iv64)
						.whereEq(usrMeta.pk, usr.uid())
						.u(sctx.instancontxt(sctx.getSysConnId(), usr));

					// ok, logout
					lock.lock();
					users.remove(ssid);
					lock.unlock();

					write(response, ok("You must relogin!"));
				}
				else if ("init".equals(a)) {
					// reset password
					AnsonHeader header = msg.header();

					if (!DATranscxt.hasSemantics(connId, usrMeta.tbl, smtype.dencrypt)) {
						throw new SemanticException("Can't update pswd, because data entry %s is not protected by semantics %s",
								usrMeta.tbl, smtype.dencrypt.name());
					}

					String ssid = (String) header.ssid();
					String iv64 = sessionBody.md("iv_pswd");
					String newPswd = sessionBody.md("pswd");

					// check his IV
					SemanticObject s = sctx.select(usrMeta.tbl, "u")
							.col(usrMeta.iv, "iv")
							.where_("=", "u." + usrMeta.pk, sessionBody.uid())
							.rs(sctx.instancontxt(sctx.getSysConnId(), jrobot));

					AnResultset rs = (AnResultset) s.rs(0);;
					if (rs.beforeFirst().next()) {
						String iv = rs.getString("iv");
						if (!LangExt.isEmpty(iv))
							throw new SemanticException("Can't update pswd, because it is not allowed to change.");
					}

					// set a new pswd
					String pswd2 = AESHelper.decrypt(newPswd, jrobot.sessionId(), AESHelper.decode64(iv64));
					Utils.logi("intialize pswd: %s", pswd2);

					sctx.update(usrMeta.tbl, jrobot)
						.nv(usrMeta.pswd, pswd2)
						.nv(usrMeta.iv, iv64)
						.whereEq(usrMeta.pk, header.logid())
						.u(sctx.instancontxt(sctx.getSysConnId(), jrobot));

					// remove session if logged in
					if (users.containsKey(ssid)) {
						// This happens when log on users IV been reset
						lock.lock();
						users.remove(ssid);
						lock.unlock();
						write(response, ok("You must re-login!"));
					}
					else {
						write(response, ok("Initializing password successed."));
					}
				}
				else {
					if (a != null) a = a.toLowerCase().trim();
					if ("ping".equals(a) || "touch".equals(a)) {
						AnsonHeader header = msg.header();
						verify(header);
						write(response, AnsonMsg.ok(p, ""), msg.opts());
					}
					else throw new SsException ("Session Request not supported: a=%s", a);
				}
			}
			else throw new SsException ("Session request not supported: request body is null");
		} catch (SsException | TransException e) {
			write(response, err(MsgCode.exSession, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			write(response, err(MsgCode.exGeneral, e.getMessage()));
		} finally {
		}
	}

	public static String allocateSsid() {
		Random random = new Random();
		// 2 ^ 48 = 64 ^ 8
		String ssid = Radix64.toString((long)random.nextInt() * (short)random.nextInt(), 8);
		while (users != null && users.containsKey(ssid))
			ssid = Radix64.toString((long)random.nextInt() * (short)random.nextInt(), 8);
		return ssid;
	}

	/**Load user instance form DB table (name = {@link UserMeta#tbl}).
	 * @param sessionBody
	 * @param connId
	 * @return new IUser instance loaded from database (from connId), see {@link #createUser(String, String, String, String, String)}
	 * @throws TransException
	 * @throws SQLException
	 * @throws SsException
	 * @throws ReflectiveOperationException
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	private IUser loadUser(AnSessionReq sessionBody, String connId)
			throws TransException, SQLException, SsException,
			ReflectiveOperationException, GeneralSecurityException, IOException {
		SemanticObject s = sctx.select(usrMeta.tbl, "u")
			.je("u", usrMeta.roleTbl, "r", usrMeta.role)
			.je("u", usrMeta.orgTbl, "o", usrMeta.org)
			/*
			.col(usrMeta.pk)
			.col(usrMeta.uname)
			.col(usrMeta.pswd)
			.col(usrMeta.iv)
			*/
			.col("u.*")
			.col(usrMeta.orgName)       // v1.4.11
			.col(usrMeta.roleName)		// v1.4.11
			.where_("=", "u." + usrMeta.pk, sessionBody.uid())
			.rs(sctx.instancontxt(sctx.getSysConnId(), jrobot));

		AnResultset rs = (AnResultset) s.rs(0);;
		if (rs.beforeFirst().next()) {
			String uid = rs.getString(usrMeta.pk);
			IUser obj = createUser(usrClzz, uid,
							rs.getString(usrMeta.pswd), rs.getString(usrMeta.iv), rs.getString(usrMeta.uname))
						.onCreate(rs) // v1.4.11
						.onCreate(sessionBody)
						.touch();
			if (obj instanceof SemanticObject)
				return obj;
			throw new SemanticException("IUser implementation must extend SemanticObject.");
		}
		else
			throw new SsException("User Id not found: ", sessionBody.uid());
	}

	/**Create a new IUser instance, where the class name is configured in config.xml/k=class-IUser.
	 * For the sample project, jserv-sample coming with this lib, it's configured as <a href='https://github.com/odys-z/semantic-jserv/blob/master/jserv-sample/src/main/webapp/WEB-INF/config.xml'>
	 * io.odysz.jsample.SampleUser</a>
	 * @param clsNamekey class name
	 * @param uid user id
	 * @param pswd
	 * @param iv auxiliary encryption field
	 * @param userName
	 * @return new IUser instance, if the use's IV is empty, will create a notification of {@link Notify#changePswd}.
	 * @throws ReflectiveOperationException
	 * @throws IOException
	 * @throws GeneralSecurityException
	 * @throws TransException notifying message failed
	 * @throws IllegalArgumentException
	 */
	private static IUser createUser(String clsNamekey, String uid, String pswd, String iv, String userName)
			throws ReflectiveOperationException, GeneralSecurityException, IOException, IllegalArgumentException, TransException {
		if (!Configs.hasCfg(clsNamekey))
			throw new SemanticException("No class name configured for creating user information, check config.xml/k=%s", clsNamekey);
		String clsname = Configs.getCfg(clsNamekey);
		if (clsname == null)
			throw new SemanticException("No class name configured for creating user information, check config.xml/k=%s", clsNamekey);

		@SuppressWarnings("unchecked")
		Class<IUser> cls = (Class<IUser>) Class.forName(clsname);
		Constructor<IUser> constructor = null;

		try {
			constructor = cls.getConstructor(String.class, String.class, String.class);
		} catch (NoSuchMethodException ne) {
			throw new SemanticException("Class %s needs a consturctor like JUser(String, String, String).", cls.getTypeName());
		}

		try {
			if (!LangExt.isblank(iv)) {
				// still can be wrong with messed up data, e.g. with iv and plain pswd
				try {
					pswd = AESHelper.decrypt(pswd,
							DATranscxt.key("user-pswd"), AESHelper.decode64(iv));
				} catch (Throwable e) {
					String rootkey = DATranscxt.key("user-pswd");
					Utils.warn("Decrypting user pswd failed. cipher: %s, iv %s, rootkey: *(%s)",
							pswd, iv == null ? null : AESHelper.decode64(iv),
							rootkey == null ? null : rootkey.length());
					
					if (rootkey == null)
						Utils.warn("The rootkey can be configured either with context.xml or set like the way of JSingleton.initjserv().\n\t%s\n\t%s",
							"context.xml example: https://github.com/odys-z/semantic-jserv/blob/master/jserv-sample/src/main/webapp/META-INF/context.xml",
							"JSingleton.initJserv() example: https://github.com/odys-z/semantic-jserv/blob/20acb2f9a5397f96927a5e768263ccd3088e1a85/jserv-album/src/main/java/io/oz/album/JettyApp.java#L45");
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

}
