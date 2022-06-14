package io.odysz.jsample;

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
		public SampleUserMeta(String tbl, String... conn) {
			super(tbl, conn);

			this.tbl = "a_users";
			pk = "userId";
			uname = "userName";
			pswd = "pswd";
			iv = "iv";
		}
	}

	public SampleUser(String uid, String pswd, String usrName) throws SemanticException {
		super(uid, pswd, usrName);
	}

	public TableMeta meta() {
		return new SampleUserMeta("");
	}

	@Override
	public boolean login(Object reqObj) throws TransException {
		return super.login(reqObj);
	}
}
