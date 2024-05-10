package io.oz.album.tier;

import static org.junit.jupiter.api.Assertions.*;

import static io.odysz.common.LangExt.isblank;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import io.odysz.semantic.DA.Connects;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.album.PhotoUser;

/**
 * Test file / uri data access functions.
 * 
 * <pre>INSERT INTO h_photos (pid,uri,pname,folder,pdate,cdate,tags,oper,opertime) VALUES
	 ('test-00000','omni/ody/2019_08/DSC_0005.JPG','DSC_0005.JPG','2019-08-24','2021-08-24','#Qing Hai Lake','ody','2022-01-13'),
	 ('test-00001','omni/ody/2019_08/DSC_0124.JPG','DSC_0124.JPG','2019-08-24','2021-08-24','#Qing Hai Lake','ody','2022-01-13'),
	 ('test-00002','omni/ody/2021_08/IMG_20210826.jgp','IMG_20210826.jgp','2019-08-24 15:44:30','2021-08-26','#Lotus Lake','ody','2022-01-13'),
	 ('test-00003','omni/ody/2021_10/IMG_20211005.jgp','IMG_20211005.jgp','2019-10-05 11:19:18','2021-10-05','#Song Gong Fort','ody','2022-01-13'),
	 ('test-00004','omni/ody/2021_12/DSG_0753.JPG','DSG_0753.JPG','2021-12-05','2021-12-05','#Garze','ody','2022-01-13'),
	 ('test-00005','omni/ody/2021_12/DSG_0827.JPG','DSG_0827.JPG','2021-12-05','2021-12-05','#Garze','ody','2022-01-13'),
	 ('test-00006','omni/ody/2021_12/DSG_0880.JPG','DSG_0880.JPG','2021-12-31','2021-12-31','#Toronto','ody','2022-01-13');
   </pre>
 * @author ody
 *
 */
class AlbumsServTierTest {
	static String jserv;

	static IUser robot;
	/** local working dir */
	static String local;

	static {
		try {
			jserv = "http://localhost:8080/jserv-album";
			System.setProperty("VOLUME_HOME", "../../../../volume");
			Connects.init("src/main/webapp/WEB-INF");

			local = new File("src/test/local").getAbsolutePath();
			robot = new PhotoUser("test album");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	void testRec() throws SemanticException, TransException, SQLException, IOException {
	
		AlbumReq req = new AlbumReq("/local/test").page(0, -1, "pid", "C000000A");
		req.docId = "test-00001";

		AlbumResp rep = Albums.doc(req, robot);
		
		assertEquals("C000000A", rep.photo.recId);
		assertEquals("ottawa-canada.jpg", rep.photo.pname);
		assertTrue(isblank(rep.photo.uri));
		assertEquals("2022_03", rep.photo.folder());
		
		/*
		req.collectId = "c-001";
		AlbumResp coll = Albums.collect(req, robot);
		assertEquals(1, coll.collectRecords.size());
		assertEquals("c-001", coll.collectRecords.get(0).cid);
		assertEquals("Liar & Fool", coll.collectRecords.get(0).cname);
		assertEquals("c-001", coll.photos.get(0)[0].collectId);
		assertEquals("DSC_0005.JPG", coll.photos.get(0)[0].pname);
		assertTrue(isblank(coll.photos.get(0)[0].uri));
		*/
	}

}
