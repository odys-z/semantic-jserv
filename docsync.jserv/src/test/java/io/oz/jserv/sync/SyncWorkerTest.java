package io.oz.jserv.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.io_odysz.FilenameUtils;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.AESHelper;
import io.odysz.common.DateFormat;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.SessionInf;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.SyncRec;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.x.TransException;
import io.oz.album.client.AlbumClientier;
import io.oz.album.tier.AlbumResp;
import io.oz.jserv.docsync.SyncDoc;
import io.oz.jserv.sync.SyncRobot;
import io.oz.jserv.sync.SyncWorker;

class SyncWorkerTest {

	static ErrorCtx errLog;

	static {
		Path currentRelativePath = Paths.get("");
		String p = currentRelativePath.toAbsolutePath().toString();
		System.setProperty("VOLUME_HOME", p + "/src/test/res/volume");
		Connects.init("src/test/res/WEB-INF");
		Clients.init("http://localhost:8081/jserv-album", true);
		
		errLog = new ErrorCtx() {
			@Override
			public void onError(MsgCode code, String msg) {
				Utils.warn(msg);
			}
		};
	}
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
	void testPrivPush() throws
		AnsonException, SQLException, IOException, TransException,
		SsException, GeneralSecurityException, SAXException {
		
		String conn = "main-sqlite";
		SyncDoc photo = new SyncDoc();

		String clientpath = "src/test/res/182x121.png";
		File png = new File(clientpath);
		FileInputStream ifs = new FileInputStream(png);

		String b64 = AESHelper.encode64(ifs, 216); // 12 | 216, length = 219
		photo.uri = b64;
		while (b64 != null) {
			b64 = AESHelper.encode64(ifs, 216); // FIXME this will padding useless bytes, what is happening when the file is saved at server side?
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
		SyncWorker worker = new SyncWorker(0, conn, "kyiv.jnode", "h_photos")
				.login("odys-z.github.io", "слава україні") // jserv node
				.push();
	
		DocsResp resp = worker.queryTasks();

		worker.syncDocs(resp);
	}

	@Test
	void testPrivPull() throws
		AnsonException, SQLException, IOException, TransException,
		SsException, GeneralSecurityException, SAXException {
	}
	
	@Test
	void videoUp() throws SemanticException, SsException, IOException, GeneralSecurityException, AnsonException {
		String localFolder = "src/test/res/anclient.java";
		int bsize = 72 * 1024;
		String filename = "Amelia Anisovych.mp4";

		SessionClient ssclient = Clients.login("ody", "123456", "device-test");  // client device
		AlbumClientier tier = new AlbumClientier("test/album", ssclient, errLog)
								.blockSize(bsize);

		List<SyncRec> videos = new ArrayList<SyncRec>();
		videos.add((SyncRec) new SyncRec()
					.fullpath(FilenameUtils.concat(localFolder, filename)));

		SessionInf photoUser = ssclient.ssInfo();
		photoUser.device = "device-test";

		tier.syncVideos( videos, photoUser,
			(c, v, resp) -> {
				fail("duplicate checking not working");
			},
			new ErrorCtx() {
				@Override
				public void onError(MsgCode c, String msg) {
					if (!MsgCode.exGeneral.equals(c))
						fail("Not expected code");

					tier.del("device-test", videos.get(0).fullpath());
					List<DocsResp> resps = tier.syncVideos(videos, photoUser, null);
					assertNotNull(resps);
					assertEquals(1, resps.size());

					for (DocsResp d : resps) {
						String docId = d.recId();
						assertEquals(8, docId.length());

						AlbumResp rp = tier.selectPhotoRec(docId);
						assertNotNull(rp.photo().pname);
						assertEquals(rp.photo().pname, filename);
					}
				}
			});

	}
}
