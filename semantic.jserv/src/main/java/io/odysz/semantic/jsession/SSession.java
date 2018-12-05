package io.odysz.semantic.jsession;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Random;
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

import io.odysz.common.Configs;
import io.odysz.common.Radix64;
import io.odysz.common.Utils;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantics.SemanticObject;
import io.odysz.transact.x.TransException;
import io.odysz.module.rs.SResultset;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.DA.DATranscxt;
import io.odysz.semantic.jprotocol.JHelper;
import io.odysz.semantic.jserv.JSingleton;
import io.odysz.semantic.jserv.SQuery;
import io.odysz.semantic.jserv.ServFlags;

/**Handle login-obj: {a: "login/logout", uid: "user-id", pswd: "uid-cipher-by-pswd", iv: "session-iv"}<br>
 * and session-header: {uid: “user-id”,  ssid: “session-id-plain/cipher”, sys: “module-id”}<br>
	 * Session object are required when login successfully, and removed automatically.
	 * When removing, the SUser object is removed via session lisenter.<br>
	 * Be careful with servlet session - created via getSessionId():<br>
	 * https://stackoverflow.com/questions/2255814/can-i-turn-off-the-httpsession-in-web-xml
 * @author ody
 */
@WebServlet(description = "session manager", urlPatterns = { "/login.serv" })
public class SSession extends HttpServlet {
	/** * */
	private static final long serialVersionUID = 1L;
	
	static final String ERR_UID = "uid_err";
	static final String ERR_PSWD = "pswd_err";
	/**Session error, session expired, wrong pswd, etc. */
	public static final String ERR_CHK = "ss_err";
	/**Identity error, id expired, duplicate, etc. */
	public static final String ERR_ID = "id_err";
	
	/**[session-id, SUser]*/
	static HashMap<String, SUser> users;

	private static ScheduledExecutorService scheduler;
	public static ReentrantLock lock;
	private static Random random;
	
	// FIXME - CLM needing presented here?
	/** a_functions,a_role_funcs,funcId,parentId*/
	private static String functionStrArr;
	/** a_functions,a_role_funcs,funcId,parentId */
	private static String functionStr[];

	private static ScheduledFuture<?> schedualed;
	
	private static DATranscxt sctx;

	public static void init(DATranscxt daSctx) {
		sctx = daSctx;

		functionStrArr = Configs.getCfg("function");
		if (functionStrArr != null)
			functionStr = functionStrArr.split(",");

		users = new HashMap<String, SUser>();
		// see https://stackoverflow.com/questions/34202701/how-to-stop-a-scheduledexecutorservice
		//scheduler = Executors.newSingleThreadScheduledExecutor();
		scheduler = Executors.newScheduledThreadPool(0);

		lock = new ReentrantLock();
		int m = 20;
		boolean debugMode = false;
		try { m = Integer.valueOf(Configs.getCfg("ss-timeout-min"));} catch (Exception e) {}
		try { debugMode = Configs.getBoolean("ss-timeout-min.debugMode");} catch (Exception e) {}
		if (debugMode)
			Utils.warn("IrSession debug mode true (config.xml/ss-timeout-min.debugMode");

        schedualed = scheduler.scheduleAtFixedRate(
        		new SessionChecker(users, m, debugMode),
        		0, 1, TimeUnit.MINUTES);
        
        random = new Random();
	}
	
	/**Stop all threads that were scheduled by IrSession.
	 * @param msDelay delay in milliseconds.
	 */
	public static void stopScheduled(int msDelay) {
		System.out.println("cancling session checker ... ");
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
	
	/**
	 * @param jHeader
	 * @return DbLog for really checking (check session with session id), else null for debug - only uid usable
	 * - use this to load functions, etc.
	 * @throws SsException Session checking failed.
	 * @throws SQLException Reqest payload header.usrAct is null 
	 */
	public static SUser check(SemanticObject jHeader) throws SsException, SQLException {
		if (jHeader == null)
			throw new SsException("session header is missing");

		// ignore debugging
		String debug = (String) jHeader.get("debug");
		if (debug != null && !debug.equals("false")) {
			return null;
		}

		// if ("admin".equals(ssHeader.get("uid"))) return false;

		String ssid = (String)jHeader.get("ssid");
		if (users.containsKey(ssid)) {
			SUser usr = users.get(ssid);
			String slogid = (String)jHeader.get("logid");
			if (slogid != null && slogid.equals(usr.getUserId())) {
				usr.touch();
				// return new DbLog(usr, jHeader);
				return usr;
			}
			else throw new SsException("session token is not matching");
		}
		else throw new SsException("session info is missing or timeout");
	}

	/**
	 * @param jHeader
	 * @return DbLog for really checking (check session with session id), else null for debug - only uid usable
	 * - use this to load functions, etc.
	 * @throws SsException Session checking failed.
	 * @throws SQLException Reqest payload header.usrAct is null 
	 */
//	public static DbLog check(SemanticObject jHeader) throws IrSessionException, SQLException {
//		if (jHeader == null)
//			throw new IrSessionException("session header is missing");
//
//		// ignore debugging
//		try {String debug = jHeader.getString("debug");
//			if (debug != null && !debug.equals("false")) {
//				return null;
//			}
//		} catch (Exception ex) {}
//
//		// if ("admin".equals(ssHeader.get("uid"))) return false;
//
//		String ssid = jHeader.getString("ssid");
//		if (users.containsKey(ssid)) {
//			SUser usr = users.get(ssid);
//			String slogid = jHeader.getString("logid");
//			if (slogid != null && slogid.equals(usr.getLogId())) {
//				usr.touch();
//				return new DbLog(usr, jHeader);
//			}
//			else throw new IrSessionException("session token is not matching");
//		}
//		else throw new IrSessionException("session info is missing or timeout");
//	}

	public static SUser getUser(SemanticObject jheader) {
		return users.get(jheader.get("ssid"));
	}


	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if (ServFlags.session) System.out.println("login get ========");
		response.setContentType("text/html;charset=UTF-8");
		JsonWriter writer = Json.createWriter(response.getOutputStream());
		try {
			String headstr = request.getParameter("header");
			SemanticObject jheader = null;
			if (headstr != null) {
				jheader = (SemanticObject) JHelper.parse(headstr);
				check(jheader);
			}
			else throw new SsException("Query session with GET request neending a header string.");

			String t = request.getParameter("t");
			String rootId = request.getParameter("root");
			if (rootId != null && rootId.trim().length() == 0)
				rootId = null;
			String conn = request.getParameter("conn");
			if ("query".equals(t)) {
				String ssid = request.getParameter("ssid");
				SemanticObject resp = respSessionInfo(ssid, conn, rootId);
				writer.write(resp);
			}
			else if ("touch".equals(t)) {
				// already touched by check()
				String logid = (String) jheader.get("logid");
				writer.write(JHelper.OK(logid, null));
			}
			else writer.write(JHelper.err("Login.serv using GET to query or touch session info - use POST to login, logout, check session."));
		} catch (SQLException e) {
			e.printStackTrace();
			if (writer != null)
				writer.write(JHelper.err(e.getMessage()));
		} catch (SsException e) {
			writer.write(JHelper.err(ERR_CHK, e.getMessage()));
		} catch (SAXException e) {
			e.printStackTrace();
		} finally {
			if (writer != null)
				writer.close();
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		if (ServFlags.session) System.out.println("login post ------");
		jsonResp(request, response);
	}

	private void jsonResp(HttpServletRequest request, HttpServletResponse response) throws IOException {
		response.setContentType("text/html;charset=UTF-8");
		JsonWriter writer = Json.createWriter(response.getOutputStream());
		try {
			String connId = request.getParameter("conn");
			if (connId == null || connId.trim().length() == 0)
				connId = Connects.defltConn();

			String payload = JHelper.getPayloadString(request);
			// find user and check login info String payload = JsonHelper.getPayloadString(request);
			// request-obj: {a: "login/logout", uid: "user-id", pswd: "uid-cipher-by-pswd", iv: "session-iv"}
			SemanticObject jlogin = null;
			if (payload != null && payload.trim().length() > 0)
				jlogin = (SemanticObject)JHelper.parse(payload);
			else throw new SQLException ("Session request not supported: login-obj=null");

			String t = request.getParameter("t");
			String rootId = request.getParameter("root");
			if (rootId != null && rootId.trim().length() == 0)
				rootId = null;
			String a = (String) jlogin.get("a");
			SemanticObject header = null;
			try {header = (SemanticObject) jlogin.get("header"); }
			catch (Exception e) {throw new SsException("request header not understandable");}

			if ("query".equals(t)) {
				// get function tree
				SemanticObject resp;
				DbLog dblog = check(header);
				if (dblog != null) // login
					resp = respSessionInfo((String)header.get("ssid"), connId, rootId);
				else { // debugging
					String logid = (String) header.get("logid");
					String uid = (String) header.get("uid");
					resp = respSessionInfoDebug(uid, logid, connId, rootId);
				}
				writer.write(resp);
			}
			else if ("login".equals(a)) {
				SUser login = parseLogin(jlogin, t, connId);
				if (login == null) {
					// no such user
					String logid = (String) jlogin.get("logid");
					SemanticObject resp = JHelper.err(ERR_UID, logid); 
					writer.write(resp);
				}
				else {
					if (login.login(jlogin, request)) {
						//SemanticObject resp = login.response(jlogin, request);
						
						lock.lock();
						users.put(login.sessionId(), login);
						lock.unlock();
						writer.write(JHelper.OK(login.sessionId(), login, new String[] {"url", login.homepage()}));
					}
					else writer.write(JHelper.err(ERR_PSWD, "passwords not matching - pswd = encrypt(uid, pswd, iv)"));
				}
			}
			else if ("logout".equals(a)) {
				check(header);
				// {uid: “user-id”,  ssid: “session-id-plain/cipher”, vi: "vi-b64"<, sys: “module-id”>}
				String ssid = (String) header.get("ssid");

				lock.lock();
				SUser usr = users.remove(ssid);
				lock.unlock();

				if (usr != null) {
					SemanticObject resp = usr.logout(header);
					writer.write(resp);
				}
				else writer.write(JHelper.OK("But no such session been found."));
			}
			else if ("touch".equals(t)) {
				check(header);
				String logid = (String) header.get("logid");
				writer.write(JHelper.OK(logid, null));
			}
			else
				throw new SQLException (String.format(
					"Session Request not supported: a=%s", a));
			//writer.close();
			response.flushBuffer();
		} catch (SsException e) {
			writer.write(JHelper.err(ERR_CHK, e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			if (writer != null)
				writer.write(JHelper.err(e.getMessage()));
		} finally {
			if (writer != null)
				writer.close();
		}
	}

	private SUser parseLogin(SemanticObject jlogin, String target, String connId) throws Exception {
		String logid = (String) jlogin.get("logid");
	
		SUser iruser =createUser(logid);
		return iruser;
	}
	
	/**Get function tree
	 * FIXME not correct in the point framework view.
	 * @param ssid
	 * @param conn
	 * @param rootId
	 * @return
	 * @throws SQLException
	 * @throws TransException 
	 */
	private SemanticObject respSessionInfo(String ssid, String conn, String rootId)
			throws SQLException, TransException {
		SUser u = users.get(ssid);
		if (u != null) {
//			SResultset funcs = sctx.select(conn,
//				new String[][] {new String[] {"main-table", functionStr[0]},
//								new String[] {"j", functionStr[1], null, functionStr[2]+"=funcId"}},
//				new String[][] {new String[] {"eq", "roleId", u.getRoleId(), functionStr[1]},
//								rootId == null ? null : new String[] {"eq", functionStr[3], rootId},
//								new String[] {"in", "isUsed", "Y,X"}},
//				null, 
//				new String[][] {new String[] {functionStr[0], "fullpath", "true"}},
//				null);
			SResultset funcs = (SResultset) sctx.select("").rs();

			// Note: add hard coded uppercase maping as the first parameter
//			SemanticObject f = JsonHelper.convert(null, Connects.getMappings(conn, functionStr[0], functionStr[1]), funcs.getRowCount(), funcs);
			SemanticObject resp = JHelper.OK("", u);
			return resp;
		}
		else return JHelper.err(ERR_CHK, "Session tocken failed");
	}
	
	private SemanticObject respSessionInfoDebug(String uid, String logId, String conn, String rootId)
			throws Exception {
		if ("admin".equals(uid)) return respSessionAdmin(conn, rootId);
//		SResultset funcs = sctx.select(conn,
//				new String[][] {new String[] {"main-table", functionStr[0]},
//								new String[] {"j", functionStr[1], null, functionStr[2]+"=funcId"}},
//				new String[][] {new String[] {"eq", "roleId", uid, functionStr[1]},
//								rootId == null ? null : new String[] {"eq", functionStr[3], rootId},
//								new String[] {"in", "isUsed", "Y,X"}},
//				null, 
//				new String[][] {new String[] {functionStr[0], "fullpath", "true"}} ,
//				null);
		SResultset funcs = (SResultset) sctx.select("").rs();

		// Note: add hard coded uppercase maping as the first parameter
//		SemanticObject f = JsonHelper.convert(null, Connects.getMappings(conn, functionStr[0], functionStr[1]), funcs.getRowCount(), funcs);
		
//		Class cls = Class.forName(Configs.getCfg("SUser"));
//		Constructor constructor = cls.getConstructor(String.class,String.class,String.class,String.class,String.class); 
//		SUser iruser =  (SUser) constructor.newInstance(uid, logId, "", "", "debug");
		
		SUser iruser = createUser("SUser",uid, logId, "", "", "debug");
		
		SemanticObject resp = JHelper.OK("", iruser);
		return resp;
	}

	/**
	 * 通过反射机制新建用户
	 * @param clsNamekey 类名
	 * @param uid 用户id
	 * @param logId 登录名
	 * @param pswd 登陆密码
	 * @param iv 加密字段
	 * @param userName 用户名
	 * @return
	 * @throws Exception
	 */
	public static SUser createUser(String clsNamekey, String uid, String logId, String pswd, String iv, String userName) throws Exception {
		@SuppressWarnings("unchecked")
		Class<SUser> cls = (Class<SUser>) Class.forName(Configs.getCfg(clsNamekey));
		Constructor<SUser> constructor = cls.getConstructor(String.class,String.class,String.class,String.class,String.class);
		return  (SUser) constructor.newInstance(uid,logId,pswd,iv,userName);
	}
	
	//通过反射机制新建用户(读数据库)
	public static SUser createUser(String logid) throws Exception {
		String sql = String.format(Configs.getCfg("irSession_bas_person"), logid);
		SResultset rs0 = Connects.select(sql);
		if (rs0.getRowCount() <= 0)
			return null;
		rs0.beforeFirst().next();
		
		String userStr= Configs.getCfg("SUsertable");
		String[] userStrArray = userStr.split(",");
		String userId = rs0.getString(userStrArray[0]);    //用户ID
		String logId = rs0.getString(userStrArray[1]);     //登陆名，用于登陆
		String dbPswd = rs0.getString(userStrArray[2]);    //密码
		String iv = rs0.getString(userStrArray[3]);        //加密辅助字段
		String userName = rs0.getString(userStrArray[4]);  //姓名
		
		
//		new irUser = Configs.getCfg("SUser");

		Class<?> cls = Class.forName(Configs.getCfg("SUser"));
		Constructor<?> constructor = cls.getConstructor(String.class,String.class,String.class,String.class,String.class); 
		return (SUser) constructor.newInstance(userId, logId, dbPswd, iv, userName);
	}
	
	private SemanticObject respSessionAdmin(String conn, String rootId) throws Exception {
//		SResultset funcs = sctx.select(conn,
//				new String[][] {new String[] {"main-table", functionStr[0]}},
//				new String[][] {rootId == null ? null : new String[] {"eq", functionStr[3], rootId},
//								new String[] {"in", "isUsed", "Y,X"}},
//				null,
//				new String[][] {new String[] {functionStr[0], "fullPath", "true"}} ,
//				null);
		SResultset funcs = (SResultset) sctx.select("").rs();

		// Note: add hard coded uppercase maping as the first parameter
//		SemanticObject f = JsonHelper.convert(null, Connects.getMappings(conn, functionStr[0]), funcs.getRowCount(), funcs);
		

//		Class cls = Class.forName(Configs.getCfg("SUser"));
//		Constructor constructor = cls.getConstructor(String.class,String.class,String.class,String.class,String.class); 
//		SUser iruser1 =  (SUser) constructor.newInstance("admin", "admin", "", "", "debug");
		
		SUser iruser = createUser("SUser","admin", "admin", "", "", "debug");
		SemanticObject resp = JHelper.OK("", iruser);
		return resp;
	}

	/**Get a 24 chars random Id.
	 * @return
	 */
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
}
