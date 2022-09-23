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
import io.odysz.common.Configs;
import io.odysz.common.DateFormat;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.SessionInf;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.SyncRec;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;
import io.oz.album.client.AlbumClientier;
import io.oz.album.tier.AlbumResp;
import io.oz.album.tier.DocUtils;
import io.oz.album.tier.Photo;
import io.oz.album.tier.PhotoMeta;

class SyncWorkerTest {

	static ErrorCtx errLog;
	static DATranscxt defltSt;

	static {
		try {
			Path currentRelativePath = Paths.get("");
			String p = currentRelativePath.toAbsolutePath().toString();
			System.setProperty("VOLUME_HOME", p + "/src/test/res/volume");

			Configs.init(p + "/src/test/res/WEB-INF");
			Connects.init(p + "/src/test/res/WEB-INF");
			defltSt = new DATranscxt(Connects.defltConn());
			AnsonMsg.understandPorts(Port.docsync);
			AnSession.init(defltSt);

			Connects.init("src/test/res/WEB-INF");
			Clients.init("http://localhost:8081/jserv-album", true);
			
			errLog = new ErrorCtx() {
				@Override
				public void onError(MsgCode code, String msg) {
					Utils.warn(msg);
				}
			};
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
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
		
		// create a public file at this private node
		String conn = "main-sqlite";
		Photo photo = new Photo();

		String clientpath = "src/test/res/182x121.png";
		File png = new File(clientpath);
		FileInputStream ifs = new FileInputStream(png);
		photo.pname = png.getName();

		String b64 = AESHelper.encode64(ifs, 216); // 12 | 216, length = 219
		photo.uri = b64;
		while (b64 != null) {
			b64 = AESHelper.encode64(ifs, 216); // FIXME this will padding useless bytes, what is happening when the file is saved at server side?
			if (b64 != null)
				photo.uri += b64;
		}
		ifs.close();

		photo.clientpath = clientpath;
		photo.device = "test device";
		photo.shareby = "ody";
		photo.exif = new ArrayList<String>() {
			{add("location:вулиця Лаврська' 27' Київ");};
			{add("camera:Bayraktar TB2");}};
		photo.sharedate = DateFormat.format(new Date());

		SyncRobot usr = new SyncRobot("odys-z.github.io", "f/zsu");

		String pth = createPhoto(conn, photo, usr, new PhotoMeta(defltSt.getSysConnId()));

		Utils.logi("------ Saved Photo: %s ----------\n%s\n%s", photo.recId, photo.pname, pth);

		// synchronize to cloud hub
		SyncWorker.blocksize = 32 * 3;
		DocTableMeta meta = new DocTableMeta("h_photos", "pid", conn);
		SyncWorker worker = new SyncWorker(0, conn, "kyiv.jnode", meta)
				.login("odys-z.github.io", "слава україні") // jserv node
				.push();
	
		DocsResp resp = worker.queryTasks(meta, usr, photo.device);

		worker.pullDocs(resp);
	}

	/**
	 * Simulates the processing of Albums.createFile(), creating a stub photo and querying it (have syncflag updated).
	 * 
	 * @param conn
	 * @param photo
	 * @param usr
	 * @param meta 
	 * @return
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException 
	 */
	String createPhoto(String conn, Photo photo, SyncRobot usr, PhotoMeta meta)
			throws TransException, SQLException, IOException {

		if (!DATranscxt.hasSemantics(conn, meta.tbl, smtype.extFile))
			throw new SemanticException("Semantics of ext-file for h_photos.uri dosen't been found");
		
		Update post = Docsyncer.onDocreate(photo, meta, usr);
		return DocUtils.createFile(conn, photo, usr, meta, defltSt, post);
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
