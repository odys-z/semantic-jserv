package io.odysz.jsample;

import io.odysz.semantic.jsession.JProfile;
import io.odysz.semantic.jsession.JUser;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;

public class SampleUser extends JUser {
	/**
	 * Hard coded field string of user table information.
	 * With this class, sample project's user table can be different from the default table,
	 * providing the same semantics presented.
	 * @author odys-z@github.com
	 */
	public static class SampleUserMeta extends JUserMeta {
		public SampleUserMeta(String... conn) {
			super(conn);

			this.tbl = "a_users";
			pk = "userId";
//			uname = "userName";
//			pswd = "pswd";
//			iv = "iv";
		}
	}

	public SampleUser(String uid, String pswd, String usrName) throws SemanticException {
		super(uid, pswd, usrName);
	}

	@Override
	public TableMeta meta(String... conn) {
		return new SampleUserMeta();
	}

	@Override
	public boolean login(Object reqObj) throws TransException {
		return super.login(reqObj);
	}

	/**
	 * Replied on login, AnSessionResp.profile, evaluated by AnSession on each login
	 * on the user freshly loaded from DB (a_users.orgId).
	 * 
	 * @since 1.5.19
	 */
	@Override
	public JProfile profile() {
		return new JProfile(null, orgId());
	}
}
