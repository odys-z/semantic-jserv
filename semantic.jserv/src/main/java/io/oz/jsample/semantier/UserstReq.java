package io.oz.jsample.semantier;

import java.util.ArrayList;
import java.util.HashMap;

import io.odysz.semantic.jprotocol.UserReq;
import io.odysz.semantic.tier.Relations;
import io.odysz.transact.sql.Insert;
import io.odysz.transact.sql.PageInf;
import io.odysz.transact.sql.Statement;
import io.odysz.transact.x.TransException;

public class UserstReq extends UserReq {

	public static class A {
		/** Ask for loading records */
		public static final String records = "records";
		/** Ask for loading a rec */
		public static final String rec = "rec";

		public static final String update = "u";
		public static final String avatar = "u/avatar";
		public static final String insert = "c";
		public static final String del = "d";
	}

	/// use case: load record - refactor this into CRUD?
	PageInf page;
	/// record field?
	String userId;
	public String userId() { return userId; }
	String userName;
	public String userName() { return userName; };
	String orgId;
	public String orgId() { return orgId; };
	String roleId;
	public String roleId() { return roleId; };
	boolean hasTodos;
	public boolean hasTodos() { return hasTodos; }
	
	/// use case: insert/update record
	public HashMap<String, Object> record;
	public ArrayList<Relations> relations;
	String pk;
	public String pk() { return pk; }

	/// use case: d
	/**
	 * Deleting Ids, e.g. AnQueryForm.state.selected.Ids
	 */
	String[] deletings;

	public UserstReq() {
		super(null, null);
	}

	/**Setup nv for {@link Insert}
	 * @param st
	 * @return st
	 * @throws TransException 
	 */
	public Statement<?> nvs(Statement<?> st) throws TransException {
		for (String f : record.keySet())
			st.nv(f, (String)record.get(f));
		return st;
	}
}
