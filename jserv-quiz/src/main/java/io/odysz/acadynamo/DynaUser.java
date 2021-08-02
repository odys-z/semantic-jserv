package io.odysz.acadynamo;

import io.odysz.common.LangExt;
import io.odysz.semantic.jsession.JUser;
import io.odysz.semantics.meta.TableMeta;
import io.odysz.semantics.x.SemanticException;

public class DynaUser extends JUser {
	/**Hard coded field string of user table's information.
	 * With this class, the project's user table can be different from the default table,
	 * providing the same semantics presented.
	 * @author odys-z@github.com
	 */
	public static class DbUserMeta extends JUserMeta {
		public DbUserMeta(String tbl, String... conn) {
			super(tbl, conn);

			this.tbl = LangExt.isEmpty(tbl) ? "a_users" : tbl;
			pk = "userId";
			uname = "userName";
			pswd = "pswd";
			iv = "iv";
		}
	}

	public DynaUser(String uid, String pswd, String usrName) throws SemanticException {
		super(uid, pswd, usrName);
	}

	public TableMeta meta() {
		return new DbUserMeta("");
	}

}
