package io.odysz.semantic.jserv.file;

import static org.junit.Assert.*;

import org.junit.Test;

public class JFileServTest {

	@Test
	public void test() {
		String p = JFileServ.parsefileId("ip", "form-data; name=\"file\"; filename=\"t1.png\"");
		assertEquals(".png", p.substring(p.length() - 4, p.length()));

		p = JFileServ.parsefileId("ip", "form-data; name=\"file\"; filename=\"t1-png\"");
		assertEquals(".upload", p.substring(p.length() - 7, p.length()));

		p = JFileServ.parsefileId("ip", "form-data; name=\"file\"; filename=\"morning-2.jpg\"");
		assertEquals(".jpg", p.substring(p.length() - 4, p.length()));
	}

}
