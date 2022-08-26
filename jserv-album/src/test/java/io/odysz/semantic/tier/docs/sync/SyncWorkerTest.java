package io.odysz.semantic.tier.docs.sync;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.AESHelper;
import io.odysz.common.DateFormat;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.transact.x.TransException;
import io.oz.album.PhotoRobot;
import io.oz.album.tier.Albums;
import io.oz.album.tier.Photo;

class SyncWorkerTest {

	/**
	 * Test {@link SyncWorker} with album-jserv as sync hub (8081).
	 * @throws TransException 
	 * @throws IOException 
	 * @throws SQLException 
	 * @throws AnsonException 
	 * @throws GeneralSecurityException 
	 * @throws SsException 
	 * @throws SAXException 
	 * 
	 */
	@SuppressWarnings("serial")
	@Test
	void testPrivMain() throws AnsonException, SQLException, IOException, TransException, SsException, GeneralSecurityException, SAXException {
		
		String conn = "sys-sqlite";
		Photo photo = new Photo();

		String clientpath = "test/res/182x121.png";
		File png = new File(clientpath);
		FileInputStream ifs = new FileInputStream(png);
		photo.uri = AESHelper.encode64(ifs, (int)png.length()); // 3 | 219

		photo.albumId = "t0";
		photo.clientpath = clientpath;
		photo.device = "jserv.main";
		photo.sharer = "ody";
		photo.geox = "50.426516"; // longitude
		photo.geoy = "30.563037"; // latitude
		photo.exif = new ArrayList<String>() {
			{add("location:вулиця Лаврська' 27' Київ");};
			{add("camera:Bayraktar TB2");}};
		photo.sharedate = DateFormat.format(new Date());

		PhotoRobot usr = new PhotoRobot("odys-z.github.io");

		String pid = Albums.createFile(conn, photo, usr);

		SyncWorker.blocksize = 32 * 3;
		SyncWorker worker = new SyncWorker(0, conn, "h_photos")
				.login("odys-z.github.io", "слава україні")
				.push();
	
		DocsResp  resp = worker.queryTasks();

		worker.syncDocs(resp);
	}

}
