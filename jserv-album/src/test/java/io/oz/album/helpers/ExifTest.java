package io.oz.album.helpers;

import static org.junit.jupiter.api.Assertions.*;
import static io.odysz.common.CheapMath.*;
import static io.odysz.common.LangExt.isblank;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Map;

import org.apache.commons.io_odysz.FilenameUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import io.odysz.common.Utils;
import io.odysz.semantics.x.SemanticException;
import io.oz.album.tier.PhotoRec;

class ExifTest {
	@Test
	void testEscape() {
		Exif.verbose = false;
		String v = Exif.escape("1 enUS(sRGB\0\0\0");
		assertEquals("1 enUS(sRGB", v);
	}

	@Test
	void testParseWidthHeight() throws IOException, SemanticException {
		int[] wh = Exif.parseWidthHeight("test/res/300x150.jpg");
		
		assertEquals(300, wh[0]);
		assertEquals(150, wh[1]);
		
		wh = reduceFract(wh[0], wh[1]);
		
		assertEquals(2, wh[0]);
		assertEquals(1, wh[1]);

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
		
		assertEquals(71, wh[0]);
		assertEquals(40, wh[1]);
	
		wh = Exif.parseWidthHeight("test/res/182x121.png");
		
		assertEquals(182, wh[0]);
		assertEquals(121, wh[1]);
		
		wh = reduceFract(wh[0], wh[1]);
		
		assertEquals(182, wh[0]);
		assertEquals(121, wh[1]);

		wh = Exif.parseWidthHeight("test/res/no-exif.jpg");
		
		assertEquals(1200, wh[0]);
		assertEquals(675, wh[1]);
		
		wh = reduceFract(wh[0], wh[1]);
		
		assertEquals(16, wh[0]);
		assertEquals(9, wh[1]);
		
		/** more to be tested
		 * see test-case-raw-exif.txt
		 */
	}
	
	@Test
	void testTika() throws IOException, TikaException, SAXException {
		Exif.verbose = false;
		
        Utils.logi(Paths.get(".").toAbsolutePath().toString());

		Utils.logi(FilenameUtils.concat(Paths.get(".").toAbsolutePath().toString(), "src/main/webapp/WEB-INF"));

		Exif.init(FilenameUtils.concat(Paths.get(".").toAbsolutePath().toString(), "src/main/webapp/WEB-INF"));
		
		{
			AutoDetectParser parser = new AutoDetectParser(Exif.config);
			Map<MediaType, Parser> ps = parser.getParsers();
			for (MediaType t : ps.keySet())
				Utils.logi("[testTika] %s, %s", t.getType(), ps.get(t).getClass().getName());
		}
		
		PhotoRec p = new PhotoRec();
		Exif.parseExif(p, "test/res/C0000006 IMG_20230816_111535.jpg");
		
		assertEquals(3, p.wh[0]);
		assertEquals(4, p.wh[1]);
		assertEquals(4896, p.widthHeight[0]);
		assertEquals(6528, p.widthHeight[1]);
		assertTrue(isblank(p.rotation));
		
		/** This mp4 is too large and the test result won't guarantee the same results in runtime
		 * (different runs with diffrenct results)
		p = new PhotoRec();
		Exif.parseExif(p, "test/res/C0000002 VID_20230816_135143.mp4");
		
		assertEquals(9, p.wh[0]);
		assertEquals(16, p.wh[1]);
		assertEquals(1920, p.widthHeight[0]);
		assertEquals(1080, p.widthHeight[1]);
		assertEquals("90", p.rotation);
		 */
		
		Exif.verbose = true;
		p = new PhotoRec(); 
		Exif.parseExif(p, "test/res/C000000D VID_20230831_200144.mp4");

		assertEquals(9, p.wh[0]);
		assertEquals(20, p.wh[1]);
		assertEquals(2400, p.widthHeight[0]);
		assertEquals(1080, p.widthHeight[1]);
		assertEquals("90", p.rotation);
		
	}

}
