package io.oz.jserv.docs.syn;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Set;

import io.odysz.semantic.jsession.AnSessionReq;
import io.odysz.semantic.jsession.JUser.JUserMeta;
import io.odysz.semantic.syn.SyncUser;
import io.odysz.semantics.IUser;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

/**
 * Doc User, can only be created at server side.
 * 
 * @author odys-z@github.com
 */
public class DocUser extends SyncUser implements IUser {
	@Override
	public boolean login(Object reqObj) throws TransException {

		if (super.login(reqObj)) {
			AnSessionReq req = (AnSessionReq)reqObj;
			deviceId = req.deviceId();
			return true;
		}
		return false;
	}

	public static JUserMeta userMeta;
	
	static {
		userMeta = new JUserMeta("a_users");
	}

	static final String WebRoot = "web-root";

	long touched;

	String roleId;
	String roleName;
	String orgName;

	protected String deviceId;
	public String deviceId() { return deviceId; }
	public DocUser deviceId(String id) {
		deviceId = id;
		return this;
	}

	public DocUser(String userid, String passwd, String userName) throws SemanticException {
		super(userid, passwd, userName);
	}

	public DocUser(String userid, String passwd) throws SemanticException {
		super(userid, passwd, userid);
	}

	public DocUser(String userid) throws SemanticException {
		super(userid, null, userid);
	}

	@Override public ArrayList<String> dbLog(ArrayList<String> sqls) { return null; }

	protected Set<String> tempDirs;
	
	@Override
	public TableMeta meta(String ... connId) throws SQLException, TransException {
		return new JUserMeta("a_users");
				// .clone(Connects.getMeta(isNull(connId) ? null : connId[0], "a_users"));
	}

	@Override
	public IUser sessionKey(String k) {
		knowledge = k;
		return this;
	}

	/** Session Token Knowledge */
	String knowledge;
	@Override
	public String sessionKey() { return knowledge; }

	/**
	 * <p>Get a temp dir, and have it deleted when logout.</p>
	 * Since jserv 1.4.3 and album 0.5.2, deleting temp dirs are handled by session users.
	 * @param conn
	 * @return the dir
	 * @throws DocsException 
	 * @throws SemanticException
	public String touchTempDir(String conn, String doctbl) throws DocsException {
		if (!DATranscxt.hasSemantics(conn, doctbl, smtype.extFilev2))
			throw new DocsException(DocsException.SemanticsError,
					"No smtype.extFilev handler is configured for conn %s, table %s.",
					conn, doctbl);

		String extroot = ((ShExtFilev2) DATranscxt
						.getHandler(conn, doctbl, smtype.extFilev2))
						.getFileRoot();

		String tempDir = IUser.tempDir(extroot, uid(), "uploading-temp", ssid);
		if (tempDirs == null)
			tempDirs= new HashSet<String>(1);
		tempDirs.add(tempDir);
		return tempDir;
	}
	 */
	
//	SynDomanager domanager;

//	@Override
//	public SemanticObject logout() {
//		// if (domanager != null)
//		domanager.unlockx(this);
//		return super.logout();
//	}

}
