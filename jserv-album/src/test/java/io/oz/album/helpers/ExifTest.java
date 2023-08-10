package io.oz.album.helpers;

import static org.junit.jupiter.api.Assertions.*;
import static io.odysz.common.CheapMath.*;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import io.odysz.semantics.x.SemanticException;

class ExifTest {
	@Test
	void testEscape() {
		String v = Exif.escape("1 enUS(sRGB\0\0\0");
		assertEquals("1 enUS(sRGB", v);
	}

	@Test
	void testParseWidthHeight() throws IOException, SemanticException {
		int[] wh = Exif.parseWidthHeight("test/res/300x150.jpg");
		
		assertEquals(300, wh[0]);
		assertEquals(150, wh[1]);
		
		wh = reduceFract(wh[0], wh[1]);
		
		assertEquals(1, wh[0]);
		assertEquals(2, wh[1]);

		wh = Exif.parseWidthHeight("test/res/ca-us.png");
		
		assertEquals(256, wh[0]);
		assertEquals(256, wh[1]);
		
		wh = reduceFract(wh[0], wh[1]);
		
		assertEquals(1, wh[0]);
		assertEquals(1, wh[1]);
	
		wh = Exif.parseWidthHeight("test/res/142x80.gif");
		
		assertEquals(142, wh[0]);
		assertEquals(80, wh[1]);
		
		wh = reduceFract(wh[0], wh[1]);
		
		assertEquals(40, wh[0]);
		assertEquals(71, wh[1]);
	
		wh = Exif.parseWidthHeight("test/res/182x121.png");
		
		assertEquals(182, wh[0]);
		assertEquals(121, wh[1]);
		
		wh = reduceFract(wh[0], wh[1]);
		
		assertEquals(121, wh[0]);
		assertEquals(182, wh[1]);

		wh = Exif.parseWidthHeight("test/res/no-exif.jpg");
		
		assertEquals(1200, wh[0]);
		assertEquals(675, wh[1]);
		
		wh = reduceFract(wh[0], wh[1]);
		
		assertEquals(9, wh[0]);
		assertEquals(16, wh[1]);
	}

}
