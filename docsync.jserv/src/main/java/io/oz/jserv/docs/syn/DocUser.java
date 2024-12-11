package io.oz.jserv.docs.syn;

import static io.odysz.common.LangExt.isblank;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Set;

import io.odysz.common.AESHelper;
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

	public static JUserMeta userMeta;
	
	static {
		userMeta = new JUserMeta("a_users");
	}

	static final String WebRoot = "web-root";

	long touched;

	String roleName;
	String orgName;

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

	@Override
	public boolean login(Object reqObj) throws TransException {
		AnSessionReq req = (AnSessionReq)reqObj;
		// 1. encrypt db-uid with (db.pswd, j.iv) => pswd-cipher
		byte[] ssiv = AESHelper.decode64(req.iv());
		String c = null;
		try { c = AESHelper.encrypt(userId, pswd, ssiv); }
		catch (Exception e) { throw new TransException (e.getMessage()); }

		// 2. compare pswd-cipher with j.pswd
		if (c.equals(req.token())) {
			touch();
			deviceId = req.deviceId();
			return true;
		}

		return false;
	}

}
