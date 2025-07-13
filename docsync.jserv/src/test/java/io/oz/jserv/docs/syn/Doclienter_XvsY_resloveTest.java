package io.oz.jserv.docs.syn;

import org.junit.jupiter.api.Test;

import io.oz.jserv.docs.syn.singleton.ExpDoctierservTest;

class Doclienter_XvsY_resloveTest {

	@Test
	void testXvsY() throws Exception {
		DoclientierTest.init(ExpDoctierservTest.case_xy_resolve);
		DoclientierTest.synclientUp();
	}

}
