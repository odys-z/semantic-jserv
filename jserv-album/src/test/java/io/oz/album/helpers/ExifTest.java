package io.oz.album.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import io.odysz.common.Configs;
import io.odysz.semantics.x.SemanticException;
import io.oz.album.peer.PhotoRec;

class ExifTest {

	@BeforeAll
	static void init () throws InterruptedException, IOException, TimeoutException {
		Configs.init("./src/main/webapp/WEB-INF");
		Exiftool.init();
	}

	@Test
	void testEscape() {
		Exiftool.verbose = false;
		String v = Exiftool.escape("1 enUS(sRGB\0\0\0");
		assertEquals("1 enUS(sRGB", v);
	}

	@Test
	void testParseWidthHeight() throws IOException, SemanticException {

		PhotoRec p = new PhotoRec();
		Exiftool.parseExif(p, "test/res/300x150.jpg");
		
		assertEquals(300, p.widthHeight[0]);
		assertEquals(150, p.widthHeight[1]);
		
		assertEquals(2, p.wh[0]);
		assertEquals(1, p.wh[1]);

		p = new PhotoRec();
		Exiftool.parseExif(p, "test/res/ca-us.png");
		
		assertEquals(256, p.widthHeight[0]);
		assertEquals(256, p.widthHeight[1]);
		
		assertEquals(1, p.wh[0]);
		assertEquals(1, p.wh[1]);
	
		p = new PhotoRec();
		Exiftool.parseExif(p, "test/res/142x80.gif");
		
		assertEquals(142, p.widthHeight[0]);
		assertEquals(80, p.widthHeight[1]);
		
		assertEquals(71, p.wh[0]);
		assertEquals(40, p.wh[1]);
	
		p = new PhotoRec();
		Exiftool.parseExif(p, "test/res/182x121.png");
		
		assertEquals(182, p.widthHeight[0]);
		assertEquals(121, p.widthHeight[1]);
		
		assertEquals(182, p.wh[0]);
		assertEquals(121, p.wh[1]);

		p = new PhotoRec();
		Exiftool.parseExif(p, "test/res/no-exif.jpg");
		
		assertEquals(1200, p.widthHeight[0]);
		assertEquals(675, p.widthHeight[1]);
		
		assertEquals(16, p.wh[0]);
		assertEquals(9, p.wh[1]);
	}
	
//	@Test
//	@Disabled
//	void testTika() throws IOException, SAXException, SemanticException, ReflectiveOperationException {
//		Exif.verbose = true;
//		
//        Utils.logi(Paths.get(".").toAbsolutePath().toString());
//
//		Utils.logi(FilenameUtils.concat(Paths.get(".").toAbsolutePath().toString(), "src/main/webapp/WEB-INF"));
//
//		Exif.init(FilenameUtils.concat(Paths.get(".").toAbsolutePath().toString(), "src/main/webapp/WEB-INF"));
//		
//		PhotoRec p = new PhotoRec();
//		Exif.parseExif(p, "test/res/C0000006 IMG_20230816_111535.jpg");
//		
//		assertEquals(3, p.wh[0]);
//		assertEquals(4, p.wh[1]);
//		assertEquals(4896, p.widthHeight[0]);
//		assertEquals(6528, p.widthHeight[1]);
//		assertTrue(isblank(p.rotation));
//		
//		/** This mp4 is too large and the test result won't guarantee the same results in runtime
//		 * (different runs with diffrenct results)
//		p = new PhotoRec();
//		Exif.parseExif(p, "test/res/C0000002 VID_20230816_135143.mp4");
//		
//		assertEquals(9, p.wh[0]);
//		assertEquals(16, p.wh[1]);
//		assertEquals(1920, p.widthHeight[0]);
//		assertEquals(1080, p.widthHeight[1]);
//		assertEquals("90", p.rotation);
//		 */
//		
//		Exif.verbose = false;
//		p = new PhotoRec(); 
//		Exif.parseExif(p, "test/res/C000000D VID_20230831_200144.mp4");
//
//		assertEquals(9, p.wh[0]);
//		assertEquals(20, p.wh[1]);
//		assertEquals(2400, p.widthHeight[0]);
//		assertEquals(1080, p.widthHeight[1]);
//		assertEquals("90", p.rotation);
//		
//	}

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
		assertEquals(0, p.rotation);
		
		p = new PhotoRec(); 
		Exiftool.parseExif(p,"test/res/C000000D VID_20230831_200144.mp4"); 

		assertEquals(9, p.wh[0]);
		assertEquals(20, p.wh[1]);
		assertEquals(1080, p.widthHeight[0]);
		assertEquals(2400, p.widthHeight[1]);
		assertEquals("video/mp4", p.mime);
		assertEquals(90, p.rotation);
		
	}

}
