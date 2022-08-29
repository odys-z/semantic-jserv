package io.oz.jserv.sync;

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
import io.oz.jserv.docsync.SyncDoc;
import io.oz.jserv.sync.SyncRobot;
import io.oz.jserv.sync.SyncWorker;

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
	void testPrivMain() throws
		AnsonException, SQLException, IOException, TransException,
		SsException, GeneralSecurityException, SAXException {
		
		String conn = "sys-sqlite";
		SyncDoc photo = new SyncDoc();

		String clientpath = "src/test/res/182x121.png";
		File png = new File(clientpath);
		FileInputStream ifs = new FileInputStream(png);

		String b64 = AESHelper.encode64(ifs, 216); // 12 | 216, length = 219
		photo.uri = b64;
		while (b64 != null) {
			b64 = AESHelper.encode64(ifs, 216);
			if (b64 != null)
				photo.uri += b64;
		}
		ifs.close();

		photo.clientpath = clientpath;
		photo.device = "jserv.main";
		photo.shareby = "ody";
		photo.exif = new ArrayList<String>() {
			{add("location:вулиця Лаврська' 27' Київ");};
			{add("camera:Bayraktar TB2");}};
		photo.sharedate = DateFormat.format(new Date());

		SyncRobot usr = new SyncRobot("odys-z.github.io");

		String pid = SyncWorker.createFile(conn, photo, usr);

		SyncWorker.blocksize = 32 * 3;
		SyncWorker worker = new SyncWorker(0, conn, "h_photos")
				.login("odys-z.github.io", "слава україні")
				.push();
	
		DocsResp  resp = worker.queryTasks();

		worker.syncDocs(resp);
	}

}
