package io.oz.jserv.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import io.odysz.common.LangExt;
import io.odysz.common.Utils;
import io.odysz.jclient.Clients;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.DASemantics.smtype;
import io.odysz.semantic.DATranscxt;
import io.odysz.semantic.DA.Connects;
import io.odysz.semantic.ext.DocTableMeta;
import io.odysz.semantic.ext.DocTableMeta.Share;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.x.SsException;
import io.odysz.semantic.jsession.AnSession;
import io.odysz.semantic.jsession.SessionInf;
import io.odysz.semantic.tier.docs.DocUtils;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantic.tier.docs.SyncDoc;
import io.odysz.semantics.IUser;
import io.odysz.semantics.x.SemanticException;
import io.odysz.transact.sql.Update;
import io.odysz.transact.x.TransException;
import io.oz.album.tier.Photo;
import io.oz.album.tier.PhotoMeta;
import io.oz.jserv.sync.SyncWorker.SyncMode;

class SyncWorkerTest {

	static String conn;
	static String testDevice;

	static ErrorCtx errLog;
	static DATranscxt defltSt;
	static DocTableMeta meta;
	
	static {
		try {
			conn = "main-sqlite";
			testDevice = "device-test";

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
			
			meta = new DocTableMeta("h_photos", "pid", conn);

			Docsyncer.init("Sync Test");

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
		photo.exif = new ArrayList<String>() {
			{add("location:вулиця Лаврська' 27' Київ");};
			{add("camera:Bayraktar TB2");}};
		photo.shareflag(DocTableMeta.Share.pub)
			.shareby("ody")
			.sharedate(new Date());

		SyncRobot rob = new SyncRobot("odys-z.github.io", "f/zsu");

		String pid = createPhoto(conn, photo, rob, new PhotoMeta(defltSt.getSysConnId()));

		Utils.logi("------ Saved Photo: %s ----------\n%s\n%s", photo.fullpath(), photo.pname, pid);

		// synchronize to cloud hub
		SyncWorker.blocksize = 32 * 3;
		SyncWorker worker = new SyncWorker(SyncMode.main, conn, "kyiv.jnode", meta)
				.login("odys-z.github.io", "слава україні") // jserv node
				.push();
	
		DocsResp resp = worker.queryTasks(meta, rob, photo.device);
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

	/**
	 * </h5>A word about sharer and path at jserv:</h5>
	 * <p>
	 * - video is uploaded as owning of "odys-z.github.io"<br>
	 * - uri is handled by {@link io.odysz.semantic.DASemantics.ShExtFile ShExtFile} and using shareby as user-id-sub-folder name, like:<br>
	 * [volume]/[family]/[shareby]/[folder]/[pid] filename, e.g.<br>
	 * /.../jserv-album/volume/odys-z.github.io/synctest/0000009P ...<br>
	 * </p>
	 * @throws Exception
	 */
	@Test
	void testPrivPull() throws Exception {
		String owner = "odys-z.github.io";
		SyncRobot robot = new SyncRobot(owner, "f/zsu");
		videoUp(meta, robot);

		// downward synchronize the file
		SyncWorker.blocksize = 32 * 3;
		DocTableMeta meta = new DocTableMeta("h_photos", "pid", conn);
		SyncWorker worker = new SyncWorker(SyncMode.main, conn, "kyiv.jnode", meta);
		ArrayList<String> ids = worker
				.login(owner, "слава україні") // jserv node
				.pull();
		
		worker.verifyDocs(ids);
	}

	static String videoUp(DocTableMeta photoMeta, IUser owner) throws SemanticException, SsException, IOException, GeneralSecurityException, AnsonException {
		String localFolder = "src/test/res/anclient.java";
		int bsize = 72 * 1024;
		String filename = "Amelia Anisovych.mp4";

		SessionClient ssclient = Clients.login("ody", "123456", testDevice);  // client device
		Synclientier tier = new Synclientier("test/album", ssclient, photoMeta, errLog)
								.blockSize(bsize);

		List<SyncDoc> videos = new ArrayList<SyncDoc>();
		String path = FilenameUtils.concat(localFolder, filename);
		videos.add((SyncDoc) new SyncDoc().loadFile(path, owner, Share.pub));

		SessionInf photoUser = ssclient.ssInfo();
		photoUser.device = testDevice;

		tier.syncVideos( videos, photoUser,
			(rows, rx, bx, blks, resp) -> {
				/* First time sql for data manipulation:
					INSERT INTO h_photos
					(pid, family, folder, pname, uri, pdate,
					device, shareby, sharedate, tags, geox, geoy, exif, oper, opertime, 
					clientpath, mime, filesize, css, shareflag, sync)
					VALUES
					('Test ЗСУ00001', 'omni', '2022_03', 'Amelia Anisovych.mp4', '$VOLUME_HOME/ody//2022_03/no such file.mp4', 1994,
					'device-test', 'ody', 1997, NULL, 0.0, 0.0, 'exif', 'ody', 1997,
					'src/test/res/anclient.java/Amelia Anisovych.mp4', 'video/mp4', NULL, NULL, 'pub', NULL);
				 */
				fail("Duplicate checking failed: " + resp.toString());
			},
			new ErrorCtx() {
				@Override
				public void onError(MsgCode c, String msg) {
					if (!MsgCode.exGeneral.equals(c))
						fail("Not expected code");

					tier.del(testDevice, videos.get(0).fullpath());
					List<DocsResp> resps = tier.syncVideos(videos, photoUser, null);
					assertNotNull(resps);
					assertEquals(1, resps.size());

					for (DocsResp d : resps) {
						String docId = d.recId();
						assertEquals(8, docId.length());

						DocsResp rp = tier.selectDoc(docId);

						assertTrue(LangExt.isblank(rp.msg()));
						assertNotNull(rp.clientname());
						assertEquals(rp.clientname(), filename);
					}
				}
			});
		return path;
	}
}
