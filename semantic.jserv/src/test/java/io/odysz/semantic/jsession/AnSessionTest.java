package io.odysz.semantic.jsession;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AnSessionTest {

	@Test
	void testAllocateSsid() {
		assertFalse(AnSession.allocateSsid().startsWith("00"));
		assertEquals(8, AnSession.allocateSsid().length());
	}

}
