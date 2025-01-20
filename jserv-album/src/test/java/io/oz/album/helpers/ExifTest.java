package io.oz.album.helpers;

import static io.odysz.common.CheapMath.reduceFract;
import static io.odysz.common.LangExt.isblank;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io_odysz.FilenameUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import io.odysz.common.Configs;
import io.odysz.common.Utils;
import io.odysz.semantics.x.SemanticException;
import io.oz.album.peer.PhotoRec;

class ExifTest {
//	static {
//		// setEnv("org.apache.tika.service.error.warn", "true");
//		// assertEquals("true", System.getenv("org.apache.tika.service.error.warn"));
//	}
		
	/**
	 * For <a href='https://cwiki.apache.org/confluence/display/TIKA/Troubleshooting+Tika#TroubleshootingTika-IdentifyingifanyParsersfailedtobeloaded'>
	 * dentifying if any Parsers failed to be loaded</a>,
	 * 
	 * by
	 * https://stackoverflow.com/a/40682052,
	 * 
	 * Used as
	 * setEnv("org.apache.tika.service.error.warn", "true");
	 * 
	 * @param key
	 * @param value
	static void setEnv(String key, String value) {
	    try {
	        Map<String, String> env = System.getenv();
	        Class<?> cl = env.getClass();
	        Field field = cl.getDeclaredField("m");
	        field.setAccessible(true);
	        @SuppressWarnings("unchecked")
			Map<String, String> writableEnv = (Map<String, String>) field.get(env);
	        writableEnv.put(key, value);
	    } catch (Exception e) {
	        throw new IllegalStateException("Failed to set environment variable", e);
	    }
	}
	 * @throws TimeoutException 
	 * @throws IOException 
	 * @throws InterruptedException 
	 */

	@BeforeAll
	static void init () throws InterruptedException, IOException, TimeoutException {
		// Exiftool.init("./src/main/java/webapp/WEBINF");
		Configs.init("./src/main/webapp/WEB-INF");
		Exiftool.init();
	}

	@Test
	void testEscape() {
		Exif.verbose = false;
		String v = Exif.escape("1 enUS(sRGB\0\0\0");
		assertEquals("1 enUS(sRGB", v);
	}

	@SuppressWarnings("deprecation")
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
	void testTika() throws IOException, SAXException, SemanticException, ReflectiveOperationException {
		Exif.verbose = true;
		
        Utils.logi(Paths.get(".").toAbsolutePath().toString());

		Utils.logi(FilenameUtils.concat(Paths.get(".").toAbsolutePath().toString(), "src/main/webapp/WEB-INF"));

		Exif.init(FilenameUtils.concat(Paths.get(".").toAbsolutePath().toString(), "src/main/webapp/WEB-INF"));
		
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
		
		Exif.verbose = false;
		p = new PhotoRec(); 
		Exif.parseExif(p, "test/res/C000000D VID_20230831_200144.mp4");

		assertEquals(9, p.wh[0]);
		assertEquals(20, p.wh[1]);
		assertEquals(2400, p.widthHeight[0]);
		assertEquals(1080, p.widthHeight[1]);
		assertEquals("90", p.rotation);
		
	}

	@Test
	void testExiftool() throws Exception {
		// Exiftool.cmd = "exiftool";// System.getProperty("exiftool");
		
		PhotoRec p = new PhotoRec();
		Exiftool.parseExif(p, "test/res/C0000006 IMG_20230816_111535.jpg");
		
		assertEquals(3, p.wh[0]);
		assertEquals(4, p.wh[1]);
		assertEquals(4896, p.widthHeight[0]);
		assertEquals(6528, p.widthHeight[1]);
		assertEquals("image/jpeg", p.mime);
		assertTrue(isblank(p.rotation));
		
		p = new PhotoRec(); 
		Exiftool.parseExif(p,"test/res/C000000D VID_20230831_200144.mp4"); 

		assertEquals(9, p.wh[0]);
		assertEquals(20, p.wh[1]);
		assertEquals(2400, p.widthHeight[0]);
		assertEquals(1080, p.widthHeight[1]);
		assertEquals("video/mp4", p.mime);
		assertEquals("90", p.rotation);
		
	}

}
