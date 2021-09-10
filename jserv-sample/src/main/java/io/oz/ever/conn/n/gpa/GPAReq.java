package io.oz.ever.conn.n.gpa;

import java.util.List;

import io.odysz.semantic.jserv.user.UserReq;
import io.odysz.transact.sql.Insert;

public class GPAReq extends UserReq {
	/** case insert  (with gday)
	 * [ [alice, 0], ... ]
	 */
	List<Object[]> kids;
	
	/// case update
	String kid;
	String gday;
	String gpa;

	static class A {
		public static final String gpas = "r/gpas";
		/** insert gpa row for all kids */
		public static final String insert = "c";
		public static final String update = "u";
	}

	public GPAReq insCols(Insert ins) {
		return this;
	}

	public Insert insVals(Insert ins) {
		return null;
	}
}
