package io.oz.jserv.sync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import static io.oz.jserv.sync.ZSUNodes.*;

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
import io.odysz.semantic.jprotocol.AnsonResp;
import io.odysz.semantic.jprotocol.JProtocol.OnDocOk;
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
import io.oz.jserv.sync.SyncFlag.SyncEvent;
import io.oz.jserv.sync.SyncWorker.SyncMode;

class SyncWorkerTest {

	static String conn;

	static ErrorCtx errLog;
	static DATranscxt defltSt;
	static DocTableMeta meta;
	
	static {
		try {
			conn = "main-sqlite";

			Path currentRelativePath = Paths.get("");
			String p = currentRelativePath.toAbsolutePath().toString();
			System.setProperty("VOLUME_HOME", FilenameUtils.concat(p, volumeDir));

			String wwwinf = FilenameUtils.concat(p, webRoot);
			Configs.init(wwwinf);
			Connects.init(wwwinf);
			defltSt = new DATranscxt(Connects.defltConn());
			AnsonMsg.understandPorts(Port.docsync);
			AnSession.init(defltSt);

			// Connects.init(webRoot);
			Clients.init(jservUrl, false);
			
			meta = new PhotoMeta(conn);

			errLog = new ErrorCtx() {
				@Override
				public void onError(MsgCode code, String msg) {
					// Utils.warn(msg);
					fail(msg);
				}
			};
		} catch (SemanticException | SQLException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	@Test
	void testIds() {
		
	}

	/**
	 * <p>Kyiv -&gt; hub -&gt; Kharkiv</p>
	 * 
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
	void testShareByKyiv() throws 
		AnsonException, SQLException, IOException, TransException,
		SsException, GeneralSecurityException, SAXException {
		
		String clientpath = Kyiv.png;
		SyncWorker worker = new SyncWorker(Kyiv.JNode.mode, Kyiv.JNode.nodeId, conn, Kyiv.JNode.worker, meta)
				.login(Kyiv.JNode.passwd);

		// 0. clean failed tests
		cleanKyiv(worker, clientpath);
		worker.synctier.del(meta, worker.nodeId(), clientpath);

		// 1. create a public file at this private node
		Photo photo = new Photo();

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
			.shareby("ody@kyiv")
			.sharedate(new Date());

		// 2. synchronize to cloud hub ( hub <- kyiv )
		Docsyncer.init(Kyiv.JNode.nodeId);
		SyncWorker.blocksize = 32 * 3;

		String pid = createPhoto(conn, photo, worker.robot(), new PhotoMeta(defltSt.getSysConnId()));
		Utils.logi("------ Saved Photo: %s ----------\n%s\n%s", photo.fullpath(), photo.pname, pid);

		worker.push();
	
		// 3. query my tasks as another jnode (kharkiv)
		worker = new SyncWorker(Kharkiv.JNode.mode, Kharkiv.JNode.nodeId, conn, Kharkiv.JNode.worker, meta)
				.login(Kharkiv.JNode.passwd);
		DocsResp resp = worker.queryTasks();
		
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

	private void cleanKyiv(SyncWorker worker, String clientpath) throws TransException, SQLException {

		String device = worker.synctier.robot.deviceId;

		defltSt
			.delete(meta.tbl, worker.robot())
			.whereEq(meta.org, worker.org())
			.whereEq(meta.device, device)
			.whereEq(meta.fullpath, clientpath)
			.post(Docsyncer.onDel(clientpath, device))
			.d(defltSt.instancontxt(conn, worker.robot()));
	}


	/**
	 * Simulates the processing of Albums.createFile(), creating a stub photo and having syncflag updated.
	 * 
	 * @see DocUtils#createFileB64(String, SyncDoc, IUser, DocTableMeta, DATranscxt, Update)
	 * @see Docsyncer#onDocreate(SyncDoc, DocTableMeta, IUser)
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
	 * <p> device -&gt; hub -&gt; Kharkiv</p>
	 * 
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
	void testKharivPull() throws Exception {
		videoUpByApp(meta);

		// downward synchronize the file, hub -> Kyiv
		SyncWorker.blocksize = 32 * 3;
		DocTableMeta meta = new PhotoMeta(conn);
		SyncWorker worker = new SyncWorker(SyncMode.main, Kharkiv.JNode.nodeId, conn, Kyiv.JNode.worker, meta);
		ArrayList<String> ids = worker
				.login(Kyiv.JNode.passwd)
				.pull();
		
		if (ids == null || ids.size() != 1)
			fail("No pull tasks are completed.");

		worker.verifyDocs(ids);
	}

	/**
	 * Upload video from a device to a private node, Kharkiv.
	 * 
	 * @param photoMeta
	 * @return
	 * @throws SsException
	 * @throws IOException
	 * @throws GeneralSecurityException
	 * @throws AnsonException
	 * @throws SQLException
	 * @throws SAXException
	 * @throws TransException 
	 */
	static String videoUpByApp(DocTableMeta photoMeta)
			throws SsException, IOException, GeneralSecurityException, AnsonException, SQLException, SAXException, TransException {
		int bsize = 72 * 1024;

		Synclientier tier = new Synclientier(clientUri, conn, errLog)
				.login(AnDevice.userId, AnDevice.device, AnDevice.passwd)
				.blockSize(bsize);

		List<SyncDoc> videos = new ArrayList<SyncDoc>();
		videos.add((SyncDoc) new SyncDoc()
					.share(tier .robot.uid(), Share.pub, new Date())
					.folder(Kharkiv.folder)
					.fullpath(AnDevice.localFile));

		SessionInf ssInf = tier.client.ssInfo();
		// ssInf.device = AnDevice.device;  

		tier.pushBlocks( meta, videos,
			ssInf, // simulating pushing from app
			(rows, rx, bx, blks, resp) -> {
				/* First time sql for data manipulation:
					INSERT INTO h_photos
					(pid, family, folder, pname, uri, pdate,
					device, shareby, sharedate, tags, geox, geoy, exif, oper, opertime, 
					clientpath, mime, filesize, css, shareflag, sync)
					VALUES
					('Test ЗСУ00001', 'omni', '2022_03', 'Amelia Anisovych.mp4', '$VOLUME_HOME/ody/2022_03/no such file.mp4', 1994,
					'jnode syrskyi', 'ody', 1997, NULL, 0.0, 0.0, 'exif', 'ody', 1997,
					'src/test/res/anclient.java/Amelia Anisovych.mp4', 'video/mp4', NULL, NULL, 'pub', NULL);
				 */
				fail(String.format(
					"Duplicate checking failed: %s\n%s",
					resp.msg(), resp.toString()));
			}, null,
			new ErrorCtx() {
				@Override
				public void onError(MsgCode c, String msg) {
					if (MsgCode.exGeneral != c)
						fail("Not expected code");

					try { tier.del(meta, AnDevice.device, videos.get(0).fullpath()); }
					catch (Exception e) {
						e.printStackTrace();
						fail(e.getMessage());
					}
					
					List<DocsResp> resps = null;
					try {
						resps = tier.pushBlocks(meta, videos, ssInf, null, new OnDocOk() {
							@Override
							public void ok(SyncDoc doc, AnsonResp resp)
									throws IOException, AnsonException, TransException, SQLException {
								String f = SyncFlag.to(Share.pub, SyncEvent.pushEnd, doc.shareflag());
								Synclientier.setLocalSync(tier.localSt, tier.connPriv, meta, doc, f, tier.robot);
							}
						});
					} catch (TransException | IOException | SQLException e) {
						e.printStackTrace();
						fail(e.getMessage());
					}
					
					assertNotNull(resps);
					assertEquals(1, resps.size());

					for (DocsResp d : resps) {
						String docId = d.doc.recId();
						assertEquals(8, docId.length());

						DocsResp rp = tier.selectDoc(meta, docId);

						assertTrue(LangExt.isblank(rp.msg()));
						assertEquals(AnDevice.device, rp.doc.device());
						assertEquals(AnDevice.localFile, rp.doc.fullpath());
					}
				}
			});
		return AnDevice.localFile;
	}
}