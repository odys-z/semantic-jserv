package io.odysz.jsample.semantier;

import java.util.ArrayList;
import java.util.HashMap;

import io.odysz.semantic.jserv.user.UserReq;
import io.odysz.semantic.tier.Relations;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.Statement;
import io.odysz.transact.x.TransException;

public class UserstReq extends UserReq {

	static class A {
		/** Ask for loading records */
		public static final String records = "records";
		/** Ask for loading a rec */
		public static final String rec = "rec";

		public static final String update = "a-u";

		public static final String insert = "a-c";
	}

	/// use case: load record - refactor this into CRUD?
	String userId;
	String userName;
	String orgId;
	
	/// use case: insert/update record
	HashMap<String, Object> record;
	ArrayList<Relations> relations;

	public UserstReq() {
		super(null, null);
	}

	/**Setup nv for {@link Insert}
	 * @param st
	 * @return
	 * @throws TransException 
	 */
	public Statement<?> nvs(Statement<?> st) throws TransException {
		for (String f : record.keySet())
			st.nv(f, (String)record.get(f));
		return st;
	}


}
