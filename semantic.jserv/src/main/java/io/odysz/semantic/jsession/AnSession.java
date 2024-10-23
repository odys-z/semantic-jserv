package io.odysz.semantic.jsession;

import static io.odysz.common.AESHelper.*;
import static io.odysz.common.LangExt.isblank;
import static io.odysz.semantic.jsession.AnSessionReq.A.init;
import static io.odysz.semantic.jsession.AnSessionReq.A.login;
import static io.odysz.semantic.jsession.AnSessionReq.A.logout;
import static io.odysz.semantic.jsession.AnSessionReq.A.ping;
import static io.odysz.semantic.jsession.AnSessionReq.A.pswd;
import static io.odysz.semantic.jsession.AnSessionReq.A.touch;

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
import io.odysz.common.Configs.keys;
import io.odysz.common.LangExt;
import io.odysz.common.Radix64;
import io.odysz.common.Utils;
import io.odysz.module.rs.AnResultset;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
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
 *  <p>a = {@link AnSessionReq.A}</p>
 *  <p>for a = A.login,<br>
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
@WebServlet(description = "session manager", urlPatterns = { "/login.serv" })
public class AnSession extends ServPort<AnSessionReq> implements ISessionVerifier {
	private static final long serialVersionUID = 1L;

	public static final String disableTokenKey = "disable-token";

	public static enum Notify { changePswd, todo }
	
	protected static DATranscxt sctx;

	/**[session-id, SUser]*/
	static HashMap<String, IUser> users;

	private static ScheduledExecutorService scheduler;

	/**session pool reentrant lock*/
	public static ReentrantLock lock;

	/** Session checking task buffer */
	private static ScheduledFuture<?> schedualed;

	private static JUserMeta usrMeta;

	IUser jrobot = new JRobot();

	/**
	 * Initialize semantext, schedule tasks,
	 * load root key from tomcat context.xml.
	 * To configure root key in tomcat, in context.xml, <pre>
	 * &lt;Context&gt;
	 *   &lt;Parameter name="io.oz.root-key" value="*************" override="false"/&gt;
	 * &lt;/Context&gt;</pre>
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

		users = new HashMap<String, IUser>();
		// see https://stackoverflow.com/questions/34202701/how-to-stop-a-scheduledexecutorservice
		scheduler = Executors.newScheduledThreadPool(1);

		try {
			IUser tmp = createUser(keys.usrClzz, "temp", "pswd", null, "temp user");
			// usrMeta = (JUserMeta) tmp.meta(daSctx.getSysConnId());
			usrMeta = (JUserMeta) tmp.meta();
		}
		catch (Exception ex) {
			Utils.warn("SSesion: Implementation class of IUser hasn't been configured correctly in: %s/t[id=%s]/k=%s, check the value.",
					Configs.cfgFullpath, Configs.keys.deftXTableId, Configs.keys.usrClzz);
			ex.printStackTrace();
		}

		int m = 20;
		try { m = Integer.valueOf(Configs.getCfg("ss-timeout-min"));} catch (Exception e) {}
		if (ServFlags.session) {
			Utils.logi("[AnSession] timeout = %s minutes (configure: %s)",
					m, Configs.cfgFullpath);
			Utils.warn("[ServFlags.session] SSession debug mode true (ServFlage.session)");
		}

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

	private boolean verfiyToken;

	/** url pattern: /login.serv */
	public AnSession() {
		this(true);
	}

	public AnSession(boolean verifyToken) {
		super(Port.session);
		verfiyToken = verifyToken;
		if (!verfiyToken)
			Utils.warn("Verifying token is recommended but is disabled by config.xml/k=%s", disableTokenKey);
	}

	/**
	 * Session Verification
	 * 
	 * @param anHeader
	 * @param seq not used (Semantic-* is not planned to support replay attack prevention)
	 * @return {@link JUser} if succeed, which can be used for db logging
	 * - use this to load functions, etc.
	 * @throws SsException Session checking failed.
	 */
	@Override
	public IUser verify(AnsonHeader anHeader, int ...seq) throws SsException {
		if (anHeader == null)
			throw new SsException("session header is missing");

		String ssid = (String)anHeader.ssid();
		if (users.containsKey(ssid)) {
			IUser usr = users.get(ssid);

			if (verfiyToken) {
				touchSessionToken(usr, anHeader.token(), usr.sessionKey());
			}
			else
				usr.touch();
			return usr;
		}
		else throw new SsException("Session info is missing or timeout.");
	}

	/**
	 * 
	 * @param usr
	 * @param clientoken
	 * @param knowledge 
	 * @return true if session token is valid.
	 * @throws SsException 
	 */
	static void touchSessionToken(IUser usr, String clientoken, String knowledge) throws SsException {
		try {
			if (!AESHelper.verifyToken(clientoken, knowledge, usr.uid(), usr.pswd()))
				throw new SsException("Tokens are not matching");
			usr.touch();
		} catch (GeneralSecurityException | IOException e) {
			e.printStackTrace();
			throw new SsException("Can not decrypt token: %s. %s", clientoken, e.getMessage());
		} catch (Exception e) {
			throw new SsException("Error while verifying tokens: %s. %s", clientoken, e.getMessage());
		}
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
		String connId = null;
		try {
			if (msg != null) {
				if (isblank(msg.body(0).uri()))
					throw new SsException("Since 2.0.0, client uri cannot be empty for session checking, logging in, etc.");

				if (msg != null)
					connId = Connects.uri2conn(msg.body(0).uri());

				if (isblank(connId))
					throw new SsException("Since 2.0.0, connection id for logging is mandatory. See uri(%s) - connId mappings in connects.xml.",
							msg.body(0).uri());

				// find user and check login info
				// request-obj: {a: "login/logout", uid: "user-id", pswd: "uid-cipher-by-pswd", iv: "session-iv"}
				AnSessionReq sessionBody = msg.body(0);
				String a = sessionBody.a();
				if (login.equals(a)) {
					IUser login = loadUser(sessionBody, connId);
					if (login.login(sessionBody)) {
						try {
							lock.lock();
							login.sessionId(allocateSsid());
							users.put(login.sessionId(), login);
						} finally { lock.unlock();}

						SessionInf ssinf = login.getClientSessionInf(login);
						AnSessionResp bd = new AnSessionResp(null, ssinf).profile(login.profile());
						AnsonMsg<AnSessionResp> rspMsg = ok(bd);
						write(response, rspMsg, msg.opts());
					}
					else throw new SsException(
							"Password doesn't match!\\n"
							+ "Additional Details: %s",
							login.notifies() != null && login.notifies().size() > 0 ? login.notifies().get(0) : "");
				}
				else if (logout.equals(a)) {
					AnsonHeader header = msg.header();
					try {verify(header);}
					catch (SsException sx) {} // logout anyway if session check is failed
					// {uid: “user-id”,  ssid: “session-id-plain/cipher”, vi: "vi-b64"<, sys: “module-id”>}
					String ssid = (String) header.ssid();

					try {
						lock.lock();
						IUser usr = users.remove(ssid);

						if (usr != null) {
							SemanticObject resp = usr.logout();
							write(response, AnsonMsg.ok(p, resp.msg()),
									msg.opts());
						}
						else
							write(response, AnsonMsg.ok(p, "But no such session exists."),
									msg.opts());
					} finally { lock.unlock(); }
				}
				else if (pswd.equals(a)) {
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
						.u(sctx.instancontxt(connId, usr));

					// ok, logout
					try {
						lock.lock();
						users.remove(ssid);
					} finally { lock.unlock(); }

					write(response, ok("You must re-login!"));
				}
				else if (init.equals(a)) {
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
							// .rs(sctx.instancontxt(sctx.getSysConnId(), jrobot));
							.rs(sctx.instancontxt(connId, jrobot));

					AnResultset rs = (AnResultset) s.rs(0);;
					if (rs.beforeFirst().next()) {
						String iv = rs.getString("iv");
						if (!LangExt.isEmpty(iv))
							throw new SemanticException("Can't update pswd, because it is not allowed to change.");
					}

					// set a new pswd
					String pswd2 = decrypt(newPswd, jrobot.sessionId(), decode64(iv64));
					Utils.logi("intialize pswd: %s", pswd2);

					sctx.update(usrMeta.tbl, jrobot)
						.nv(usrMeta.pswd, pswd2)
						.nv(usrMeta.iv, iv64)
						.whereEq(usrMeta.pk, header.logid())
						// .u(sctx.instancontxt(sctx.getSysConnId(), jrobot));
						.u(sctx.instancontxt(connId, jrobot));

					// remove session if logged in
					if (users.containsKey(ssid)) {
						// This happens when log on users IV been reset
						try {
							lock.lock();
							users.remove(ssid);
						} finally {lock.unlock();}
						write(response, ok("You must re-login!"));
					}
					else {
						write(response, ok("Initializing password successed."));
					}
				}
				else {
					if (a != null) a = a.toLowerCase().trim();
					if (ping.equals(a) || touch.equals(a)) {
						AnsonHeader header = msg.header();
						verify(header);
						// write(response, AnsonMsg.ok(p, sctx.getSysConnId()), msg.opts());
						write(response, AnsonMsg.ok(p, connId), msg.opts());
					}
					else throw new SsException ("Session Request not supported: a=%s", a);
				}
			}
			else throw new SsException ("Session request not supported: request body is null");
		} catch (SsException | TransException e) {
			write(response, err(MsgCode.exSession, e.getMessage()).uri(connId));
		} catch (Exception e) {
			e.printStackTrace();
			write(response, err(MsgCode.exGeneral, e.getMessage()).uri(connId));
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

	/**
	 * Load user instance form DB table (name = {@link UserMeta#tbl}).
	 * <p>Since 2.0.0, uses left join to a_orgs and a_roles from a_users.</p>
	 * 
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
		return loadUser(sessionBody.uid(), connId, jrobot)
				.onCreate(sessionBody);
	}
	
	/**
	 * 
	 * @since 2.0.0
	 * @param sessionBody
	 * @param connId
	 * @param jrobt
	 * @return user object
	 * @throws TransException
	 * @throws SQLException
	 * @throws SsException
	 * @throws ReflectiveOperationException
	 * @throws GeneralSecurityException
	 * @throws IOException
	 */
	public static IUser loadUser(String uid, String connId, IUser jrobt)
			throws TransException, SQLException, SsException,
			ReflectiveOperationException, GeneralSecurityException, IOException {
		SemanticObject s = sctx.select(usrMeta.tbl, "u")
			.l_(usrMeta.rm.tbl, "r", usrMeta.role, "roleId")
			.l_(usrMeta.om.tbl, "o", usrMeta.org, "orgId")
			.col("u.*")
			.col(usrMeta.orgName)       // v1.4.11
			.col(usrMeta.roleName)		// v1.4.11
			.whereEq("u." + usrMeta.pk, uid)
			.rs(sctx.instancontxt(connId, jrobt));

		AnResultset rs = (AnResultset) s.rs(0);;
		if (rs.beforeFirst().next()) {
			IUser obj = createUser(keys.usrClzz, uid,
							rs.getString(usrMeta.pswd),
							rs.getString(usrMeta.iv),
							rs.getString(usrMeta.uname))
						.onCreate(rs) // v1.4.11
						// .onCreate(sessionBody)
						.touch();
			if (obj instanceof SemanticObject)
				return obj;
			throw new SemanticException("IUser implementation must extend SemanticObject.");
		}
		else
			throw new SsException("User Id is not found: %s", uid);
	}


	/**
	 * Create a new IUser instance, where the class name is configured in config.xml/k=class-IUser.
	 * For the sample project, jserv-sample coming with this lib, it's configured as
	 * <a href='https://github.com/odys-z/semantic-jserv/blob/master/jserv-sample/src/main/webapp/WEB-INF/config.xml'>
	 * io.odysz.jsample.SampleUser</a>
	 * 
	 * @param clsNamekey class name, since 1.4.36, this name can be class name itself
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
	static IUser createUser(String clsNamekey, String uid, String pswd, String iv, String userName)
			throws ReflectiveOperationException, GeneralSecurityException, IOException, IllegalArgumentException, TransException {
		if (!Configs.hasCfg(clsNamekey))
			throw new SemanticException("No class name configured for creating user information, check config.xml/k=%s", clsNamekey);
		String clsname = Configs.getCfg(clsNamekey);
		if (clsname == null)
			return createUserByClassname(clsNamekey, uid, pswd, iv, userName);
		else 
			return createUserByClassname(clsname, uid, pswd, iv, userName);

	}

	static IUser createUserByClassname(String clsname, String uid, String pswd, String iv, String userName) 
			throws ReflectiveOperationException, GeneralSecurityException, IOException, IllegalArgumentException, TransException {
		@SuppressWarnings("unchecked")
		Class<IUser> cls = (Class<IUser>) Class.forName(clsname);
		Constructor<IUser> constructor = null;

		try {
			constructor = cls.getConstructor(String.class, String.class, String.class);
		} catch (NoSuchMethodException ne) {
			throw new SemanticException("Class %s needs a consturctor like JUser(String uid, String pswd, String usrName).", cls.getTypeName());
		}

		try {
			if (!LangExt.isblank(iv)) {
				// still can be wrong with messed up data, e.g. with iv and plain pswd
				try {
					pswd = decrypt(pswd,
							DATranscxt.key("user-pswd"), decode64(iv));
				} catch (Throwable e) {
					String rootkey = DATranscxt.key("user-pswd");
					Utils.warn("Decrypting user pswd failed. cipher: %s, iv %s, rootkey: *(%s)",
							pswd, iv == null ? null : decode64(iv),
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
