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
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.module.rs.AnResultset;
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

import static io.oz.jserv.sync.ZSUNodes.*;

class SyncWorkerTest {

	static String conn;
	// static String testDevice;

	static ErrorCtx errLog;
	static DATranscxt defltSt;
	static DocTableMeta meta;
	
	static {
		try {
			conn = "main-sqlite";
			// testDevice = "device-test";

			Path currentRelativePath = Paths.get("");
			String p = currentRelativePath.toAbsolutePath().toString();
			System.setProperty("VOLUME_HOME", p + "/src/test/res/volume");

			Configs.init(p + "/src/test/res/WEB-INF");
			Connects.init(p + "/src/test/res/WEB-INF");
			defltSt = new DATranscxt(Connects.defltConn());
			AnsonMsg.understandPorts(Port.docsync);
			AnSession.init(defltSt);

			Connects.init("src/test/res/WEB-INF");
			Clients.init(ZSUNodes.jservUrl, false);
			
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
	 * Test {@link SyncWorker}' pushing (synchronizing 'pub') with album-jserv as sync hub (8081).
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
	void testShareByPriv() throws
		AnsonException, SQLException, IOException, TransException,
		SsException, GeneralSecurityException, SAXException {
		
		// 1. create a public file at this private node
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
		photo.exif = new ArrayList<String>() {
			{add("location:вулиця Лаврська' 27' Київ");};
			{add("camera:Bayraktar TB2");}};
		photo.shareflag(DocTableMeta.Share.pub)
			.shareby("ody")
			.sharedate(new Date());

		// 2. synchronize to cloud hub
		SyncWorker.blocksize = 32 * 3;
		SyncWorker worker = new SyncWorker(SyncMode.priv, conn, "kyiv.jnode", meta)
				.login("odys-z.github.io", "слава україні");

		String pid = createPhoto(conn, photo, worker.client().robot, new PhotoMeta(defltSt.getSysConnId()));
		Utils.logi("------ Saved Photo: %s ----------\n%s\n%s", photo.fullpath(), photo.pname, pid);

		worker.push();
	
		// 3. query my tasks as another jnode
		worker = new SyncWorker(SyncMode.main, conn, "kharkiv.jnode", meta)
				.login("ody", "123456");
		DocsResp resp = worker.client().queryTasks(meta, worker.client().robot.orgId, worker.client().robot.deviceId);
		
		AnResultset tasks = resp.rs(0);
		if (tasks == null || tasks.total() == 0)
			fail("Shouldn't be here");

		tasks.beforeFirst();
		while (tasks.next()) {
			assertEquals(clientpath, tasks.getString(meta.fullpath));
		}

		// 4. synchronize downwardly 
		// worker.pullDocs(resp);
	}

	/**
	 * Simulates the processing of Albums.createFile(), creating a stub photo and querying it (having syncflag updated).
	 * 
	 * @see DocUtils#createFileB64(String, SyncDoc, IUser, DocTableMeta, DATranscxt, Update)
	 * @param conn
	 * @param photo
	 * @param usr
	 * @param meta table meta of h_photes
	 * @return doc id, e.g. pid
	 * @throws TransException
	 * @throws SQLException
	 * @throws IOException 
	 */
	String createPhoto(String conn, Photo photo, SyncRobot usr, PhotoMeta meta)
			throws TransException, SQLException, IOException {

		if (!DATranscxt.hasSemantics(conn, meta.tbl, smtype.extFilev2))
			throw new SemanticException("Semantics of ext-file for h_photos.uri can't been found");
		
		Update post = Docsyncer.onDocreate(photo, meta, usr);
		return DocUtils.createFileB64(conn, photo, usr, meta, defltSt, post);
	}

	/**
	 * <h5>A word about sharer and path at jserv:</h5>
	 * <p>
	 * - video is uploaded as owned by "odys-z.github.io"<br>
	 * - uri is handled by {@link io.odysz.semantic.DASemantics.ShExtFile ShExtFile} and using shareby as user-id/sub-folder name, like:<br>
	 * [volume]/[family]/[shareby]/[folder]/[pid] filename, e.g.<br>
	 * /.../jserv-album/volume/odys-z.github.io/synctest/0000009P ...<br>
	 * </p>
	 * @throws Exception
	 */
	@Test
	void testPrivPull() throws Exception {
		String owner = "odys-z.github.io";
		SyncRobot robot = new SyncRobot(owner, "f/zsu");
		Synclientier kharkiv = new Synclientier("sync.jser", conn, errLog).login("syrskyi", "123456");
		videoUpTest(meta);

		// downward synchronize the file
		SyncWorker.blocksize = 32 * 3;
		DocTableMeta meta = new DocTableMeta("h_photos", "pid", conn);
		SyncWorker worker = new SyncWorker(SyncMode.main, conn, "kyiv.jnode", meta);
		ArrayList<String> ids = worker
				.login(owner, "слава україні") // jserv node
				.pull();
		
		if (ids == null || ids.size() != 1)
			fail("No pull tasks are completed.");

		worker.verifyDocs(ids);
	}

	static String videoUpTest(DocTableMeta photoMeta)
			throws SemanticException, SsException, IOException, GeneralSecurityException, AnsonException, SQLException, SAXException {
		String localFolder = "src/test/res/anclient.java";
		int bsize = 72 * 1024;
		String filename = "Amelia Anisovych.mp4";

		Synclientier tier = new Synclientier("sync.jserv", conn, errLog)
				.login(Kharkiv.Anclient.userId, Kharkiv.Anclient.pswd)
				.blockSize(bsize);

		List<SyncDoc> videos = new ArrayList<SyncDoc>();
		String path = FilenameUtils.concat(localFolder, filename);
		videos.add((SyncDoc) new SyncDoc()
					.share(tier .robot.uid(), Share.pub, new Date())
					.folder(Kharkiv.folder)
					.fullpath(path));

		SessionInf photoUser = tier.client.ssInfo();
		photoUser.device = Kharkiv.Anclient.device;// "kharkiv.jnode";

		tier.pushBlocks( meta, videos, photoUser,
			(rows, rx, bx, blks, resp) -> {
				/* First time sql for data manipulation:
					INSERT INTO h_photos
					(pid, family, folder, pname, uri, pdate,
					device, shareby, sharedate, tags, geox, geoy, exif, oper, opertime, 
					clientpath, mime, filesize, css, shareflag, sync)
					VALUES
					('Test ЗСУ00001', 'omni', '2022_03', 'Amelia Anisovych.mp4', '$VOLUME_HOME/ody//2022_03/no such file.mp4', 1994,
					'jnode syrskyi', 'ody', 1997, NULL, 0.0, 0.0, 'exif', 'ody', 1997,
					'src/test/res/anclient.java/Amelia Anisovych.mp4', 'video/mp4', NULL, NULL, 'pub', NULL);
				 */
				fail(String.format(
					"Duplicate checking failed: %s\n%s",
					resp.msg(), resp.toString()));
			},
			new ErrorCtx() {
				@Override
				public void onError(MsgCode c, String msg) {
					if (!MsgCode.exGeneral.equals(c))
						fail("Not expected code");

					tier.del(meta, Kharkiv.Anclient.device, videos.get(0).fullpath());
					List<DocsResp> resps = tier.pushBlocks(meta, videos, photoUser, null);
					assertNotNull(resps);
					assertEquals(1, resps.size());

					for (DocsResp d : resps) {
						String docId = d.doc.recId();
						assertEquals(8, docId.length());

						DocsResp rp = tier.selectDoc(meta, docId);

						assertTrue(LangExt.isblank(rp.msg()));
						assertEquals(Kharkiv.Anclient.device, rp.doc.device());
						assertEquals(path, rp.doc.fullpath());
					}
				}
			});
		return path;
	}
}
