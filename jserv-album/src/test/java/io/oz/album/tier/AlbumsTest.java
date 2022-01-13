package io.oz.album.tier;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.album.PhotoRobot;

class AlbumsTest {

	@Test
	void testRec() throws SemanticException, TransException, SQLException {
		File f = new File("volume");
		String dir = f.getAbsolutePath();
		
		AlbumReq req = new AlbumReq();
		req.fileId = "test-00001";
		
		IUser robot = new PhotoRobot("test album");

		AlbumResp rep = Albums.rec(req, robot);
		
		assertEquals("test-00001", rep.id);
		assertEquals("test-00001.jpg", rep.fileName);
		
	}

}
