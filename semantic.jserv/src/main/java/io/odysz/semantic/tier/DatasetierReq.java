package io.odysz.semantic.tier;

import io.odysz.semantic.jprotocol.AnsonResp;

public class DatasetierReq extends AnsonResp {

	public static class A {
		public static final String sks = "r/sks";

		/** Load stree, usually overiden with subclass's A. */
		public static final String stree = "r/stree";
	}
}
