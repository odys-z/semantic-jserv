package io.odysz.semantic.jserv.file;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class JFileServTest {

	/**Make sure {@link JFileServ} handle file path correctly */
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
