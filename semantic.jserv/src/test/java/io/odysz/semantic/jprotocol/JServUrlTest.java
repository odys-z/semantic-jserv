package io.odysz.semantic.jprotocol;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.odysz.semantic.jprotocol.AnsonMsg.Port;

class JServUrlTest {

	@Test
	void testValid() {
		JProtocol.setup("root-path", Port.echo);
		assertFalse(JServUrl.valid("http://127.0.0.1"));
		assertFalse(JServUrl.valid("http//127.0.0.1"));
		assertFalse(JServUrl.valid("https://127.0.0.1/tank"));
		assertFalse(JServUrl.valid("https://127.0.0.1:8964/tank"));
		assertFalse(JServUrl.valid("https://127.0.0.1/root-path"));
		assertTrue(JServUrl.valid("https://127.0.0.1:8964/root-path"));
		assertTrue(JServUrl.valid("https://127.0.0.1:1984/root-path"));
		assertTrue(JServUrl.valid("http://127.0.0.1:1984/root-path"));
		assertTrue(JServUrl.valid("http://127.0.0.1:1984/root-path/link.serv"));
		assertTrue(JServUrl.valid("http://127.0.0.1:1984/root-path/echo.less"));

		assertFalse(JServUrl.valid("http://?:?/root-path"));
		assertFalse(JServUrl.valid("http://--:8964/root-path"));
		assertFalse(JServUrl.valid("http://[]:[]/root-path"));
		assertTrue(JServUrl.valid("http://[::3]:8964/root-path"));
		assertTrue(JServUrl.valid("http://[::3]:8964/root-path/echo.less"));
	}

}
