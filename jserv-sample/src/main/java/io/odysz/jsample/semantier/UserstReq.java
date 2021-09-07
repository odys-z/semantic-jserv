package io.odysz.jsample.semantier;

import io.odysz.semantic.jserv.user.UserReq;

public class UserstReq extends UserReq {

	static class A {
		/** Ask for loading records */
		public static final String records = "records";
		/** Ask for loading a rec */
		public static final String rec = "rec";

		public static final String update = "a-u";

		public static final String insert = "a-c";
	}

	String userId;
	String userName;
	String orgId;

	public UserstReq() {
		super(null, null);
	}


}
