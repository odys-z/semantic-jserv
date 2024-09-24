package io.oz.jserv.docs;

import static io.odysz.common.LangExt.isNull;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.odysz.common.IAssert;

public class AssertImpl implements IAssert {

	@Override
	public <T> void equals(T a, T b, String... msg) throws Error {
		if ((b == null || a == null) && a != b)
			fail(isNull(msg) ? "a != b" : msg[0]);
		else if (b instanceof String)
			assertTrue(((String)b).matches((String)a));
		else
			assertEquals(a, b, isNull(msg) ? null : msg[0]);
	}

	@Override
	public void equali(int a, int b, String... msg) throws Error {
		assertEquals(a, b, isNull(msg) ? null : msg[0]);
	}

	@Override
	public void fail(String e) throws Error {
		fail(e);
	}

	@Override
	public void equall(long a, long b, String... msg) {
		assertEquals(a, b, isNull(msg) ? null : msg[0]);
	}
}
